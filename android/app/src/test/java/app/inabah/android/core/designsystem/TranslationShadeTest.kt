package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.inabah.android.core.designsystem.components.TranslationColors
import app.inabah.android.core.designsystem.components.shadeAt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** «Фон перевода»: сдвиг карточек перевода и предел осветления во всех палитрах. */
class TranslationShadeTest {
    private val themes = ThemeStyle.entries.map { it to it.theme }

    private fun ratio(foreground: Color, background: Color) =
        Contrast.ratio(foreground.compositeOver(background), background)

    @Test
    fun `По центру — та же тема`() {
        for ((style, theme) in themes) assertEquals(theme, theme.withTranslationShade(0f), "стиль $style")
    }

    @Test
    fun `Меняются только фоны перевода`() {
        for ((style, theme) in themes) {
            for (shade in listOf(-1f, 1f)) {
                val shaded = theme.withTranslationShade(shade).palette
                val expected = theme.palette.copy(
                    zikrCard = shaded.zikrCard, accentDim = shaded.accentDim, hadithCard = shaded.hadithCard, zikrSource = shaded.zikrSource,
                )
                assertEquals(expected, shaded, "стиль $style, сдвиг $shade")
                assertEquals(theme.gradients, theme.withTranslationShade(shade).gradients, "стиль $style")
            }
        }
    }

    @Test
    fun `Влево темнее, вправо светлее — плавно`() {
        val steps = listOf(-1f, -0.5f, -0.1f, 0f, 0.1f, 0.5f, 1f)
        for ((style, theme) in themes) {
            for (card in listOf<(Palette) -> Color>({ it.zikrCard }, { it.accentDim }, { it.hadithCard })) {
                val luminance = steps.map { Contrast.luminance(card(theme.withTranslationShade(it).palette)) }
                assertTrue(luminance.zipWithNext().all { (a, b) -> a <= b }, "стиль $style: $luminance")
                assertTrue(luminance.first() < luminance[3], "стиль $style: −1 не темнее")
            }
        }
    }

    @Test
    fun `Вне диапазона — как край`() {
        val theme = ThemeStyle.Sections.theme
        assertEquals(theme.withTranslationShade(1f), theme.withTranslationShade(3f))
        assertEquals(theme.withTranslationShade(-1f), theme.withTranslationShade(-3f))
    }

    @Test
    fun `Правый край заметно светлее в обеих карточках`() {
        // Регрессия (эмулятор 2026-10-10): источник зикра ниже 4,5:1 не давал осветлить карточку зикра вовсе.
        for ((style, theme) in themes) {
            for (card in TranslationCard.entries) {
                val limit = translationLightLimit(theme.palette, card)
                assertTrue(limit >= 0.2f, "стиль $style, $card: предел ${"%.3f".format(limit)}")
            }
        }
    }

    @Test
    fun `На краях ползунка перевод читается во всех палитрах`() {
        for ((style, theme) in themes) {
            for (shade in listOf(-1f, 1f)) {
                val p = theme.withTranslationShade(shade).palette
                val base = theme.palette
                val azkar = TranslationColors.azkar(p)
                val hadith = TranslationColors.hadith(p)
                fun check(name: String, foreground: Color, background: Color, minimum: Double, baseRatio: Double) {
                    val actual = ratio(foreground, background)
                    val need = minOf(minimum, baseRatio) - 1e-3
                    assertTrue(actual >= need, "стиль $style, сдвиг $shade, $name: ${"%.2f".format(actual)} < ${"%.2f".format(need)}")
                }
                check("перевод зикра", azkar.text, azkar.fill, 7.0, ratio(base.textPrimary, base.accentDim))
                check("транскрипция", azkar.lead, p.zikrCard, 4.5, ratio(base.textSecondary, base.zikrCard))
                check("источник зикра", azkar.source, p.zikrCard, 3.0, ratio(base.zikrSource, base.zikrCard))
                check("перевод хадиса", hadith.text, p.hadithCard, 7.0, ratio(base.textPrimary, base.hadithCard))
                check("«Передал»", hadith.lead, p.hadithCard, 4.5, ratio(base.noteText, base.hadithCard))
                check("источник хадиса", hadith.source, p.hadithCard, 4.5, ratio(base.onAccentSecondary, base.hadithCard))
                check(
                    "сноска", p.noteText, p.noteFill.compositeOver(p.hadithCard), 4.5,
                    ratio(base.noteText, base.noteFill.compositeOver(base.hadithCard)),
                )
            }
        }
    }

    @Test
    fun `Предел осветления — от 0 до 1`() {
        for ((style, theme) in themes) {
            for (card in TranslationCard.entries) {
                val limit = translationLightLimit(theme.palette, card)
                assertTrue(limit in 0f..1f, "стиль $style, $card: $limit")
            }
        }
    }

    // Дорожка 426 px, края отступают на радиус бегунка 13 — половина дорожки 200 px, середина — 213.
    private fun shade(x: Float) = shadeAt(x, width = 426f, inset = 13f)

    @Test
    fun `Ползунок — края, середина и прилипание к 0`() {
        assertEquals(-1f, shade(13f))
        assertEquals(1f, shade(413f))
        assertEquals(0f, shade(213f))
        assertEquals(0f, shade(213f + 200f * 0.05f))
        assertEquals(0.5f, shade(313f), 1e-4f)
        assertEquals(-1f, shade(-100f))
        assertEquals(1f, shade(900f))
    }
}
