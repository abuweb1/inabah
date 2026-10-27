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
    fun `Европа`() {
        assertBearing(136.68, 52.5200, 13.4050, "Берлин")
        assertBearing(128.07, 50.1109, 8.6821, "Франкфурт")
        assertBearing(129.79, 48.1351, 11.5820, "Мюнхен")
        assertBearing(119.16, 48.8566, 2.3522, "Париж")
        assertBearing(125.28, 48.5734, 7.7521, "Страсбург")
        assertBearing(123.48, 50.8503, 4.3517, "Брюссель")
        assertBearing(136.71, 48.2082, 16.3738, "Вена")
    }

    @Test
    fun `Турция, Россия, Средняя Азия`() {
        assertBearing(151.62, 41.0082, 28.9784, "Стамбул")
        assertBearing(160.17, 39.9334, 32.8597, "Анкара")
        assertBearing(195.15, 55.7963, 49.1088, "Казань")
        assertBearing(198.97, 42.9849, 47.5047, "Махачкала")
        assertBearing(246.60, 43.2389, 76.8897, "Алматы")
        assertBearing(231.48, 51.1694, 71.4491, "Астана")
        assertBearing(240.30, 41.2995, 69.2401, "Ташкент")
    }

    @Test
    fun `Южное полушарие и дальний восток`() {
        assertBearing(277.50, -33.8688, 151.2093, "Сидней")
        assertBearing(23.35, -33.9249, 18.4241, "Кейптаун")
        assertBearing(293.00, 35.6762, 139.6503, "Токио")
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
