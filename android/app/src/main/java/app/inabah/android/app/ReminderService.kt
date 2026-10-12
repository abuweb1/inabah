package app.inabah.android.app

import app.inabah.android.core.content.Loadable
import app.inabah.android.core.reminders.ReminderKind
import app.inabah.android.core.reminders.ReminderPlaceholders
import app.inabah.android.core.reminders.ReminderText
import app.inabah.android.core.reminders.ReminderTextsSource
import app.inabah.android.core.reminders.isAzkarReminderDue
import app.inabah.android.core.reminders.pickHadithOfDay
import app.inabah.android.core.reminders.planReminders
import app.inabah.android.core.reminders.streakAtRisk
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.ReminderSettings
import app.inabah.android.feature.hadith.HadithStore
import app.inabah.android.feature.hadith.title
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Напоминания (`docs/07-screen-settings.md`, «Напоминания»): единственное место, где пересчитывается
 * расписание — при смене настроек напоминаний или окон азкаров ([run]), после срабатывания, перезагрузки
 * и смены времени ([reschedule]). Вызывать с главного потока.
 */
class ReminderService(
    private val settings: ReminderSettings,
    private val windowSettings: AzkarWindowSettings,
    private val history: AzkarHistory,
    private val hadithStore: HadithStore,
    private val hadithProgress: HadithProgress,
    private val hadithOrder: HadithCollectionOrder,
    private val texts: ReminderTextsSource,
    private val alarms: ReminderAlarms,
    private val notifier: ReminderNotifier,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
) {
    /** Расписание следует за настройками напоминаний и окнами азкаров, пока живёт процесс. */
    suspend fun run() {
        notifier.ensureChannels()
        combine(settings.preferences, windowSettings.windows) { _, _ -> }.collect { reschedule() }
    }

    fun reschedule() {
        alarms.schedule(planReminders(settings.preferences.value, windowSettings.windows.value, now(), zone()))
    }

    /** Будильник [kind] сработал: показать, если условие ещё верно, и поставить следующий. */
    suspend fun fire(kind: ReminderKind) {
        try {
            if (notifier.canNotify) notify(kind)
        } finally {
            reschedule()
        }
    }

    private suspend fun notify(kind: ReminderKind) {
        val text = texts.texts()?.forKind(kind) ?: return
        val section = kind.section
        if (section == null) {
            notifyHadith(text)
            return
        }
        if (kind.isStreak) {
            val streak = streakAtRisk(section, windowSettings.windows.value, history.days.value, now(), zone()) ?: return
            notifier.showAzkar(kind, section, text.fill(mapOf(ReminderPlaceholders.STREAK to notifier.streakText(streak))))
            return
        }
        if (!isAzkarReminderDue(kind, windowSettings.window(section), now(), zone()) { history.record(section, it) }) return
        notifier.showAzkar(kind, section, text)
    }

    private suspend fun notifyHadith(text: ReminderText) {
        hadithStore.loadAll()
        val order = hadithOrder.collections.value
        // Процесс мог стартовать ради будильника: сборники, которые уже грузит AppContainer, loadAll
        // пропускает — дождаться, пока каждый загрузится или упадёт, иначе хадис взялся бы не из того.
        hadithStore.collections.first { states -> order.all { states[it] is Loadable.Loaded || states[it] is Loadable.Failed } }
        val hadith = pickHadithOfDay(
            order = order,
            hadiths = order.associateWith { hadithStore.hadiths(it) },
            isRead = { hadithProgress.status(it).isRead },
            last = settings.lastHadithOfDay,
        ) ?: return
        val values = mapOf(
            ReminderPlaceholders.COLLECTION to notifier.string(hadith.id.collection.title),
            ReminderPlaceholders.NUMBER to hadith.number.toString(),
            ReminderPlaceholders.TEXT to hadith.translation?.text.orEmpty(),
        )
        // Канал «Хадис дня» выключен — не показан и не запомнен: иначе круг уходил бы вперёд невиданным.
        if (notifier.showHadith(hadith.id, text.fill(values))) settings.rememberHadithOfDay(hadith.id)
    }
}
