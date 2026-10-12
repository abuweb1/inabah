package app.inabah.android.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.inabah.android.core.reminders.PlannedReminder
import app.inabah.android.core.reminders.ReminderKind

/**
 * Будильники напоминаний: по одному на вид ([ReminderKind.requestCode]), без точных будильников —
 * `setAndAllowWhileIdle` (Android 12+ даёт точные только будильникам и календарям; азкарам допуск в
 * минуты не страшен). Срабатывание — [ReminderReceiver]; невключённые виды снимаются.
 */
class ReminderAlarms(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(planned: List<PlannedReminder>) {
        val byKind = planned.associateBy { it.kind }
        for (kind in ReminderKind.entries) {
            val reminder = byKind[kind]
            if (reminder == null) {
                alarmManager.cancel(pendingIntent(kind))
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at.toEpochMilli(), pendingIntent(kind))
            }
        }
    }

    private fun pendingIntent(kind: ReminderKind): PendingIntent = PendingIntent.getBroadcast(
        context,
        kind.requestCode,
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            .putExtra(ReminderReceiver.EXTRA_KIND, kind.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
