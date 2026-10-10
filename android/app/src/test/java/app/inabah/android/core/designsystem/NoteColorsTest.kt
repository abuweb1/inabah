package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.compositeOver
import app.inabah.android.core.designsystem.components.TranslationColors
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Перевод и сноски хадиса — на карточке `hadithCard`, цвета — производные от `tabHadith`, читаются во всех палитрах. */
class NoteColorsTest {
    // Текст с прозрачностью (`onAccentSecondary` — белый 60 %) — уже наложенным на фон.
    private fun ratio(foreground: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color) =
        Contrast.ratio(foreground.compositeOver(background), background)

    @Test
    fun `Перевод хадиса читается на своей карточке во всех палитрах`() {
        for (style in ThemeStyle.entries) {
            val palette = style.theme.palette
            val colors = TranslationColors.hadith(palette)
            val card = palette.hadithCard
            val bubble = colors.fill.compositeOver(card)
            assertTrue(ratio(colors.text, bubble) >= 7.0, "стиль $style: перевод ${"%.2f".format(ratio(colors.text, bubble))}")
            assertTrue(ratio(colors.lead, card) >= 4.5, "стиль $style: «Передал» ${"%.2f".format(ratio(colors.lead, card))}")
            assertTrue(ratio(colors.source, card) >= 4.5, "стиль $style: источник ${"%.2f".format(ratio(colors.source, card))}")
            // Сноски — тот же пузырь, текст — noteText.
            assertTrue(ratio(palette.noteText, bubble) >= 4.5, "стиль $style: сноска ${"%.2f".format(ratio(palette.noteText, bubble))}")
        }
    }

    @Test
    fun `В единых стилях карточки перевода — цвет карточки стиля`() {
        for (style in ThemeStyle.entries.filter { it != ThemeStyle.Sections }) {
            assertEquals(style.theme.palette.card, style.theme.palette.hadithCard, "стиль $style")
            assertEquals(style.theme.palette.card, style.theme.palette.zikrCard, "стиль $style")
            assertEquals(style.theme.palette.textTertiary, style.theme.palette.zikrSource, "стиль $style")
        }
    }
}
