package app.inabah.android.feature.azkar

import app.inabah.android.core.content.InMemoryContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.settings.AzkarComposition
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.StoredComposition
import app.inabah.android.core.settings.TestStorage
import app.inabah.android.core.settings.berlin
import app.inabah.android.core.settings.date
import app.inabah.android.core.settings.defaultComposition
import app.inabah.android.core.settings.resolveComposition
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

/** «Состав азкаров»: набор по умолчанию, сохранённый выбор, будущие зикры, применение к счёту. */
class AzkarCompositionTest {
    private val morning = AzkarSection.Morning

    // 1-3 - основной набор, 4-5 - добавлены позже с optional.
    private val all = listOf(
        zikr(1, repetitions = 1),
        zikr(2, repetitions = 3),
        zikr(3, repetitions = 1),
        zikr(4, repetitions = 1).copy(optional = true),
        zikr(5, repetitions = 1).copy(optional = true),
    )

    @Test
    fun `Набор по умолчанию - зикры без optional, в порядке данных`() {
        assertEquals(listOf(1, 2, 3), defaultComposition(all))
        assertEquals(listOf(1, 2, 3), resolveComposition(all, stored = null))
    }

    @Test
    fun `Сохранённый порядок - неизвестные и повторы отброшены`() {
        assertEquals(listOf(4, 2, 1), resolveComposition(all, StoredComposition(listOf(4, 99, 2, 4, 1), removed = setOf(3, 5))))
    }

    @Test
    fun `Новый зикр без optional встаёт в конец, убранный пользователем - нет`() {
        val withNew = all + zikr(6, repetitions = 1)
        val stored = StoredComposition(order = listOf(2, 1), removed = setOf(3, 4, 5))
        assertEquals(listOf(2, 1, 6), resolveComposition(withNew, stored))
        assertEquals(listOf(2, 1), resolveComposition(withNew, stored.copy(removed = setOf(3, 4, 5, 6))))
        assertEquals(listOf(2, 1), resolveComposition(withNew + zikr(7, repetitions = 1).copy(optional = true), stored.copy(removed = setOf(3, 4, 5, 6))))
    }

    @Test
    fun `Пустой набор - набор по умолчанию`() {
        assertEquals(listOf(1, 2, 3), resolveComposition(all, StoredComposition(listOf(42), removed = emptySet())))
    }

    @Test
    fun `Набор по умолчанию не сохраняется, выбор переживает перезапуск, возврат забывает выбор`() = TestStorage.run { storage ->
        val composition = AzkarComposition(storage.storage)
        composition.set(morning, listOf(1, 2, 3), all)
        assertNull(composition.stored(morning).value, "совпал с набором по умолчанию - ничего не храним")

        composition.set(morning, listOf(3, 1, 5), all)
        val restarted = AzkarComposition(storage.restart())
        assertEquals(listOf(3, 1, 5), restarted.numbers(morning, all))
        assertEquals(setOf(2, 4), restarted.stored(morning).value?.removed)
        assertEquals(listOf(1, 2, 3), restarted.numbers(AzkarSection.Evening, all), "вечерние не тронуты")

        restarted.set(morning, emptyList(), all)
        assertEquals(listOf(3, 1, 5), restarted.numbers(morning, all), "пустой набор не сохраняется")

        restarted.restoreDefault(morning)
        assertEquals(listOf(1, 2, 3), AzkarComposition(storage.restart()).numbers(morning, all))
    }

    @Test
    fun `Хранилище строит раздел по составу, применение сохраняет счёт оставшихся и пишет день с новым числом`() = TestStorage.run { storage ->
        val composition = AzkarComposition(storage.storage)
        val history = AzkarHistory(storage.storage) { throw AssertionError(it) }
        val store = AzkarStore(
            repository = InMemoryContentRepository(azkar = mapOf(morning to all, AzkarSection.Evening to emptyList())),
            storage = storage.storage,
            windowSettings = AzkarWindowSettings(storage.storage),
            history = history,
            now = { date(2026, 10, 3, 8) },
            zone = { berlin },
            onUnreadableProgress = { section, error -> throw AssertionError("Прогресс $section не читается", error) },
            composition = composition,
        )
        store.load(morning)
        assertEquals(listOf(1, 2, 3), store.sessions(morning).map { it.id.number })
        assertEquals(all, store.catalog(morning))

        val kept = store.sessions(morning)[1]
        kept.increment()
        store.sessions(morning)[0].increment()
        store.sessions(morning)[2].increment()
        assertEquals(2, store.progress(morning).value.completed)

        // Убрали 1 (прочитанный), добавили 5, переставили: 2 остаётся тем же объектом со счётом 1, 3 - прочитан.
        composition.set(morning, listOf(2, 3, 5), all)
        store.applyComposition(morning)
        assertEquals(listOf(2, 3, 5), store.sessions(morning).map { it.id.number })
        assertSame(kept, store.sessions(morning)[0])
        assertEquals(1, kept.count)
        assertEquals(SectionProgress(completed = 1, total = 3, isStarted = true), store.progress(morning).value)
        assertEquals(3, history.record(morning, LocalDate.of(2026, 10, 3))?.total)

        // Холодный старт - тот же состав и счёт; у убранного счёта нет.
        val restarted = AzkarStore(
            repository = InMemoryContentRepository(azkar = mapOf(morning to all, AzkarSection.Evening to emptyList())),
            storage = storage.restart(),
            windowSettings = AzkarWindowSettings(storage.storage),
            history = AzkarHistory(storage.storage) { throw AssertionError(it) },
            now = { date(2026, 10, 3, 8) },
            zone = { berlin },
            onUnreadableProgress = { section, error -> throw AssertionError("Прогресс $section не читается", error) },
            composition = AzkarComposition(storage.storage),
        )
        restarted.load(morning)
        assertEquals(listOf(2, 3, 5), restarted.sessions(morning).map { it.id.number })
        assertEquals(listOf(1, 1, 0), restarted.sessions(morning).map { it.count })

        // Вернули зикр 1 - он с нуля (его счёт ушёл при убирании).
        val restartedComposition = AzkarComposition(storage.storage)
        restartedComposition.set(morning, listOf(1, 2, 3), all)
        val again = AzkarStore(
            repository = InMemoryContentRepository(azkar = mapOf(morning to all, AzkarSection.Evening to emptyList())),
            storage = storage.storage,
            windowSettings = AzkarWindowSettings(storage.storage),
            history = AzkarHistory(storage.storage) { throw AssertionError(it) },
            now = { date(2026, 10, 3, 8) },
            zone = { berlin },
            onUnreadableProgress = { section, error -> throw AssertionError("Прогресс $section не читается", error) },
            composition = restartedComposition,
        )
        again.load(morning)
        assertEquals(listOf(0, 1, 1), again.sessions(morning).map { it.count })
    }

    @Test
    fun `Не загруженный раздел - применение ничего не делает`() = TestStorage.run { storage ->
        val store = AzkarStore(
            repository = InMemoryContentRepository(azkar = mapOf(morning to all, AzkarSection.Evening to emptyList())),
            storage = storage.storage,
            windowSettings = AzkarWindowSettings(storage.storage),
            history = AzkarHistory(storage.storage) { throw AssertionError(it) },
            now = { date(2026, 10, 3, 8) },
            zone = { berlin },
            onUnreadableProgress = { section, error -> throw AssertionError("Прогресс $section не читается", error) },
        )
        store.applyComposition(morning)
        assertEquals(emptyList(), store.sessions(morning))
    }
}
