package app.inabah.android.app

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.inabah.android.core.qibla.LocationFix
import app.inabah.android.core.qibla.LocationSource
import app.inabah.android.core.qibla.LocationStatus
import app.inabah.android.core.qibla.MagneticDeclination
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Координаты через `LocationManager` - без Google Play Services и сети (решение 2026-10-24). Вызывается
 * только при выданном доступе; координаты не сохраняются и не пишутся в журнал.
 */
class AndroidLocationSource(context: Context, private val timeoutMillis: Long) : LocationSource {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // экран запускает поиск только с доступом (QiblaModel.state)
    override fun locate(): Flow<LocationStatus> = flow {
        if (manager == null || !manager.isLocationEnabled) {
            emit(LocationStatus.Off)
            return@flow
        }
        val last = runCatchingSecurity { lastKnown() }
        emit(last?.let { LocationStatus.Found(it.toFix()) } ?: LocationStatus.Searching)
        val fresh = withTimeoutOrNull(timeoutMillis) { runCatchingSecurity { current() } }
        when {
            fresh != null -> emit(LocationStatus.Found(fresh.toFix()))
            last == null -> emit(LocationStatus.Failed)
        }
    }

    /** Самые свежие из последних известных по всем включённым источникам. */
    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? = manager.getProviders(true)
        .mapNotNull { manager.getLastKnownLocation(it) }
        .maxByOrNull { it.elapsedRealtimeNanos }

    /** Одни свежие координаты: объединённый источник, иначе GPS или сеть - что включено. */
    @SuppressLint("MissingPermission")
    private suspend fun current(): Location? {
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { it in manager.getProviders(true) } ?: return null
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(appContext)) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }
    }

    /** Доступ отозвали, пока экран открыт: координат нет, экран сам перепроверит доступ при возврате. */
    private inline fun <T> runCatchingSecurity(block: () -> T?): T? =
        try {
            block()
        } catch (_: SecurityException) {
            null
        }
}

/** Время координат - по тем же монотонным часам, что и показания датчика. */
private fun Location.toFix() = LocationFix(
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = if (hasAltitude()) altitude else 0.0,
    timeMillis = elapsedRealtimeNanos / NANOS_IN_MILLI,
)

private const val NANOS_IN_MILLI = 1_000_000L

/** Склонение по мировой магнитной модели, встроенной в Android (без сети). */
val GeomagneticDeclination = MagneticDeclination { fix ->
    GeomagneticField(
        fix.latitude.toFloat(),
        fix.longitude.toFloat(),
        fix.altitudeMeters.toFloat(),
        System.currentTimeMillis(),
    ).declination.toDouble()
}

/** Монотонные часы экрана «Къибла»: время координат и показаний датчика. */
fun qiblaClock(): Long = SystemClock.elapsedRealtime()
