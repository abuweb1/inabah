package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.Color
import app.inabah.android.core.designsystem.components.glassColors
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test

/** Стекло кнопок шапки — цвета со снимков iOS (android/docs/hadith: back, next-hadith, text-button; IMG_9729). */
class GlassColorsTest {
    private val white = Color(0xFFFFFFFF)

    // Каналы — до 18 из 255: сдвиги в OKLCH общие для двух разделов, а не подогнаны под каждый.
    private fun assertClose(expected: Long, actual: Color, tolerance: Int = 18) {
        val e = Color(expected)
        val diff = listOf(e.red - actual.red, e.green - actual.green, e.blue - actual.blue).maxOf { abs(it) * 255 }
        assertTrue(diff <= tolerance, "ожидалось ${hex(e)}, получено ${hex(actual)} (расхождение $diff)")
    }

    private fun hex(c: Color) = "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    @Test
    fun `Хадисы — стекло в покое и при нажатии как в iOS`() {
        val glass = glassColors(tint = Color(0xFF5ED6A6), barBackground = Color(0xFF1A5C4A), onAccent = white)
        assertClose(0xFF02543E, glass.fill)
        assertClose(0xFF189168, glass.rim)
        assertClose(0xFFB6FFF3, glass.icon)
        assertClose(0xFF01C384, glass.pressedFill)
        assertClose(0xFF1AEDA4, glass.pressedRim)
        assertClose(0xFF01FEC5, glass.glowNear)
        assertClose(0xFF018B62, glass.glowFar)
    }

    @Test
    fun `Азкары — стекло в покое и при нажатии как в iOS`() {
        // Эталоны android/docs/azkar: img.png (нажата «А+»), img_1.png (нажата «‹»).
        val glass = glassColors(tint = Color(0xFFB59BEA), barBackground = Color(0xFF5C33A0), onAccent = white)
        assertClose(0xFF5722AE, glass.fill)
        assertClose(0xFF8D46D4, glass.rim)
        assertClose(0xFFFFDFFF, glass.icon)
        assertClose(0xFFFF66FF, glass.pressedFill)
        assertClose(0xFFEE70FF, glass.pressedRim)
        assertClose(0xFFFFFFFF, glass.pressedIcon)
        assertClose(0xFFFF5EFF, glass.glowNear)
        assertClose(0xFF8334FF, glass.glowFar)
    }

    @Test
    fun `Прозрачный навбар — полупрозрачное стекло и белый значок`() {
        val tint = Color(0xFF8FB4F0)
        val glass = glassColors(tint, barBackground = null, onAccent = white)
        assertTrue(glass.fill.alpha < 1f && glass.rim.alpha < 1f)
        assertTrue(glass.icon == white)
    }
}
