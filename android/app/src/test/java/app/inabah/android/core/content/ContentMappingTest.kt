package app.inabah.android.core.content

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
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
}
