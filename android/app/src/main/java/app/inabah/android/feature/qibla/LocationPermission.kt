package app.inabah.android.feature.qibla

import app.inabah.android.core.settings.OnceFlag
import app.inabah.android.core.qibla.classifyDenial
import app.inabah.android.core.qibla.DenialOutcome
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
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
    /** Идёт запрос: окно Android на экране (подсказки ждут). */
    val requesting: Boolean,
    /** Системное окно Android с точным и приблизительным местоположением. */
    val request: () -> Unit,
    /** Сведения о приложении - когда спросить уже нельзя. */
    val openAppSettings: () -> Unit,
    /** Настройки геолокации телефона - когда она выключена. */
    val openLocationSettings: () -> Unit,
)

/**
 * [asked] - «уже спрашивали, и пользователь ответил» (хранится, аудит 2026-10-24): не выдано + спрашивали + Android
 * не предлагает пояснения = «больше не спрашивать» - сразу «Открыть настройки», без мёртвых нажатий «Разрешить».
 */
@Composable
fun rememberLocationPermission(asked: OnceFlag?): LocationPermissionState {
    val context = LocalContext.current
    var requesting by remember { mutableStateOf(false) }
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var access by remember { mutableStateOf(context.locationAccess(asked?.isSet == true)) }
    LifecycleResumeEffect(context) {
        access = context.locationAccess(asked?.isSet == true)
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requesting = false
        if (!context.locationGranted()) {
            val outcome = classifyDenial(rationaleBefore, context.locationRationale(), SystemClock.elapsedRealtime() - startedAt)
            if (outcome != DenialOutcome.Dismissed) asked?.set()
        }
        access = context.locationAccess(asked?.isSet == true)
    }
    return LocationPermissionState(
        access = access,
        requesting = requesting,
        request = {
            rationaleBefore = context.locationRationale()
            startedAt = SystemClock.elapsedRealtime()
            requesting = true
            launcher.launch(LOCATION_PERMISSIONS)
        },
        openAppSettings = {
            context.openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
        openLocationSettings = { context.openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
    )
}

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.locationGranted() = LOCATION_PERMISSIONS.any { granted(it) }

private fun Context.locationRationale(): Boolean {
    val activity = findActivity() ?: return false
    return LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

private fun Context.locationAccess(asked: Boolean): LocationAccess = when {
    granted(Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.Precise
    granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.Approximate
    asked && !locationRationale() -> LocationAccess.DeniedForever
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
        } catch (error: SecurityException) {
            // Редкие прошивки закрывают экран настроек для чужих приложений - не падаем (аудит 2026-10-24).
            Log.w("Inabah", "Экран настроек закрыт: ${candidate.action}", error)
        }
    }
}
