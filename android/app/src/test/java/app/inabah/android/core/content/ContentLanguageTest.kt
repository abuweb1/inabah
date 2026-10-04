package app.inabah.android.core.content

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/** «Выбор языка контента». */
class ContentLanguageTest {
    @Test
    fun `Базовый язык всегда последний и не дублируется`() {
        val cases = listOf(
            listOf("en-US", "ru-RU") to listOf("en", "ru"),
            listOf("ru-RU", "en") to listOf("ru", "en"),
            listOf("de-DE", "de-AT") to listOf("de", "ru"),
            emptyList<String>() to listOf("ru"),
        )
        for ((preferred, expected) in cases) {
            val priority = ContentLanguagePriority.fromLanguageTags(preferred)
            assertEquals(expected, priority.languages.map { it.code }, "для $preferred")
        }
    }

    @Test
    fun `Берётся первый доступный перевод по приоритету`() {
        val english = ContentLanguage.of("en")
        val priority = ContentLanguagePriority(listOf(english, ContentLanguage.Base))
        val translations = mapOf(ContentLanguage.Base to "Русский", english to "English")

        val selected = assertNotNull(priority.select(translations))

        assertEquals("en", selected.language.code)
        assertEquals("English", selected.translation)
    }

    @Test
    fun `Без перевода на языке пользователя — базовый язык`() {
        val priority = ContentLanguagePriority.fromLanguageTags(listOf("fr-FR"))

        val selected = assertNotNull(priority.select(mapOf(ContentLanguage.Base to "Русский")))

        assertEquals(ContentLanguage.Base, selected.language)
    }

    @Test
    fun `Нет ни одного подходящего перевода`() {
        val priority = ContentLanguagePriority(listOf(ContentLanguage.Base))

        assertNull(priority.select(mapOf(ContentLanguage.of("en") to "English")))
    }

    @Test
    fun `Код языка нормализуется к нижнему регистру`() {
        assertEquals(ContentLanguage.Base, ContentLanguage.of("RU"))
    }
}
