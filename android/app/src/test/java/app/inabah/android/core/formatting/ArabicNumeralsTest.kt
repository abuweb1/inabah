package app.inabah.android.core.formatting

import kotlin.test.assertEquals
import org.junit.Test

class ArabicNumeralsTest {
    @Test
    fun `Арабско-индийские цифры без разделителей`() {
        val cases = listOf(1 to "١", 12 to "١٢", 40 to "٤٠", 1000 to "١٠٠٠")
        for ((number, expected) in cases) {
            assertEquals(expected, number.toArabicIndicDigits(), "для $number")
        }
    }
}
