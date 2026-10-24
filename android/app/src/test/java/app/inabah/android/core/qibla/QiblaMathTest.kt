package app.inabah.android.core.qibla

import kotlin.test.assertEquals
import org.junit.Test

/**
 * Направление на Каабу. Ожидания посчитаны независимо - через векторы на сфере, не формулой из кода -
 * и совпадают с опубликованными: Нью-Йорк 58,48°, Лондон 118,99°, Джакарта 295,15°.
 */
class QiblaMathTest {
    @Test
    fun `Направление из контрольных городов`() {
        assertBearing(58.48, 40.7128, -74.0060, "Нью-Йорк")
        assertBearing(118.99, 51.5074, -0.1278, "Лондон")
        assertBearing(295.15, -6.2088, 106.8456, "Джакарта")
        assertBearing(176.36, 55.7558, 37.6173, "Москва")
        assertBearing(194.45, 43.3178, 45.6986, "Грозный")
    }

    @Test
    fun `В километре к северу от Каабы - строго на юг`() {
        assertBearing(180.0, 21.4315, Kaaba.LONGITUDE, "Мекка")
    }

    @Test
    fun `Расстояние до Каабы`() {
        assertEquals(2495.0, distanceToKaabaKm(43.3178, 45.6986), 5.0, "Грозный")
        assertEquals(0.0, distanceToKaabaKm(Kaaba.LATITUDE, Kaaba.LONGITUDE), 1e-6)
    }

    @Test
    fun `Нормализация угла в 0-360`() {
        assertEquals(0.0, normalizeDegrees(360.0))
        assertEquals(359.0, normalizeDegrees(-1.0))
        assertEquals(10.0, normalizeDegrees(730.0))
        assertEquals(180.0, normalizeDegrees(-180.0))
    }

    @Test
    fun `Кратчайший поворот через 0 и 360`() {
        assertEquals(2.0, signedDelta(359.0, 1.0), 1e-9, "через север по часовой")
        assertEquals(-2.0, signedDelta(1.0, 359.0), 1e-9, "через север против часовой")
        assertEquals(-90.0, signedDelta(90.0, 0.0), 1e-9)
        assertEquals(180.0, signedDelta(0.0, 180.0), 1e-9, "противоположное - +180")
        assertEquals(-170.0, signedDelta(10.0, 200.0), 1e-9)
    }

    private fun assertBearing(expected: Double, latitude: Double, longitude: Double, city: String) =
        assertEquals(expected, qiblaBearing(latitude, longitude), 0.05, city)
}
