package app.inabah.android.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.inabah.android.core.reminders.ReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Время ожидания настроек и контента — меньше 10 с, которые система даёт `goAsync`. */
private const val TIMEOUT_MILLIS = 8_000L

/**
 * Работа приёмника после `onReceive`: ждёт готовности [AppContainer] (процесс мог быть запущен ради
 * будильника) и выполняет [block] на главном потоке. Время жизни — до `finish()`: область — только на
 * это срабатывание, по таймауту отменяется.
 */
private fun BroadcastReceiver.runWithServices(context: Context, block: suspend (AppServices) -> Unit) {
    val pending = goAsync()
    val container = (context.applicationContext as InabahApplication).container
    CoroutineScope(Dispatchers.Main.immediate).launch {
        try {
            val done = withTimeoutOrNull(TIMEOUT_MILLIS) {
                block(container.services.filterNotNull().first())
            }
            if (done == null) Log.w("Inabah", "Напоминание: не успели за ${TIMEOUT_MILLIS} мс")
        } finally {
            pending.finish()
        }
    }
}

/** Будильник напоминания (`ReminderAlarms`): только свой, не экспортируется. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val kind = ReminderKind.fromName(intent.getStringExtra(EXTRA_KIND)) ?: return
        runWithServices(context) { it.reminders.fire(kind) }
    }

    companion object {
        const val ACTION_FIRE = "app.inabah.android.action.REMINDER"
        const val EXTRA_KIND = "kind"
    }
}

/**
 * Перепланировать после перезагрузки, смены времени или пояса и обновления приложения: будильники
 * после перезагрузки стираются, а время окна считается по местному времени. Действия — защищённые
 * системные (их может послать только система), остальные игнорируются.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SYSTEM_ACTIONS) return
        runWithServices(context) { it.reminders.reschedule() }
    }

    private companion object {
        val SYSTEM_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
