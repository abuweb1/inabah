package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.compositeOver
import kotlin.test.assertTrue
import org.junit.Test

/** Сноски к переводу хадиса: цвета — производные от `tabHadith`, читаются во всех палитрах. */
class NoteColorsTest {
    @Test
    fun `Текст сноски читается на своей подложке на всём фоне хадисов (≥ 4,5 к 1)`() {
        for (style in ThemeStyle.entries) {
            val palette = style.theme.palette
            val text = Contrast.luminance(palette.noteText)
            // Подложка сноски — внутри карточки перевода (subtleFill) на градиенте экрана хадиса.
            for (background in style.theme.gradients.hadithBackground.colors) {
                val fill = palette.noteFill.compositeOver(palette.subtleFill.compositeOver(background))
                val ratio = Contrast.ratio(text, Contrast.luminance(fill))
                assertTrue(ratio >= 4.5, "стиль $style, фон $background: контраст ${"%.2f".format(ratio)}")
            }
        }
    }
}
