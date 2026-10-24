package app.inabah.android.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import app.inabah.android.core.qibla.HeadingSource
import app.inabah.android.core.qibla.MagneticHeading
import app.inabah.android.core.qibla.normalizeDegrees
import kotlin.math.acos
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Направление телефона из `TYPE_ROTATION_VECTOR` (магнитометр, акселерометр и гироскоп вместе - меньше
 * дрожания, чем «сырой» компас). Слушатель - только пока поток собирают.
 */
class AndroidHeadingSource(context: Context) : HeadingSource {
    private val manager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnet: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    override val available: Boolean get() = sensor != null

    override fun headings(): Flow<MagneticHeading> = callbackFlow {
        val rotation = FloatArray(MATRIX_SIZE)
        val orientation = FloatArray(ORIENTATION_SIZE)
        // Магнитометр - сигнал калибровки и помех (2026-10-24, телефон пользователя: у датчика направления Xiaomi
        // оценки точности нет, values[4] = -1, статус не меняется). Колбэки обоих датчиков - на главном потоке.
        var magnetStatus = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        var interference = false
        val magnetListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                magnetStatus = event.accuracy
                val (x, y, z) = event.values
                val field = sqrt(x * x + y * y + z * z)
                // Поле Земли - примерно 25-65 мкТл; дальше - металл или магнит рядом. Гистерезис - флаг не мигает у края.
                interference = if (interference) field !in FIELD_CLEAR_MIN..FIELD_CLEAR_MAX else field !in FIELD_MIN..FIELD_MAX
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                // Оценка точности направления - пятое значение, радианы; -1 - датчик её не даёт.
                val accuracy = event.values.getOrNull(HEADING_ACCURACY_INDEX)
                    ?.takeIf { it >= 0 }
                    ?.let { Math.toDegrees(it.toDouble()) }
                trySend(
                    MagneticHeading(
                        magneticAzimuth = normalizeDegrees(Math.toDegrees(orientation[0].toDouble())),
                        accuracyDegrees = accuracy,
                        // Статус - из самого показания: onAccuracyChanged при старте может не прийти (ревью 2026-10-24).
                        // «Низкий» у датчика направления - только без оценки в градусах: часть телефонов держит его всегда.
                        lowAccuracy = event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                            (accuracy == null && event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) ||
                            magnetStatus <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
                            interference,
                        // Угол между осью экрана и вертикалью: 0 - лежит экраном вверх.
                        tiltDegrees = Math.toDegrees(acos(rotation[Z_OF_Z].coerceIn(-1f, 1f).toDouble())),
                        timeMillis = SystemClock.elapsedRealtime(),
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val registered = sensor != null && manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        if (!registered) close()
        magnet?.let { manager.registerListener(magnetListener, it, SensorManager.SENSOR_DELAY_UI) }
        awaitClose {
            manager?.unregisterListener(listener)
            manager?.unregisterListener(magnetListener)
        }
    }.conflate()

    private companion object {
        const val MATRIX_SIZE = 9
        const val ORIENTATION_SIZE = 3
        const val HEADING_ACCURACY_INDEX = 4
        const val Z_OF_Z = 8

        /** Сила поля, мкТл: вне - помеха; снова чисто - только внутри более узких границ. */
        const val FIELD_MIN = 22f
        const val FIELD_MAX = 70f
        const val FIELD_CLEAR_MIN = 25f
        const val FIELD_CLEAR_MAX = 65f
    }
}
