package app.inabah.android.core.designsystem

import app.inabah.android.core.designsystem.components.stepAt
import kotlin.test.assertEquals
import org.junit.Test

/** Ползунок с засечками: шаг под касанием. */
class StepSliderTest {
    // Дорожка 426 px, края отступают на радиус бегунка 13 — между засечками по 100 px.
    private fun step(x: Float, count: Int = 5) = stepAt(x, width = 426f, inset = 13f, count = count)

    @Test
    fun `Касание засечки — её шаг`() {
        assertEquals(0, step(13f))
        assertEquals(2, step(213f))
        assertEquals(4, step(413f))
    }

    @Test
    fun `Между засечками — ближайшая`() {
        assertEquals(1, step(150f))
        assertEquals(2, step(170f))
    }

    @Test
    fun `За краями дорожки — крайний шаг`() {
        assertEquals(0, step(-50f))
        assertEquals(4, step(900f))
    }

    @Test
    fun `Три шага и один шаг`() {
        assertEquals(1, stepAt(213f, width = 426f, inset = 13f, count = 3))
        assertEquals(0, stepAt(400f, width = 426f, inset = 13f, count = 1))
    }
}
