package app.inabah.android.feature.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Уведомления разрешены: включены в системе, (Android 13+) выдано `POST_NOTIFICATIONS` и — если задан [channel] —
 * пользователь не выключил этот канал (аудит 2026-10-10, К1: иначе уведомление молча не показывается).
 * Канала ещё нет (процесс только стартует) — считается включённым: каналы создаёт `ReminderNotifier.ensureChannels` при старте.
 */
fun Context.notificationsAllowed(channel: String? = null): Boolean {
    val manager = NotificationManagerCompat.from(this)
    return manager.areNotificationsEnabled() && hasNotificationPermission() &&
        (channel == null || manager.getNotificationChannel(channel)?.importance != NotificationManager.IMPORTANCE_NONE)
}

private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Разрешение на уведомления для напоминаний: спрашивается только когда пользователь включает
 * напоминание (решение 2026-10-10 — не при запуске). [allowed] перепроверяется при возврате в
 * приложение: пользователь мог включить уведомления в настройках системы.
 */
class NotificationPermissionState internal constructor(
    val allowed: Boolean,
    /** Спросить (если ещё можно) и сообщить итог: `true` — уведомления будут показываться. */
    val request: (onResult: (Boolean) -> Unit) -> Unit,
    /** Настройки уведомлений приложения в системе — когда в разрешении отказано. */
    val openSettings: () -> Unit,
)

@Composable
fun rememberNotificationPermission(channel: String? = null): NotificationPermissionState {
    val context = LocalContext.current
    var allowed by remember(channel) { mutableStateOf(context.notificationsAllowed(channel)) }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    LifecycleResumeEffect(context) {
        allowed = context.notificationsAllowed(channel)
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = context.notificationsAllowed(channel)
        pending?.invoke(allowed)
        pending = null
    }
    return NotificationPermissionState(
        allowed = allowed,
        request = { onResult ->
            when {
                context.notificationsAllowed(channel) -> onResult(true)
                // Разрешение выдано, но уведомления (или канал) выключены в системе — спросить нечего: только настройки.
                context.hasNotificationPermission() -> onResult(false)
                else -> {
                    pending = onResult
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
        openSettings = { context.openNotificationSettings() },
    )
}

/**
 * Настройки уведомлений приложения (там и каналы); прошивка без такого экрана — сведения о приложении
 * (аудит 2026-10-10, К2: без запасного пути нажатие роняло приложение).
 */
private fun Context.openNotificationSettings() {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    for (intent in listOf(notifications, details)) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (error: ActivityNotFoundException) {
            Log.w("Inabah", "Нет экрана настроек: ${intent.action}", error)
        }
    }
}
