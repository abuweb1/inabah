package app.inabah.android.feature.azkar

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.InMemoryContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.settings.AzkarResetSettings
import app.inabah.android.core.settings.DayTime
import app.inabah.android.core.settings.TestStorage
import app.inabah.android.core.settings.berlin
import app.inabah.android.core.settings.date
import app.inabah.android.core.settings.moscow
import app.inabah.android.core.settings.moscowDate
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

/** «Сохранение и обнуление прогресса азкаров». */
class AzkarProgressPersistenceTest {
    /** Текущее время и пояс устройства — меняются тестом. */
    private class Clock(var now: Instant, var zone: ZoneId = berlin)

    private val clock = Clock(date(2026, 10, 3, 8))
    private val morning = AzkarSection.Morning

    private val repository = InMemoryContentRepository(
        azkar = mapOf(morning to listOf(zikr(1, repetitions = 3), zikr(2, repetitions = 3))),
    )

    /** Сохранения, которые стор не смог прочитать. */
    private val unreadable = mutableListOf<AzkarSection>()

    /** Новый стор — как после запуска приложения; [load] — загрузить утренние. */
    private suspend fun makeStore(
        storage: TestStorage,
        settings: AzkarResetSettings = AzkarResetSettings(storage.storage),
        load: Boolean = true,
        now: () -> Instant = { clock.now },
    ) = AzkarStore(
        repository,
        storage.storage,
        settings,
        now = now,
        zone = { clock.zone },
        onUnreadableProgress = { section, _ -> unreadable += section },
    ).also { if (load) it.load(morning) }

    private fun AzkarStore.counts() = sessions(morning).map { it.count }

    private fun AzkarStore.completeAll() = sessions(morning).forEach { session -> repeat(3) { session.increment() } }

    @Test
    fun `Счёт сохраняется между запусками в пределах периода`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.sessions(morning)[0].increment()
        repeat(2) { store.sessions(morning)[1].increment() }

        clock.now = date(2026, 10, 3, 16, 59)
        storage.restart()
        val restored = makeStore(storage)

        assertEquals(listOf(1, 2), restored.counts())
    }

    @Test
    fun `После времени обнуления сохранённый счёт не восстанавливается`() = TestStorage.run { storage ->
        makeStore(storage).sessions(morning)[0].increment()

        clock.now = date(2026, 10, 3, 17, 1)
        storage.restart()

        assertEquals(listOf(0, 0), makeStore(storage).counts())
    }

    @Test
    fun `Наступил новый период, пока приложение открыто, — счёт и оверлей завершения сбрасываются`() =
        TestStorage.run { storage ->
            val store = makeStore(storage)
            store.completeAll()
            store.acknowledgeCompletion(morning)
            assertTrue(store.progress(morning).value.isFinished)

            clock.now = date(2026, 10, 3, 17, 0)
            store.refreshPeriods()

            assertEquals(0, store.progress(morning).value.completed)
            assertTrue(store.sessions(morning).all { it.count == 0 })
            // Пометка снята: новое прохождение снова поздравит — и в этом запуске, и после перезапуска.
            store.completeAll()
            assertTrue(store.shouldPresentCompletion(morning))
            storage.restart()
            val reopened = makeStore(storage)
            assertEquals(listOf(3, 3), reopened.counts())
            assertTrue(reopened.shouldPresentCompletion(morning))
        }

    @Test
    fun `Новый период при запуске снимает пометку оверлея`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.completeAll()
        store.acknowledgeCompletion(morning)

        clock.now = date(2026, 10, 3, 17, 1)
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
    fun `В том же периоде пересчёт ничего не сбрасывает`() = TestStorage.run { storage ->
        val store = makeStore(storage)
        store.sessions(morning)[0].increment()

        clock.now = date(2026, 10, 3, 12)
        store.refreshPeriods()

        assertEquals(1, store.sessions(morning)[0].count)
    }

    @Test
    fun `Смена времени на уже прошедшее — прогресс остаётся до прежнего времени обнуления`() {
        val cases = listOf(
            // В 08:00 — на 07:00.
            date(2026, 10, 3, 8) to DayTime.of(7, 0),
            // В 16:45 — на 16:30 (раньше обнуление пропускалось до завтрашних 16:30).
            date(2026, 10, 3, 16, 45) to DayTime.of(16, 30),
        )
        for ((changedAt, newTime) in cases) {
            clock.now = date(2026, 10, 3, 8)
            TestStorage.run { storage ->
                val settings = AzkarResetSettings(storage.storage)
                val store = makeStore(storage, settings)
                store.sessions(morning)[0].increment()

                clock.now = changedAt
                settings.setResetTime(newTime, morning)
                store.refreshPeriods()
                assertEquals(1, store.sessions(morning)[0].count, "смена в $changedAt на $newTime")

                clock.now = date(2026, 10, 3, 17)
                store.refreshPeriods()
                assertEquals(0, store.sessions(morning)[0].count, "смена в $changedAt на $newTime")
            }
        }
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
    fun `Перелёт Москва → Берлин между запусками — прогресс до 17·00 по Берлину`() = TestStorage.run { storage ->
        clock.now = moscowDate(2026, 10, 3, 8)
        clock.zone = moscow
        makeStore(storage).sessions(morning)[0].increment()

        // 16:30 по Берлину = 17:30 МСК: московское обнуление прошло, берлинское — ещё нет.
        clock.now = date(2026, 10, 3, 16, 30)
        clock.zone = berlin
        storage.restart()
        val restored = makeStore(storage)
        assertEquals(1, restored.sessions(morning)[0].count)

        clock.now = date(2026, 10, 3, 17)
        restored.refreshPeriods()
        assertEquals(0, restored.sessions(morning)[0].count)
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
    fun `Повреждённое сохранение — новый период с пустым счётом`() {
        val corrupted = listOf(
            "{",
            """{"counts":{"1":2}}""",
            """{"period":{"scheduledReset":1,"validUntil":1,"timeZone":"Mars/Base"},"counts":{"1":2}}""",
            """{"period":{"scheduledReset":1,"validUntil":1,"timeZone":"Europe/Berlin"},"counts":{"один":2}}""",
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

    @Test
    fun `Смена времени на ещё не наступившее — одно обнуление в новое время, сохраняется`() =
        TestStorage.run { storage ->
            val settings = AzkarResetSettings(storage.storage)
            makeStore(storage, settings).sessions(morning)[0].increment()

            clock.now = date(2026, 10, 3, 16, 45)
            settings.setResetTime(DayTime.of(18, 0), morning)

            // Перезапуск после прежних 17:00: перенесённая граница сохранена — прогресс на месте.
            clock.now = date(2026, 10, 3, 17, 30)
            storage.restart()
            val restored = makeStore(storage)
            assertEquals(1, restored.sessions(morning)[0].count)

            clock.now = date(2026, 10, 3, 18, 0)
            restored.refreshPeriods()
            assertEquals(0, restored.sessions(morning)[0].count)
        }

    /** Часы для таймера: виртуальное время теста + сдвиг «системных часов» устройства. */
    private class VirtualClock(private val scope: TestScope, private val start: Instant) {
        var jump: java.time.Duration = java.time.Duration.ZERO
        fun now(): Instant = start.plusMillis(scope.currentTime).plus(jump)
    }

    @Test
    fun `Таймер обнуляет раздел в момент окончания периода`() = TestStorage.run { storage ->
        val virtual = VirtualClock(this, date(2026, 10, 3, 8))
        val store = makeStore(storage, now = virtual::now)
        backgroundScope.launch { store.runResetTimer() }
        store.sessions(morning)[0].increment()

        advanceTimeBy(9.hours - 1.minutes)
        runCurrent()
        assertEquals(1, store.sessions(morning)[0].count, "до 17:00 счёт на месте")

        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(0, store.sessions(morning)[0].count, "в 17:00 — новый период")

        // Перепланирован на следующие сутки.
        store.sessions(morning)[0].increment()
        advanceTimeBy(24.hours)
        runCurrent()
        assertEquals(0, store.sessions(morning)[0].count, "через сутки — снова новый период")
    }

    @Test
    fun `Таймер перепланируется при смене времени обнуления`() = TestStorage.run { storage ->
        val virtual = VirtualClock(this, date(2026, 10, 3, 16, 45))
        val settings = AzkarResetSettings(storage.storage)
        val store = makeStore(storage, settings, now = virtual::now)
        backgroundScope.launch { store.runResetTimer() }
        store.sessions(morning)[0].increment()

        settings.setResetTime(DayTime.of(16, 50), morning)
        advanceTimeBy(6.minutes)
        runCurrent()

        assertEquals(0, store.sessions(morning)[0].count, "обнуление в новые 16:50")
    }

    @Test
    fun `Часы устройства ушли вперёд, но до обнуления — таймер пересчитывает ожидание`() =
        TestStorage.run { storage ->
            val virtual = VirtualClock(this, date(2026, 10, 3, 16, 0))
            val store = makeStore(storage, now = virtual::now)
            backgroundScope.launch { store.runResetTimer() }
            store.sessions(morning)[0].increment()
            runCurrent()

            // Синхронизация времени: 16:00 → 16:55 (ACTION_TIME_CHANGED → refreshPeriods).
            virtual.jump = java.time.Duration.ofMinutes(55)
            store.refreshPeriods()
            advanceTimeBy(6.minutes)
            runCurrent()

            assertEquals(0, store.sessions(morning)[0].count, "обнуление в 17:00 по часам устройства")
        }

    @Test
    fun `Смена часового пояса при открытом приложении — обнуление по новому поясу`() =
        TestStorage.run { storage ->
            clock.zone = moscow
            val virtual = VirtualClock(this, moscowDate(2026, 10, 3, 8))
            val store = makeStore(storage, now = virtual::now)
            backgroundScope.launch { store.runResetTimer() }
            store.sessions(morning)[0].increment()

            // Прилетели в Берлин: 17:00 по Берлину = 18:00 МСК, через 10 часов.
            clock.zone = berlin
            store.refreshPeriods()
            advanceTimeBy(9.hours + 30.minutes)
            runCurrent()
            assertEquals(1, store.sessions(morning)[0].count, "17:00 МСК прошло, берлинское — ещё нет")

            advanceTimeBy(31.minutes)
            runCurrent()
            assertEquals(0, store.sessions(morning)[0].count, "17:00 по Берлину")
        }
}
