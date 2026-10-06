package app.inabah.android.core.designsystem

import androidx.compose.ui.graphics.lerp
import kotlin.test.assertEquals
import org.junit.Test

/** Смена палитры: промежуточная тема между двумя стилями. */
class ThemeTransitionTest {
    private val from = ThemeStyle.Sections.theme
    private val to = ThemeStyle.Emerald.theme

    @Test
    fun `Начало и конец перехода — сами темы`() {
        assertEquals(from, Theme.lerp(from, to, 0f))
        assertEquals(to, Theme.lerp(from, to, 1f))
    }

    @Test
    fun `Середина перехода — середина каждого цвета`() {
        val half = Theme.lerp(from, to, 0.5f)
        assertEquals(lerp(from.palette.background, to.palette.background, 0.5f), half.palette.background)
        assertEquals(lerp(from.palette.tabAzkar, to.palette.tabAzkar, 0.5f), half.palette.tabAzkar)
    }

    @Test
    fun `Градиент в середине — цвета посередине, угол и стопы те же`() {
        val half = Theme.lerp(from, to, 0.5f).gradients.morningCard
        val expected = from.gradients.morningCard.colors.zip(to.gradients.morningCard.colors) { a, b -> lerp(a, b, 0.5f) }
        assertEquals(expected, half.colors)
        // Единые стили — с теми же стопами и углом, что «По умолчанию»: промежуточный — тоже.
        assertEquals(to.gradients.morningCard.withColors(expected), half)
    }
}
