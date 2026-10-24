package app.inabah.android.core.qibla

import android.hardware.SensorManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Датчик сам сообщает о низкой точности (2026-10-24: у Xiaomi 15 Pro оценки в градусах нет, статус «низкий» бывает всегда). */
class SensorAccuracyTest {
    @Test
    fun `Статусы - те же числа, что у Android`() {
        assertEquals(SensorManager.SENSOR_STATUS_UNRELIABLE, SensorStatus.UNRELIABLE)
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_LOW, SensorStatus.LOW)
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, SensorStatus.MEDIUM)
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, SensorStatus.HIGH)
    }

    @Test
    fun `Ненадёжно - всегда низкая точность`() {
        assertTrue(sensorReportsLowAccuracy(SensorStatus.UNRELIABLE, hasEstimate = true, magnet = SensorStatus.HIGH))
    }

    @Test
    fun `Низкий статус - только без своей оценки в градусах`() {
        assertTrue(sensorReportsLowAccuracy(SensorStatus.LOW, hasEstimate = false, magnet = null))
        assertFalse(sensorReportsLowAccuracy(SensorStatus.LOW, hasEstimate = true, magnet = null), "телефоны, что держат «низкий» всегда")
    }

    @Test
    fun `Магнитометр не откалиброван - низкая точность, нет магнитометра - не мешает`() {
        assertTrue(sensorReportsLowAccuracy(SensorStatus.HIGH, hasEstimate = false, magnet = SensorStatus.LOW))
        assertFalse(sensorReportsLowAccuracy(SensorStatus.HIGH, hasEstimate = false, magnet = SensorStatus.MEDIUM))
        assertFalse(sensorReportsLowAccuracy(SensorStatus.HIGH, hasEstimate = false, magnet = null))
    }
}
