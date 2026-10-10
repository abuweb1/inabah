package app.inabah.android.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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

/** Уведомления разрешены: включены в системе и (Android 13+) выдано `POST_NOTIFICATIONS`. */
fun Context.notificationsAllowed(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled() && (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )

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
fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.notificationsAllowed()) }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    LifecycleResumeEffect(context) {
        allowed = context.notificationsAllowed()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = context.notificationsAllowed()
        pending?.invoke(allowed)
        pending = null
    }
    return NotificationPermissionState(
        allowed = allowed,
        request = { onResult ->
            when {
                context.notificationsAllowed() -> onResult(true)
                // Разрешение выдано, но уведомления выключены в системе — спросить нечего: только настройки.
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED -> onResult(false)
                else -> {
                    pending = onResult
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
        openSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}
