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

    override val available: Boolean get() = sensor != null

    override fun headings(): Flow<MagneticHeading> = callbackFlow {
        val rotation = FloatArray(MATRIX_SIZE)
        val orientation = FloatArray(ORIENTATION_SIZE)
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
                        // «Низкий» учитывается, только когда своей оценки в градусах нет: часть телефонов держит его всегда.
                        lowAccuracy = event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                            (accuracy == null && event.accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW),
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
        awaitClose { manager?.unregisterListener(listener) }
    }.conflate()

    private companion object {
        const val MATRIX_SIZE = 9
        const val ORIENTATION_SIZE = 3
        const val HEADING_ACCURACY_INDEX = 4
        const val Z_OF_Z = 8
    }
}
