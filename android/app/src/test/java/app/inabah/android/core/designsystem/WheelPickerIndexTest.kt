package app.inabah.android.core.designsystem

import app.inabah.android.core.designsystem.components.wheelFirstIndex
import app.inabah.android.core.designsystem.components.wheelValue
import kotlin.test.assertEquals
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
}
