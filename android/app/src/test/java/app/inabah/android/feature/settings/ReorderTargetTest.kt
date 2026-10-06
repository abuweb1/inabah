package app.inabah.android.feature.settings

import kotlin.test.assertEquals
import org.junit.Test

/** Перестановка сборников: место строки при перетаскивании. */
class ReorderTargetTest {
    // Строки по 100 px, три строки.
    private fun target(index: Int, offset: Float) = reorderTarget(index, offset, step = 100f, count = 3)

    @Test
    fun `До середины соседней строки — место не меняется`() {
        assertEquals(0, target(0, 49f))
        assertEquals(1, target(1, -49f))
    }

    @Test
    fun `За серединой соседней — её место, вверх и вниз`() {
        assertEquals(1, target(0, 51f))
        assertEquals(0, target(1, -51f))
    }

    @Test
    fun `Быстрый рывок через две строки — сразу через две`() {
        assertEquals(2, target(0, 180f))
    }

    @Test
    fun `За краями списка — крайнее место`() {
        assertEquals(0, target(0, -500f))
        assertEquals(2, target(2, 500f))
    }

    @Test
    fun `Высота строки ещё не измерена — место не меняется`() {
        assertEquals(1, reorderTarget(1, 300f, step = 0f, count = 3))
    }
}
