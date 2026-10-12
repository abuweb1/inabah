package app.inabah.android.core.content

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.CollectionAuthor
import app.inabah.android.core.content.model.CollectionInfo
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithNote
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Данные → модели на маленьком JSON (аудит 2026-10-06: правила разбора проверялись только на настоящих
 * данных — и ловили ошибку, лишь когда она уже в `data/`).
 */
class ContentMappingTest {
    private fun repository(files: Map<String, String>, dispatcher: TestDispatcher) = AssetContentRepository(
        source = { path -> requireNotNull(files[path.removePrefix("data/")]) { "нет $path" }.byteInputStream() },
        priority = ContentLanguagePriority(listOf(ContentLanguage.of("ru"))),
        ioDispatcher = dispatcher,
    )

    private val azkar = """
        {
          "morning": [
            { "id": 1, "arabic": "سُبْحَانَ اللَّهِ", "max": 0, "audio": "morning_01.mp3", "future": true,
              "translations": { "ru": { "text": "Пречист Аллах", "translit": "  ", "source": "\n" } } }
          ],
          "evening": [
            { "id": 1, "arabic": "سُبْحَانَ اللَّهِ", "max": 3, "audio": "evening_01.mp3",
              "translations": { "ru": { "text": "первый", "translit": " Субхана-Ллах ", "source": " Муслим " },
                                "RU": { "text": "второй" } } }
          ]
        }
    """.trimIndent()

    @Test
    fun `max меньше 1 — один повтор`() = runTest {
        val zikr = repository(mapOf("azkar.json" to azkar), StandardTestDispatcher(testScheduler)).azkar(AzkarSection.Morning).single()

        assertEquals(1, zikr.repetitions)
    }

    @Test
    fun `Пустые и пробельные транскрипция и источник — нет, края обрезаются, неизвестные поля не мешают`() = runTest {
        val repository = repository(mapOf("azkar.json" to azkar), StandardTestDispatcher(testScheduler))

        val morning = repository.azkar(AzkarSection.Morning).single().translation!!
        assertNull(morning.transliteration)
        assertNull(morning.source)

        val evening = repository.azkar(AzkarSection.Evening).single().translation!!
        assertEquals("Субхана-Ллах", evening.transliteration)
        assertEquals("Муслим", evening.source)
    }

    @Test
    fun `Ключи языка «ru» и «RU» — берётся первый по порядку файла`() = runTest {
        val zikr = repository(mapOf("azkar.json" to azkar), StandardTestDispatcher(testScheduler)).azkar(AzkarSection.Evening).single()

        assertEquals("первый", zikr.translation!!.text)
    }

    // 2026-10-06: записей пока нет — в данных нет поля audio, зикр читается и показывает «Аудио скоро».
    @Test
    fun `Зикр без поля audio — записи нет, с полем — есть`() = runTest {
        val noAudio = """{ "morning": [ { "id": 1, "arabic": "سُبْحَانَ اللَّهِ", "max": 1, "translations": { "ru": { "text": "т" } } } ], "evening": [] }"""
        val zikr = repository(mapOf("azkar.json" to noAudio), StandardTestDispatcher(testScheduler)).azkar(AzkarSection.Morning).single()
        assertNull(zikr.audioFileName)
        assertFalse(zikr.hasAudio)

        val withAudio = repository(mapOf("azkar.json" to azkar), StandardTestDispatcher(testScheduler)).azkar(AzkarSection.Morning).single()
        assertTrue(withAudio.hasAudio)
    }

    @Test
    fun `Хадис — пустой передатчик и источник становятся отсутствующими`() = runTest {
        val nawawi = """[ { "id": 1, "arabic": "إِنَّمَا", "translations": { "ru": { "text": "Перевод", "rawi": " ", "source": "" } } } ]"""
        val hadith = repository(mapOf("nawawi.json" to nawawi), StandardTestDispatcher(testScheduler))
            .hadiths(HadithCollection.Nawawi).single()

        assertNull(hadith.translation!!.narrator)
        assertNull(hadith.translation!!.source)
        assertEquals("Перевод", hadith.translation!!.text)
    }

    @Test
    fun `Передатчик зикра — необязательный, пустой становится отсутствующим`() = runTest {
        val file = """{ "morning": [
            { "id": 1, "arabic": "سُبْحَانَ اللَّهِ", "max": 1, "translations": { "ru": { "text": "а", "rawi": " Абу Хурайра " } } },
            { "id": 2, "arabic": "سُبْحَانَ اللَّهِ", "max": 1, "translations": { "ru": { "text": "б", "rawi": "  " } } },
            { "id": 3, "arabic": "سُبْحَانَ اللَّهِ", "max": 1, "translations": { "ru": { "text": "в" } } } ], "evening": [] }"""
        val azkar = repository(mapOf("azkar.json" to file), StandardTestDispatcher(testScheduler)).azkar(AzkarSection.Morning)

        assertEquals(listOf("Абу Хурайра", null, null), azkar.map { it.translation!!.narrator })
    }

    @Test
    fun `Сноски — в порядке файла, края обрезаются, с пустым словом или пояснением отбрасываются`() = runTest {
        val qudsi = """[
            { "id": 1, "arabic": "قَالَ", "translations": { "ru": { "text": "Перевод", "notes": [
                { "term": " Слово ", "text": " Пояснение. " },
                { "term": "", "text": "без слова" },
                { "term": "без пояснения", "text": "  " },
                { "term": "Различия передач", "text": "У ат-Тирмизи — иначе.", "future": 1 } ] } } },
            { "id": 2, "arabic": "قَالَ", "translations": { "ru": { "text": "Перевод", "notes": [] } } },
            { "id": 3, "arabic": "قَالَ", "translations": { "ru": { "text": "Перевод" } } }
        ]"""
        val hadiths = repository(mapOf("qudsi.json" to qudsi), StandardTestDispatcher(testScheduler))
            .hadiths(HadithCollection.Qudsi)

        assertEquals(
            listOf(HadithNote("Слово", "Пояснение."), HadithNote("Различия передач", "У ат-Тирмизи — иначе.")),
            hadiths[0].translation!!.notes,
        )
        assertEquals(emptyList(), hadiths[1].translation!!.notes, "пустой массив")
        assertEquals(emptyList(), hadiths[2].translation!!.notes, "нет поля")
    }

    private val collections = """
        {
          "nawawi": { "future": 1, "translations": {
            "en": { "about": ["English"] },
            "ru": { "authors": [ { "name": " Имам ан-Навави ", "years": "  " }, { "name": "", "years": "" },
                                 { "name": "Ибн Раджаб", "years": " 736–795 гг. х. " } ],
                    "about": [ " Первый абзац. ", "", "  ", "Второй абзац." ], "future": true } } },
          "qudsi": { "translations": { "en": { "authors": [ { "name": "Someone" } ], "about": ["English"] } } }
        }
    """.trimIndent()

    @Test
    fun `О сборнике — заготовка без имени и пустые абзацы отбрасываются, пустые годы — нет, края обрезаются`() = runTest {
        val repository = repository(mapOf("collections.json" to collections), StandardTestDispatcher(testScheduler))

        val info = repository.collectionInfo(HadithCollection.Nawawi)

        assertEquals(
            listOf(CollectionAuthor("Имам ан-Навави", years = null), CollectionAuthor("Ибн Раджаб", "736–795 гг. х.")),
            info.authors,
        )
        assertEquals(listOf("Первый абзац.", "Второй абзац."), info.about)
        assertEquals(ContentLanguage.Base, info.language, "ru — по приоритету, не первый en")
    }

    @Test
    fun `О сборнике — нет перевода на доступных языках или сборника нет в файле — пусто`() = runTest {
        val repository = repository(mapOf("collections.json" to collections), StandardTestDispatcher(testScheduler))

        assertEquals(CollectionInfo.Empty, repository.collectionInfo(HadithCollection.Qudsi), "только en")
        assertEquals(CollectionInfo.Empty, repository.collectionInfo(HadithCollection.Ajurri), "нет в файле")
    }
}
