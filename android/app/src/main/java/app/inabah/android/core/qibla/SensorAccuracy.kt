package app.inabah.android.core.qibla

/** Статусы точности датчика - те же числа, что `SensorManager.SENSOR_STATUS_*` (проверяет `SensorAccuracyTest`). */
object SensorStatus {
    const val UNRELIABLE = 0
    const val LOW = 1
    const val MEDIUM = 2
    const val HIGH = 3
}

/**
 * Датчик сам сообщает о низкой точности: направление [heading] «ненадёжно»; или «низкий» без своей оценки в
 * градусах ([hasEstimate]) - часть телефонов держит «низкий» всегда, тогда верим оценке; или магнитометр
 * не откалиброван ([magnet] ≤ «низкий», `null` - магнитометра нет). Телефон пользователя (Xiaomi 15 Pro)
 * оценки не даёт - отсюда магнитометр (2026-10-24).
 */
fun sensorReportsLowAccuracy(heading: Int, hasEstimate: Boolean, magnet: Int?): Boolean =
    heading == SensorStatus.UNRELIABLE ||
        (!hasEstimate && heading == SensorStatus.LOW) ||
        (magnet != null && magnet <= SensorStatus.LOW)
