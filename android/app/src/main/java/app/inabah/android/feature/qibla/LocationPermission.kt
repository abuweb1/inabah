package app.inabah.android.feature.qibla

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.inabah.android.core.qibla.LocationAccess

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Доступ к местоположению для «Къиблы»: спрашивается при открытии экрана, после нашего пояснения
 * (решение 2026-10-24). [access] перепроверяется при возврате в приложение - доступ могли дать в настройках.
 */
class LocationPermissionState internal constructor(
    val access: LocationAccess,
    /** Системное окно Android с точным и приблизительным местоположением. */
    val request: () -> Unit,
    /** Сведения о приложении - когда спросить уже нельзя. */
    val openAppSettings: () -> Unit,
    /** Настройки геолокации телефона - когда она выключена. */
    val openLocationSettings: () -> Unit,
)

@Composable
fun rememberLocationPermission(): LocationPermissionState {
    val context = LocalContext.current
    // «Больше не спрашивать» Android не сообщает прямо (ревью 2026-10-24): до первого запроса пояснение тоже
    // не предлагается, а закрытое «Назад» окно - не отказ. Навсегда - когда пояснение было до запроса и пропало
    // после, или когда два запроса подряд закончились без окна.
    var forever by rememberSaveable { mutableStateOf(false) }
    var silentDenials by rememberSaveable { mutableIntStateOf(0) }
    var rationaleBefore by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(context.locationAccess(forever)) }
    LifecycleResumeEffect(context) {
        access = context.locationAccess(forever)
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!context.locationGranted()) {
            val rationaleNow = context.locationRationale()
            when {
                rationaleBefore && !rationaleNow -> forever = true
                !rationaleBefore && !rationaleNow -> if (++silentDenials >= SILENT_DENIALS_FOREVER) forever = true
            }
        }
        access = context.locationAccess(forever)
    }
    return LocationPermissionState(
        access = access,
        request = {
            rationaleBefore = context.locationRationale()
            launcher.launch(LOCATION_PERMISSIONS)
        },
        openAppSettings = {
            context.openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        openLocationSettings = { context.openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
    )
}

/** Сколько запросов подряд без окна и без пояснения считаются отказом навсегда. */
private const val SILENT_DENIALS_FOREVER = 2

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.locationGranted() = LOCATION_PERMISSIONS.any { granted(it) }

private fun Context.locationRationale(): Boolean {
    val activity = findActivity() ?: return false
    return LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

private fun Context.locationAccess(forever: Boolean): LocationAccess = when {
    granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.Precise
    granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.Approximate
    forever -> LocationAccess.DeniedForever
    else -> LocationAccess.NotGranted
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Экран настроек; прошивка без него - сведения о приложении, без падения (как в `NotificationPermission`). */
private fun Context.openSettings(intent: Intent) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    for (candidate in listOf(intent, details)) {
        try {
            startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (error: ActivityNotFoundException) {
            Log.w("Inabah", "Нет экрана настроек: ${candidate.action}", error)
        }
    }
}
