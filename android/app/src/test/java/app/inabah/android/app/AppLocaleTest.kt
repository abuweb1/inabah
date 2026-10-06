package app.inabah.android.app

import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Test

/** Язык интерфейса: первый поддержанный из языков системы, иначе русский. */
class AppLocaleTest {
    private fun tags(vararg tags: String) = tags.map(Locale::forLanguageTag)

    @Test
    fun `Система на английском — интерфейс русский`() {
        assertEquals("ru", uiLanguage(tags("en-US", "de-DE")).language)
    }

    @Test
    fun `Русский в системе есть — русский, без региона`() {
        assertEquals(Locale.forLanguageTag("ru"), uiLanguage(tags("ru-RU")))
    }

    @Test
    fun `Языков системы нет — русский`() {
        assertEquals("ru", uiLanguage(emptyList()).language)
    }

    @Test
    fun `Появится перевод — выбирается первый поддержанный по порядку системы`() {
        val supported = listOf("ru", "en")
        assertEquals("en", uiLanguage(tags("de-DE", "en-GB", "ru-RU"), supported).language)
        assertEquals("ru", uiLanguage(tags("ru-RU", "en-GB"), supported).language)
    }
}
