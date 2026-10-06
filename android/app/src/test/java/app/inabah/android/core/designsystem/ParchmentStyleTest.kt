package app.inabah.android.core.designsystem

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** «Фон арабского текста»: читаемость каждого варианта и что меняется в теме. */
class ParchmentStyleTest {
    @Test
    fun `Текст на каждом оттенке каждого фона — контраст не ниже 7 к 1`() {
        // Арабский с мелкими огласовками — порог AAA, а не 4,5.
        for (style in ParchmentStyle.entries) {
            val text = Contrast.luminance(style.colors.text)
            for (background in style.colors.backgrounds) {
                val ratio = Contrast.ratio(text, Contrast.luminance(background))
                assertTrue(ratio >= MIN_CONTRAST, "${style.name}: контраст %.2f".format(ratio))
            }
        }
    }

    @Test
    fun `Значок «N раз», рамка и звёздочки видны на каждом фоне — контраст не ниже 4,5 к 1`() {
        // Регрессия (эмулятор 2026-10-06): тёмно-зелёный значок на «Ночном» почти пропадал.
        for (style in ParchmentStyle.entries) {
            val accent = Contrast.luminance(style.colors.accent)
            for (background in style.colors.backgrounds) {
                val ratio = Contrast.ratio(accent, Contrast.luminance(background))
                assertTrue(ratio >= MIN_ACCENT_CONTRAST, "${style.name}: акцент %.2f".format(ratio))
            }
        }
    }

    @Test
    fun `Пергамент по умолчанию — как раньше`() {
        val palette = Palette.Sections
        assertEquals(
            ParchmentColors(
                palette.parchmentLight, palette.gold, palette.parchmentMid, palette.parchmentDeep, palette.parchmentInk,
                accent = palette.successDeep,
            ),
            ParchmentStyle.Classic.colors,
        )
        assertEquals(Theme.Sections, Theme.Sections.withParchment(ParchmentStyle.Classic))
        // Регрессия (ревью 2026-10-06): от темы с другим фоном «Пергамент» возвращает свои цвета, а не чужие.
        val fromNight = Theme.Sections.withParchment(ParchmentStyle.Night).withParchment(ParchmentStyle.Classic)
        assertEquals(Theme.Sections, fromNight)
    }

    @Test
    fun `Вариант меняет пергамент и текст на нём, но не золото и чернила кнопок`() {
        for (base in ThemeStyle.entries.map { it.theme }) {
            val theme = base.withParchment(ParchmentStyle.Night)
            val colors = ParchmentStyle.Night.colors
            assertEquals(colors.backgrounds, theme.gradients.parchment.colors)
            assertEquals(colors.text, theme.palette.parchmentText)
            assertEquals(colors.accent, theme.palette.parchmentAccent)
            assertEquals(base.palette.parchmentInk, theme.palette.parchmentInk)
            assertEquals(base.palette.gold, theme.palette.gold)
            assertEquals(base.gradients.counterButton, theme.gradients.counterButton)
            assertEquals(base.gradients.parchmentStripe, theme.gradients.parchmentStripe)
            assertEquals(base.palette.successDeep, theme.palette.successDeep)
        }
    }

    @Test
    fun `Ключи вариантов разные и читаются обратно`() {
        assertEquals(ParchmentStyle.entries.size, ParchmentStyle.entries.map { it.key }.toSet().size)
        for (style in ParchmentStyle.entries) assertEquals(style, ParchmentStyle.fromKey(style.key))
        assertNull(ParchmentStyle.fromKey("sunset"))
    }

    @Test
    fun `Смена фона анимируется — середина перехода между вариантами`() {
        val from = Theme.Sections
        val to = Theme.Sections.withParchment(ParchmentStyle.Night)
        val half = Theme.lerp(from, to, 0.5f)
        assertTrue(half.palette.parchmentText != from.palette.parchmentText && half.palette.parchmentText != to.palette.parchmentText)
        assertTrue(half.palette.parchmentHighlight != from.palette.parchmentHighlight)
    }

    private companion object {
        const val MIN_CONTRAST = 7.0
        const val MIN_ACCENT_CONTRAST = 4.5
    }
}
