package app.inabah.android.feature.azkar

import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.FlakyRepository
import app.inabah.android.core.content.InMemoryContentRepository
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.settings.AzkarResetSettings
import app.inabah.android.core.settings.TestStorage
import app.inabah.android.core.settings.berlin
import app.inabah.android.core.settings.date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

fun zikr(number: Int, repetitions: Int, section: AzkarSection = AzkarSection.Morning) = Zikr(
    id = ZikrId(section, number),
    arabic = "سُبْحَانَ اللَّهِ",
    repetitions = repetitions,
    audioFileName = "${section.key}_$number.mp3",
    translation = null,
)

/** «Счёт азкаров». */
class AzkarCountingTest {
    private val repository = InMemoryContentRepository(
        azkar = mapOf(
            AzkarSection.Morning to listOf(zikr(1, repetitions = 1), zikr(2, repetitions = 3)),
            AzkarSection.Evening to listOf(zikr(1, repetitions = 1, section = AzkarSection.Evening)),
        ),
    )

    private suspend fun loadedStore(storage: TestStorage, repository: ContentRepository = this.repository) =
        AzkarStore(
            repository = repository,
            storage = storage.storage,
            resetSettings = AzkarResetSettings(storage.storage),
            now = { date(2026, 10, 3, 8) },
            zone = { berlin },
            onUnreadableProgress = { section, error -> throw AssertionError("Прогресс $section не читается", error) },
        ).also { it.loadAll() }

    @Test
    fun `Счёт растёт до max и дальше не идёт`() {
        val session = ZikrSession(zikr(1, repetitions = 3))

        assertEquals(IncrementResult.Counted, session.increment())
        assertEquals(IncrementResult.Counted, session.increment())
        assertEquals(IncrementResult.Completed, session.increment())
        assertEquals(IncrementResult.AlreadyCompleted, session.increment())
        assertEquals(3, session.count)
        assertTrue(session.isCompleted)
    }

    @Test
    fun `Завершение сворачивает раскрытую карточку, сброс обнуляет счёт`() {
        val session = ZikrSession(zikr(1, repetitions = 1))
        session.setExpanded(true)

        session.increment()
        assertFalse(session.state.value.isExpanded)

        session.setExpanded(true)
        session.reset()
        assertEquals(0, session.count)
        assertFalse(session.isCompleted)
        assertFalse(session.state.value.isExpanded)
    }

    @Test
    fun `Прогресс раздела и признак завершения`() = TestStorage.run { storage ->
        val store = loadedStore(storage)
        val sessions = store.sessions(AzkarSection.Morning)
        assertEquals(2, sessions.size)
        val progress = store.progress(AzkarSection.Morning)

        assertEquals(SectionProgress(completed = 0, total = 2), progress.value)

        sessions[0].increment()
        assertEquals(1, progress.value.completed)
        assertFalse(progress.value.isFinished)

        repeat(3) { sessions[1].increment() }
        assertTrue(progress.value.isFinished)
        assertEquals(1.0, progress.value.fraction)

        sessions[0].reset()
        assertEquals(1, progress.value.completed)
        store.resetProgress(AzkarSection.Morning)
        assertEquals(SectionProgress(completed = 0, total = 2), progress.value)
    }

    @Test
    fun `Разделы считаются независимо`() = TestStorage.run { storage ->
        val store = loadedStore(storage)

        store.sessions(AzkarSection.Evening).forEach { it.increment() }

        assertTrue(store.progress(AzkarSection.Evening).value.isFinished)
        assertEquals(0, store.progress(AzkarSection.Morning).value.completed)
    }

    @Test
    fun `Повторная загрузка не сбрасывает прогресс`() = TestStorage.run { storage ->
        val store = loadedStore(storage)
        store.sessions(AzkarSection.Morning)[0].increment()

        store.load(AzkarSection.Morning)

        assertEquals(1, store.progress(AzkarSection.Morning).value.completed)
    }

    @Test
    fun `Ошибка загрузки — состояние failed, повторная попытка разрешена`() = TestStorage.run { storage ->
        val flaky = FlakyRepository(repository, failures = 1)
        val store = loadedStore(storage, flaky)

        val failed = assertIs<Loadable.Failed>(store.state(AzkarSection.Morning).value)
        assertEquals(ContentError.ResourceMissing("azkar.json"), failed.error)
        assertFalse(store.state(AzkarSection.Morning).value.isLoadingOrLoaded)

        store.load(AzkarSection.Morning)
        assertEquals(2, store.sessions(AzkarSection.Morning).size)
    }

    @Test
    fun `Отменённая загрузка не оставляет раздел в состоянии loading`() = TestStorage.run { storage ->
        val gate = CompletableDeferred<Unit>()
        val store = AzkarStore(
            repository = object : ContentRepository by repository {
                override suspend fun azkar(section: AzkarSection): List<Zikr> {
                    gate.await()
                    return repository.azkar(section)
                }
            },
            storage = storage.storage,
            resetSettings = AzkarResetSettings(storage.storage),
            now = { date(2026, 10, 3, 8) },
            zone = { berlin },
            onUnreadableProgress = { _, error -> throw AssertionError(error) },
        )

        val loading = launch { store.load(AzkarSection.Morning) }
        runCurrent()
        assertEquals(Loadable.Loading, store.state(AzkarSection.Morning).value)
        loading.cancelAndJoin()
        assertEquals(Loadable.Idle, store.state(AzkarSection.Morning).value)

        gate.complete(Unit)
        store.load(AzkarSection.Morning)
        assertEquals(2, store.sessions(AzkarSection.Morning).size)
    }

    @Test
    fun `Восстановленный счёт ограничивается числом повторений`() {
        assertEquals(3, ZikrSession(zikr(1, repetitions = 3), count = 7).count)
        assertEquals(0, ZikrSession(zikr(1, repetitions = 3), count = -2).count)
    }

    @Test
    fun `Сброс карточки без счёта не сохраняет раздел`() {
        var saves = 0
        val session = ZikrSession(zikr(1, repetitions = 3)) { saves++ }

        session.reset()
        assertEquals(0, saves)
        session.increment()
        session.reset()
        assertEquals(2, saves)
    }

    @Test
    fun `Пустой раздел не считается завершённым`() {
        assertFalse(SectionProgress(completed = 0, total = 0).isFinished)
        assertEquals(0.0, SectionProgress(completed = 0, total = 0).fraction)
    }
}
