package app.inabah.android.feature.azkar

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.InMemoryContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.settings.AzkarDayRecord
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.DayTime
import app.inabah.android.core.settings.TestStorage
import app.inabah.android.core.settings.berlin
import app.inabah.android.core.settings.date
import app.inabah.android.core.settings.moscow
import app.inabah.android.core.settings.moscowDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

/** «Прогресс азкаров во времени азкаров» (iOS `AzkarWindowTests`) и таймер границ. */
class AzkarProgressPersistenceTest {
    /** Текущее время и пояс устройства — меняются тестом. */
    private class Clock(var now: Instant, var zone: ZoneId = berlin)

    // 08:00 — утреннее окно 5:00–12:00.
    private val clock = Clock(date(2026, 10, 3, 8))
    private val morning = AzkarSection.Morning
    private val evening = AzkarSection.Evening
    private val october3: LocalDate = LocalDate.of(2026, 10, 3)

    private val repository = InMemoryContentRepository(
        azkar = mapOf(
            morning to listOf(zikr(1, repetitions = 3), zikr(2, repetitions = 3)),
            evening to listOf(zikr(1, repetitions = 1, section = evening), zikr(2, repetitions = 1, section = evening)),
        ),
    )

    /** Сохранения, которые стор не смог прочитать. */
    private val unreadable = mutableListOf<AzkarSection>()

    /** История последнего созданного стора. */
    private lateinit var history: AzkarHistory

    /** Новый стор — как после запуска приложения; [load] — загрузить [section]. */
    private suspend fun makeStore(
        storage: TestStorage,
        settings: AzkarWindowSettings = AzkarWindowSettings(storage.storage),
        load: Boolean = true,
        section: AzkarSection = morning,
        now: () -> Instant = { clock.now },
    ): AzkarStore {
        history = AzkarHistory(storage.storage) { throw AssertionError(it) }
        return AzkarStore(
            repository,
            storage.storage,
            settings,
            history,
            now = now,
            zone = { clock.zone },
            onUnreadableProgress = { section, _ -> unreadable += section },
        ).also { if (load) it.load(section) }
    }

    private fun AzkarStore.counts() = sessions(morning).map { it.count }

    private fun AzkarStore.completeAll() = sessions(morning).forEach { session -> repeat(3) { session.increment() } }

    @Test
    fun `Счёт сохраняется между запусками в пределах окна`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.sessions(morning)[0].increment()
        repeat(2) { store.sessions(morning)[1].increment() }

        clock.now = date(2026, 10, 3, 11, 59)
        storage.restart()
        val restored = makeStore(storage)

        assertEquals(listOf(1, 2), restored.counts())
        assertTrue(restored.isInWindow(morning).value)
    }

    @Test
    fun `Конец окна — строгий сброс, пока приложение открыто, счёт и оверлей`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.completeAll()
        store.acknowledgeCompletion(morning)

        clock.now = date(2026, 10, 3, 12, 0)
        store.reconcile()

        assertEquals(listOf(0, 0), store.counts())
        assertFalse(store.isInWindow(morning).value)
        // Пометка снята, но вне окна оверлея нет; к началу окна — новое прохождение поздравит.
        store.completeAll()
        assertFalse(store.shouldPresentCompletion(morning))
        clock.now = date(2026, 10, 4, 5, 0)
        store.reconcile()
        store.completeAll()
        assertTrue(store.shouldPresentCompletion(morning))
    }

    @Test
    fun `После конца окна сохранённый счёт не восстанавливается`() = TestStorage.run { storage ->
        makeStore(storage).sessions(morning)[0].increment()

        clock.now = date(2026, 10, 3, 12, 1)
        storage.restart()

        assertEquals(listOf(0, 0), makeStore(storage).counts())
    }

    @Test
    fun `Счёт вне окна работает, сохраняется и обнуляется к началу окна`() = TestStorage.run { storage ->
        clock.now = date(2026, 10, 3, 13)
        val store = makeStore(storage)
        assertFalse(store.isInWindow(morning).value)
        store.sessions(morning)[0].increment()
        assertEquals(1, store.sessions(morning)[0].count)

        clock.now = date(2026, 10, 3, 20)
        storage.restart()
        val restored = makeStore(storage)
        assertEquals(listOf(1, 0), restored.counts())

        clock.now = date(2026, 10, 4, 5, 0)
        restored.reconcile()
        assertEquals(listOf(0, 0), restored.counts())
        assertTrue(restored.isInWindow(morning).value)
    }

    @Test
    fun `Оверлей завершения — только во время азкаров`() = TestStorage.run { storage ->
        clock.now = date(2026, 10, 3, 13)
        val store = makeStore(storage)
        store.completeAll()

        assertTrue(store.progress(morning).value.isFinished)
        assertFalse(store.shouldPresentCompletion(morning))
    }

    @Test
    fun `Перелёт Москва → Берлин между запусками — окно до 12·00 по Берлину`() = TestStorage.run { storage ->
        clock.now = moscowDate(2026, 10, 3, 8)
        clock.zone = moscow
        makeStore(storage).sessions(morning)[0].increment()

        // 11:30 по Берлину = 12:30 МСК: московское окно закончилось, берлинское — ещё нет.
        clock.now = date(2026, 10, 3, 11, 30)
        clock.zone = berlin
        storage.restart()
        val restored = makeStore(storage)
        assertEquals(1, restored.sessions(morning)[0].count)

        clock.now = date(2026, 10, 3, 12)
        restored.reconcile()
        assertEquals(0, restored.sessions(morning)[0].count)
    }

    @Test
    fun `Смена времени — сверка видит только сохранённое окно`() =
        TestStorage.run { storage ->
            val settings = AzkarWindowSettings(storage.storage)
            val store = makeStore(storage, settings)
            store.sessions(morning)[0].increment()

            // Колесо прошло через 07:00 (окно уже закончилось бы) в черновике экрана; сохранено итоговое 11:00.
            settings.set(settings.window(morning).copy(end = DayTime.of(11, 0)), morning)
            store.reconcile()
            assertEquals(1, store.sessions(morning)[0].count)

            clock.now = date(2026, 10, 3, 11)
            store.reconcile()
            assertEquals(0, store.sessions(morning)[0].count, "окно закончилось в новые 11:00")
        }

    @Test
    fun `Конец окна перенесли на уже прошедшее время — окно закончилось`() = TestStorage.run { storage ->
        val settings = AzkarWindowSettings(storage.storage)
        val store = makeStore(storage, settings)
        store.sessions(morning)[0].increment()

        settings.set(settings.window(morning).copy(end = DayTime.of(7, 30)), morning)
        store.reconcile()

        assertEquals(0, store.sessions(morning)[0].count)
        assertFalse(store.isInWindow(morning).value)
    }

    @Test
    fun `Сохранение версии 1·0·0 не восстанавливается — молча, без ошибки`() = TestStorage.run { storage ->
        val legacy = """{"period":{"scheduledReset":1759503600000,"validUntil":1759503600000,""" +
            """"timeZone":"Europe/Berlin"},"counts":{"1":2},"completionShown":false}"""
        storage.seed { it[stringPreferencesKey("azkar.progress.morning")] = legacy }

        assertEquals(listOf(0, 0), makeStore(storage).counts())
        assertTrue(unreadable.isEmpty(), "старый формат — не повреждение")
    }

    @Test
    fun `Ручной сброс из настроек обнуляет раздел и сохраняется`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.completeAll()
        store.acknowledgeCompletion(morning)

        store.resetProgress(morning)

        assertTrue(store.sessions(morning).all { it.count == 0 })
        assertFalse(store.shouldPresentCompletion(morning))
        store.completeAll()
        assertTrue(store.shouldPresentCompletion(morning), "пометка оверлея снята сбросом")
        store.resetProgress(morning)
        storage.restart()
        assertEquals(listOf(0, 0), makeStore(storage).counts())
    }

    @Test
    fun `Ручной сброс не загруженного раздела забывает сохранённый прогресс`() = TestStorage.run { storage ->
        makeStore(storage).sessions(morning)[0].increment()
        storage.restart()

        makeStore(storage, load = false).resetProgress(morning)
        storage.restart()

        assertEquals(listOf(0, 0), makeStore(storage).counts())
    }

    @Test
    fun `Оверлей завершения после перезапуска не показывается снова`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.completeAll()
        assertTrue(store.shouldPresentCompletion(morning))
        store.acknowledgeCompletion(morning)

        storage.restart()
        val reopened = makeStore(storage)

        assertTrue(reopened.progress(morning).value.isFinished)
        assertFalse(reopened.shouldPresentCompletion(morning))
    }

    @Test
    fun `Есть ли что сбрасывать — и у не загруженного раздела`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        assertFalse(store.hasProgress(morning))
        store.sessions(morning)[0].increment()
        assertTrue(store.hasProgress(morning))

        storage.restart()
        val fresh = makeStore(storage, load = false)
        assertTrue(fresh.hasProgress(morning))
        fresh.resetProgress(morning)
        assertFalse(fresh.hasProgress(morning))
    }

    @Test
    fun `Сброс одной карточки сохраняется`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.sessions(morning)[0].increment()
        store.sessions(morning)[0].reset()

        storage.restart()

        assertEquals(listOf(0, 0), makeStore(storage).counts())
    }

    @Test
    fun `История — выполнено X из N по дате окна, сохраняется после конца окна`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        repeat(3) { store.sessions(morning)[0].increment() }
        assertEquals(AzkarDayRecord(1, 2), history.record(morning, october3))

        clock.now = date(2026, 10, 3, 12)
        store.reconcile()
        storage.restart()

        assertEquals(AzkarDayRecord(1, 2), AzkarHistory(storage.storage) { throw AssertionError(it) }.record(morning, october3))
    }

    @Test
    fun `История — вечерние после полуночи, запись за дату начала окна`() = TestStorage.run { storage ->
        clock.now = date(2026, 10, 4, 0, 30)
        val store = makeStore(storage, section = evening)
        store.sessions(evening)[0].increment()

        assertEquals(AzkarDayRecord(1, 2), history.record(evening, october3))
        assertNull(history.record(evening, october3.plusDays(1)))
    }

    @Test
    fun `История — счёт вне окна не записывается`() = TestStorage.run { storage ->
        clock.now = date(2026, 10, 3, 13)
        makeStore(storage).completeAll()

        assertNull(history.record(morning, october3))
        assertNull(history.record(morning, october3.plusDays(1)))
    }

    @Test
    fun `История — ручной сброс в окне убирает запись дня`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.completeAll()
        assertEquals(AzkarDayRecord(2, 2), history.record(morning, october3))

        store.resetProgress(morning)

        assertNull(history.record(morning, october3))
    }

    // Хвост этапа 3: «Сбросить» в настройках не гасла после обнуления по времени при частичном счёте —
    // поток прогресса не менялся (ни один зикр не был выполнен).
    @Test
    fun `Конец окна при частичном счёте — прогресс раздела сообщает, что сбрасывать нечего`() =
        TestStorage.run { storage ->
            val store = makeStore(storage)
            store.sessions(morning)[0].increment()
            assertTrue(store.progress(morning).value.isStarted)

            clock.now = date(2026, 10, 3, 12)
            store.reconcile()

            assertFalse(store.progress(morning).value.isStarted)
            assertFalse(store.hasProgress(morning))
        }

    @Test
    fun `Новое окно при запуске снимает пометку оверлея`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.completeAll()
        store.acknowledgeCompletion(morning)

        clock.now = date(2026, 10, 4, 8)
        storage.restart()
        val reopened = makeStore(storage)
        reopened.completeAll()

        assertTrue(reopened.shouldPresentCompletion(morning))
    }

    @Test
    fun `Раздел снова не выполнен — следующее завершение покажет оверлей снова`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.completeAll()
        store.acknowledgeCompletion(morning)
        assertFalse(store.shouldPresentCompletion(morning))

        store.sessions(morning)[0].reset()
        store.resetCompletionAcknowledgement(morning)
        storage.restart()
        val reopened = makeStore(storage)
        repeat(3) { reopened.sessions(morning)[0].increment() }

        assertTrue(reopened.shouldPresentCompletion(morning))
    }

    @Test
    fun `В том же окне сверка ничего не сбрасывает`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.sessions(morning)[0].increment()

        clock.now = date(2026, 10, 3, 11, 59)
        store.reconcile()

        assertEquals(1, store.sessions(morning)[0].count)
    }

    @Test
    fun `Повреждённое сохранение — новый отрезок с пустым счётом и сообщение в лог`() {
        val corrupted = listOf(
            "{",
            """{"counts":{"1":2}}""",
            """{"period":{"kind":"later","day":"2026-10-03","validUntil":1},"counts":{"1":2}}""",
            """{"period":{"kind":"window","day":"3 октября","validUntil":1},"counts":{"1":2}}""",
            """{"period":{"kind":"window","day":"2026-10-03","validUntil":1},"counts":{"один":2}}""",
        )
        for (value in corrupted) {
            unreadable.clear()
            TestStorage.run { storage ->
                val restarted = storage.seed { it[stringPreferencesKey("azkar.progress.morning")] = value }
                assertFalse(makeStore(storage, load = false).hasProgress(morning), "сохранение $value")

                val store = makeStore(storage)

                assertEquals(listOf(0, 0), store.counts(), "сохранение $value")
                assertTrue(morning in unreadable, "ошибка не сообщена для $value")
                assertNotEquals(value, restarted.snapshot[stringPreferencesKey("azkar.progress.morning")])
            }
        }
    }

    /** Часы для таймера: виртуальное время теста + сдвиг «системных часов» устройства. */
    private class VirtualClock(private val scope: TestScope, private val start: Instant) {
        var jump: java.time.Duration = java.time.Duration.ZERO
        fun now(): Instant = start.plusMillis(scope.currentTime).plus(jump)
    }

    @Test
    fun `Таймер обнуляет раздел на каждой границе`() = TestStorage.run { storage ->
        val virtual = VirtualClock(this, date(2026, 10, 3, 8))
        val store = makeStore(storage, now = virtual::now)
        backgroundScope.launch { store.runBoundaryTimer() }
        store.sessions(morning)[0].increment()

        advanceTimeBy(4.hours - 1.minutes)
        runCurrent()
        assertEquals(1, store.sessions(morning)[0].count, "до 12:00 счёт на месте")

        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(0, store.sessions(morning)[0].count, "в 12:00 — конец окна")
        assertFalse(store.isInWindow(morning).value)

        // Перепланирован на начало следующего окна: счёт вне окна обнулится в 05:00.
        store.sessions(morning)[0].increment()
        advanceTimeBy(17.hours)
        runCurrent()
        assertEquals(0, store.sessions(morning)[0].count, "в 05:00 — новое окно")
        assertTrue(store.isInWindow(morning).value)
    }

    @Test
    fun `Часы устройства ушли вперёд, но до границы — таймер пересчитывает ожидание`() =
        TestStorage.run { storage ->
            val virtual = VirtualClock(this, date(2026, 10, 3, 11, 0))
            val store = makeStore(storage, now = virtual::now)
            backgroundScope.launch { store.runBoundaryTimer() }
            store.sessions(morning)[0].increment()
            runCurrent()

            // Синхронизация времени: 11:00 → 11:55 (ACTION_TIME_CHANGED → reconcile).
            virtual.jump = java.time.Duration.ofMinutes(55)
            store.reconcile()
            advanceTimeBy(6.minutes)
            runCurrent()

            assertEquals(0, store.sessions(morning)[0].count, "конец окна в 12:00 по часам устройства")
        }

    @Test
    fun `Смена часового пояса при открытом приложении — граница по новому поясу`() =
        TestStorage.run { storage ->
            clock.zone = moscow
            val virtual = VirtualClock(this, moscowDate(2026, 10, 3, 8))
            val store = makeStore(storage, now = virtual::now)
            backgroundScope.launch { store.runBoundaryTimer() }
            store.sessions(morning)[0].increment()

            // Прилетели в Берлин (07:00 по Берлину): то же окно, 12:00 по Берлину = 13:00 МСК, через 5 часов.
            clock.zone = berlin
            store.reconcile()
            advanceTimeBy(4.hours + 30.minutes)
            runCurrent()
            assertEquals(1, store.sessions(morning)[0].count, "12:00 МСК прошло, берлинское — ещё нет")

            advanceTimeBy(31.minutes)
            runCurrent()
            assertEquals(0, store.sessions(morning)[0].count, "12:00 по Берлину")
        }

    // Аудит 2026-10-06: таймер проверялся только с одним разделом.
    @Test
    fun `Таймер с двумя разделами — каждый обнуляется на своей границе, другой не трогается`() =
        TestStorage.run { storage ->
            val virtual = VirtualClock(this, date(2026, 10, 3, 8))
            val store = makeStore(storage, now = virtual::now)
            store.load(evening)
            backgroundScope.launch { store.runBoundaryTimer() }
            store.sessions(morning)[0].increment()
            store.sessions(evening)[0].increment()

            advanceTimeBy(4.hours + 1.minutes)
            runCurrent()
            assertEquals(0, store.sessions(morning)[0].count, "12:00 — конец утреннего окна")
            assertEquals(1, store.sessions(evening)[0].count, "у вечерних граница ещё не наступила")

            advanceTimeBy(5.hours)
            runCurrent()
            assertEquals(0, store.sessions(evening)[0].count, "17:00 — начало вечернего окна")
            assertTrue(store.isInWindow(evening).value)
        }

    @Test
    fun `История — сброс единственной выполненной карточки в окне убирает запись дня`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        repeat(3) { store.sessions(morning)[0].increment() }
        assertEquals(AzkarDayRecord(1, 2), history.record(morning, october3))

        store.sessions(morning)[0].reset()

        assertNull(history.record(morning, october3))
    }
}
