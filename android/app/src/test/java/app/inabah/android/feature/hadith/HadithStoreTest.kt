package app.inabah.android.feature.hadith

import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.FlakyRepository
import app.inabah.android.core.content.InMemoryContentRepository
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.content.model.HadithTranslation
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** «Сборники хадисов». */
class HadithStoreTest {
    private fun hadith(number: Int, text: String = "Текст", narrator: String? = null) = Hadith(
        id = HadithId(HadithCollection.Nawawi, number),
        arabic = "حديث",
        translation = HadithTranslation(ContentLanguage.Base, narrator, text, source = null),
    )

    @Test
    fun `Загрузка сборника`() = runTest {
        val store = HadithStore(
            InMemoryContentRepository(hadiths = mapOf(HadithCollection.Nawawi to listOf(hadith(1), hadith(2)))),
        )

        store.loadAll()

        assertEquals(listOf(1, 2), store.hadiths(HadithCollection.Nawawi).map { it.number })
        assertTrue(store.hadiths(HadithCollection.Qudsi).isEmpty())
    }

    @Test
    fun `Ошибка контента — состояние ошибки`() = runTest {
        val repository = InMemoryContentRepository(hadiths = mapOf(HadithCollection.Nawawi to listOf(hadith(1))))
        val store = HadithStore(FlakyRepository(repository, failures = 1))

        store.load(HadithCollection.Nawawi)

        val failed = assertIs<Loadable.Failed>(store.state(HadithCollection.Nawawi))
        assertIs<ContentError.ResourceMissing>(failed.error)

        store.load(HadithCollection.Nawawi)
        assertEquals(listOf(1), store.hadiths(HadithCollection.Nawawi).map { it.number }, "повторная попытка")
    }

    @Test
    fun `Отменённая загрузка сборника не оставляет состояние loading`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = InMemoryContentRepository(hadiths = mapOf(HadithCollection.Nawawi to listOf(hadith(1))))
        val store = HadithStore(
            object : ContentRepository by repository {
                override suspend fun hadiths(collection: HadithCollection): List<Hadith> {
                    gate.await()
                    return repository.hadiths(collection)
                }
            },
        )

        val loading = launch { store.load(HadithCollection.Nawawi) }
        runCurrent()
        loading.cancelAndJoin()
        assertEquals(Loadable.Idle, store.state(HadithCollection.Nawawi))

        gate.complete(Unit)
        store.load(HadithCollection.Nawawi)
        assertEquals(1, store.hadiths(HadithCollection.Nawawi).size)
    }

    @Test
    fun `Превью строки — без вступления «От …»`() {
        val hadith = hadith(1, text = "От Абу Хурайры: сказал Посланник Аллаха ﷺ")

        assertEquals("сказал Посланник Аллаха ﷺ", hadith.previewText)
    }

    @Test
    fun `Превью строки — длинный текст обрезается до 80 символов с многоточием`() {
        val hadith = hadith(1, text = "слово ".repeat(30))

        // 80 символов = 13 × «слово » + «сл».
        assertEquals("слово ".repeat(13) + "сл…", hadith.previewText)
        assertEquals(HADITH_PREVIEW_LENGTH + 1, hadith.previewText.length)

        val exact = "а".repeat(HADITH_PREVIEW_LENGTH)
        assertEquals(exact, hadith(1, text = exact).previewText, "ровно 80 — без многоточия")
    }

    @Test
    fun `Превью строки — текст без вступления не меняется`() {
        assertEquals("Сказал Пророк ﷺ", hadith(1, text = "Сказал Пророк ﷺ").previewText)
    }

    @Test
    fun `Арабский текст — одним абзацем`() {
        val hadith = Hadith(
            id = HadithId(HadithCollection.Nawawi, 1),
            arabic = "عَنْ عُمَرَ\nقَالَ: \n«إِنَّمَا»",
            translation = null,
        )

        assertEquals("عَنْ عُمَرَ قَالَ: «إِنَّمَا»", hadith.arabicDisplayText)
    }
}
