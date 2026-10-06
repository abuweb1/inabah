package app.inabah.android.core.designsystem

import app.inabah.android.core.designsystem.components.WheelRows
import app.inabah.android.core.designsystem.components.wheelDistance
import app.inabah.android.core.designsystem.components.wheelFirstIndex
import app.inabah.android.core.designsystem.components.wheelValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Барабан времени: позиция списка ↔ значение на полосе выбора (две строки ниже первой видимой). */
class WheelPickerIndexTest {
    private fun centered(value: Int, count: Int) = wheelFirstIndex(value, count) + 2

    @Test
    fun `Начальная позиция ставит на полосу то же значение`() {
        for (count in listOf(24, 60)) {
            for (value in 0 until count) {
                assertEquals(value, wheelValue(centered(value, count), count))
            }
        }
    }

    @Test
    fun `По кругу после 59 идёт 00, перед 00 — 59`() {
        assertEquals(0, wheelValue(centered(59, 60) + 1, 60))
        assertEquals(58, wheelValue(centered(59, 60) - 1, 60))
        assertEquals(59, wheelValue(centered(0, 60) - 1, 60))
    }

    @Test
    fun `Часы — 24 значения, после 23 идёт 0`() {
        assertEquals(0, wheelValue(centered(23, 24) + 1, 24))
    }

    @Test
    fun `Барабан без круга (AM · PM) — крайние значения встают на полосу, дальше не едет`() {
        val rows = WheelRows(count = 2, looping = false)
        assertEquals(6, rows.size)
        // На полосе — третья видимая строка: первая видимая + 2.
        assertEquals(0, rows.valueAt(rows.firstIndex(0) + 2))
        assertEquals(1, rows.valueAt(rows.firstIndex(1) + 2))
        assertTrue(rows.isBlank(0) && rows.isBlank(5) && !rows.isBlank(2))
        assertEquals(3, rows.shifted(2, 5), "не дальше PM")
        assertEquals(2, rows.shifted(3, -5), "не дальше AM")
        assertEquals(-1, rows.distance(from = 1, to = 0))
    }

    @Test
    fun `Возврат к принятому значению — кратчайшим путём по кругу`() {
        assertEquals(-7, wheelDistance(from = 12, to = 5, count = 24))
        assertEquals(2, wheelDistance(from = 23, to = 1, count = 24))
        assertEquals(-2, wheelDistance(from = 1, to = 59, count = 60))
        assertEquals(0, wheelDistance(from = 30, to = 30, count = 60))
    }
}
