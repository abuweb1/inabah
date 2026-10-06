package app.inabah.android.core.formatting

import kotlin.test.assertEquals
import org.junit.Test

/** Время плеера `м:сс`. */
class PlaybackTimeTest {
    @Test
    fun `Прошедшее — вниз до секунды`() {
        assertEquals("0:02", formatElapsed(2_980))
        assertEquals("1:05", formatElapsed(65_999))
    }

    @Test
    fun `Длительность — до ближайшей секунды`() {
        assertEquals("0:03", formatDuration(2_980))
        assertEquals("0:45", formatDuration(45_400))
    }

    @Test
    fun `Отрицательное и ноль — 0·00`() {
        assertEquals("0:00", formatElapsed(-5))
        assertEquals("0:00", formatDuration(0))
    }
}
