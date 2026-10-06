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

    // Эталоны 2026-10-05 (android/docs/mahraj-thema, settings-thema, azkar-thema): вечерние — тёмная шапка,
    // хадисы и утренние — средняя; нажатие ↺ — над прозрачной шапкой настроек.

    @Test
    fun `Янтарная — над тёмной шапкой стекло светлее шапки, нажатие красно-оранжевое`() {
        val glass = glassColors(tint = Color(0xFFE8A85A), barBackground = Color(0xFF231104), onAccent = white)
        assertClose(0xFF351E0E, glass.fill)
        assertClose(0xFF6B4930, glass.rim)
        assertClose(0xFFFFFCEC, glass.icon)
        assertClose(0xFFE47629, glass.pressedFill)
        assertClose(0xFFFBFFFF, glass.pressedIcon)
        assertClose(0xFFED7B25, glass.glowNear)
        assertClose(0xFF522F19, glass.glowFar)
    }

    @Test
    fun `Янтарная — над шапкой хадисов и прозрачной шапкой как в iOS`() {
        val header = glassColors(tint = Color(0xFFE8A85A), barBackground = Color(0xFF754213), onAccent = white)
        assertClose(0xFF713101, header.fill)
        assertClose(0xFFD05C1F, header.rim)
        assertClose(0xFFFFF6BC, header.icon)
        val transparent = glassColors(tint = Color(0xFFE8A85A), barBackground = null, onAccent = white)
        assertClose(0xFFFE7C00, transparent.pressedFill)
        assertClose(0xFFFFEF43, transparent.pressedIcon)
    }

    @Test
    fun `Графит — над тёмной шапкой и шапкой хадисов как в iOS`() {
        val evening = glassColors(tint = Color(0xFF8FB4F0), barBackground = Color(0xFF0D1527), onAccent = white)
        assertClose(0xFF182339, evening.fill)
        assertClose(0xFF414F74, evening.rim)
        assertClose(0xFFF7FFFF, evening.icon)
        assertClose(0xFF5886F5, evening.pressedFill)
        assertClose(0xFFFFFFFA, evening.pressedIcon)
        assertClose(0xFF6097FE, evening.glowNear)
        assertClose(0xFF2A3A5E, evening.glowFar)
        val hadith = glassColors(tint = Color(0xFF8FB4F0), barBackground = Color(0xFF3A4E83), onAccent = white)
        assertClose(0xFF253C80, hadith.fill)
        assertClose(0xFF426DD3, hadith.rim)
        assertClose(0xFFE8FFFF, hadith.icon)
    }

    @Test
    fun `Нажатие над прозрачной шапкой — бледное у графита, пурпурное у фиолетовой`() {
        val graphite = glassColors(tint = Color(0xFF8FB4F0), barBackground = null, onAccent = white)
        assertClose(0xFF889DD4, graphite.pressedFill)
        assertClose(0xFFFFFFFF, graphite.pressedIcon)
        val violet = glassColors(tint = Color(0xFFB59BEA), barBackground = null, onAccent = white)
        assertClose(0xFFE851FF, violet.pressedFill)
        assertClose(0xFFFFFFFF, violet.pressedIcon)
    }

    @Test
    fun `Прозрачный навбар — полупрозрачное стекло и белый значок`() {
        val tint = Color(0xFF8FB4F0)
        val glass = glassColors(tint, barBackground = null, onAccent = white)
        assertTrue(glass.fill.alpha < 1f && glass.rim.alpha < 1f)
        assertTrue(glass.icon == white)
    }
}
