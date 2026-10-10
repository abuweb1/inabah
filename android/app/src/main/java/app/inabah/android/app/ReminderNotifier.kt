package app.inabah.android.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.reminders.ReminderKind
import app.inabah.android.core.reminders.ReminderText

/**
 * Уведомления напоминаний: каналы «Азкары» и «Хадис дня» (видны в настройках системы), значок —
 * силуэт иконки, нажатие — явный интент на [MainActivity] (не на алиасы иконок: правило проекта).
 * Нет разрешения — ничего (пользователь выключил уведомления в системе).
 */
class ReminderNotifier(context: Context) {
    // Строки — на языке интерфейса (как у активности), не системы.
    private val context = context.applicationContext
        .createConfigurationContext(uiLocaleOverride(context.applicationContext.resources.configuration))
    private val manager = NotificationManagerCompat.from(this.context)

    val canNotify: Boolean
        get() = manager.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    /** Строки ресурсов на языке интерфейса — для подстановок (название сборника). */
    fun string(resId: Int): String = context.getString(resId)

    fun showAzkar(kind: ReminderKind, section: AzkarSection, text: ReminderText) {
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_AZKAR)
            .putExtra(MainActivity.EXTRA_SECTION, section.key)
        show(kind, AZKAR_CHANNEL, text, open)
    }

    fun showHadith(id: HadithId, text: ReminderText) {
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_HADITH)
            .putExtra(MainActivity.EXTRA_COLLECTION, id.collection.key)
            .putExtra(MainActivity.EXTRA_NUMBER, id.number)
        show(ReminderKind.HadithOfDay, HADITH_CHANNEL, text, open)
    }

    private fun show(kind: ReminderKind, channel: String, text: ReminderText, open: Intent) {
        if (!canNotify) return
        ensureChannels()
        val contentIntent = PendingIntent.getActivity(
            context,
            kind.requestCode,
            open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            // Длинный текст (хадис, «наущения и одержимость») раскрывается целиком.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // Разрешение проверено в canNotify (lint не видит проверки через свойство).
        @Suppress("MissingPermission")
        manager.notify(kind.requestCode, notification)
    }

    // Важность «высокая» — всплывающий баннер (решение пользователя 2026-10-10). Важность задаётся только при
    // создании канала, дальше она — пользователя: поэтому новые id, а каналы пробных сборок с «обычной» удаляются.
    private fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        LEGACY_CHANNELS.forEach(system::deleteNotificationChannel)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(AZKAR_CHANNEL, context.getString(R.string.reminders_channel_azkar), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(HADITH_CHANNEL, context.getString(R.string.reminders_channel_hadith), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    private companion object {
        const val AZKAR_CHANNEL = "reminders.azkar.alert"
        const val HADITH_CHANNEL = "reminders.hadith.alert"

        /** Каналы пробных сборок 1.1.1 с важностью «обычная» (2026-10-10). */
        val LEGACY_CHANNELS = listOf("reminders.azkar", "reminders.hadith")
    }
}
