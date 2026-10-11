package app.inabah.android.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.reminders.ReminderChannels
import app.inabah.android.core.reminders.ReminderKind
import app.inabah.android.core.reminders.ReminderText
import app.inabah.android.core.settings.STREAK_DISPLAY_MAX
import app.inabah.android.feature.settings.notificationsAllowed

/**
 * Уведомления напоминаний: каналы «Азкары» и «Хадис дня» (видны в настройках системы), значок —
 * силуэт иконки, нажатие — явный интент на [MainActivity] (не на алиасы иконок: правило проекта).
 * Нет разрешения или канал выключен — ничего (пользователь выключил уведомления в системе). Вызывать с главного потока.
 */
class ReminderNotifier(context: Context) {
    // Строки — на языке интерфейса (как у активности), не системы.
    private val context = context.applicationContext
        .createConfigurationContext(uiLocaleOverride(context.applicationContext.resources.configuration))
    private val manager = NotificationManagerCompat.from(this.context)

    private var channelsReady = false

    /** Уведомления приложения разрешены (каналы проверяет [show]). */
    val canNotify: Boolean get() = context.notificationsAllowed()

    /** Строки ресурсов на языке интерфейса — для подстановок (название сборника). */
    fun string(resId: Int): String = context.getString(resId)

    /** «12 дней подряд» для `{streak}`; больше [STREAK_DISPLAY_MAX] — «9999+ дней подряд», как в капсуле. */
    fun streakText(days: Int): String =
        if (days > STREAK_DISPLAY_MAX) context.getString(R.string.azkar_streak_days_max)
        else context.resources.getQuantityString(R.plurals.azkar_streak_days, days, days)

    /** Показано ли (`false` — уведомления или канал выключены). */
    fun showAzkar(kind: ReminderKind, section: AzkarSection, text: ReminderText): Boolean {
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_AZKAR)
            .putExtra(MainActivity.EXTRA_SECTION, section.key)
        return show(kind, text, open)
    }

    /** Показано ли — хадис дня запоминается только показанным (аудит 2026-10-10, К1). */
    fun showHadith(id: HadithId, text: ReminderText): Boolean {
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_HADITH)
            .putExtra(MainActivity.EXTRA_COLLECTION, id.collection.key)
            .putExtra(MainActivity.EXTRA_NUMBER, id.number)
        return show(ReminderKind.HadithOfDay, text, open)
    }

    private fun show(kind: ReminderKind, text: ReminderText, open: Intent): Boolean {
        ensureChannels()
        if (!context.notificationsAllowed(kind.channel)) return false
        val contentIntent = PendingIntent.getActivity(
            context,
            kind.requestCode,
            open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, kind.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.body)
            // Длинный текст (хадис, «наущения и одержимость») раскрывается целиком.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // Разрешение проверено выше — notificationsAllowed (lint не видит проверки через функцию).
        @Suppress("MissingPermission")
        manager.notify(kind.requestCode, notification)
        return true
    }

    /**
     * Каналы — один раз за процесс, при старте ([ReminderService.run]): до первого напоминания они уже видны в
     * настройках системы (аудит 2026-10-10, К3), и выключенный канал можно проверить. Важность «высокая» —
     * всплывающий баннер (решение пользователя 2026-10-10); у созданного канала её меняет только пользователь,
     * повторное создание обновляет лишь название (язык интерфейса). Каналы пробных сборок — удаляются.
     */
    fun ensureChannels() {
        if (channelsReady) return
        val system = context.getSystemService(NotificationManager::class.java)
        ReminderChannels.LEGACY.forEach(system::deleteNotificationChannel)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(ReminderChannels.AZKAR, context.getString(R.string.reminders_channel_azkar), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(ReminderChannels.HADITH, context.getString(R.string.reminders_channel_hadith), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
        channelsReady = true
    }
}
