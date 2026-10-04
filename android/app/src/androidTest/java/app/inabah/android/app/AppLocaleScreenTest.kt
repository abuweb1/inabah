package app.inabah.android.app

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inabah.android.R
import app.inabah.android.core.formatting.formatPercent
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Язык экрана при нерусской системе (эмулятор — en-US): склонение и проценты русские, а язык системы
 * для выбора перевода контента не подменяется.
 */
@RunWith(AndroidJUnit4::class)
class AppLocaleScreenTest {
    @Test
    fun englishSystemGivesRussianPluralsAndPercent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val resources = activity.resources
                assertEquals("ru", resources.configuration.locales[0].language)
                assertEquals("16 зикров · После фаджра", resources.getQuantityString(R.plurals.azkar_morning_meta, 16, 16))
                assertEquals("13 %", formatPercent(13, resources.configuration.locales[0]).replace(' ', ' '))
                // Выбор языка перевода (AppContainer) читает языки системы — они прежние.
                assertEquals(Resources.getSystem().configuration.locales[0], LocaleListCompat.getAdjustedDefault()[0])
            }
        }
    }

    @Test
    fun overrideKeepsOnlyLanguage() {
        val system = Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag("en-US"))) }
        val override = uiLocaleOverride(system)
        assertEquals(LocaleList(Locale.forLanguageTag("ru")), override.locales)
        // Остальные поля не заданы — шрифт, плотность и тема остаются системными.
        assertEquals(0f, override.fontScale, 0f)
    }
}
