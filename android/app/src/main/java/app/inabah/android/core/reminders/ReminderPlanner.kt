package app.inabah.android.core.reminders

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.settings.AzkarDayRecord
import app.inabah.android.core.settings.AzkarWindow
import app.inabah.android.core.settings.AzkarWindowSchedule
import app.inabah.android.core.settings.ReminderPreferences
import app.inabah.android.core.settings.nextOccurrence
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Вид напоминания; [requestCode] — постоянный номер будильника (у каждого вида свой, одновременно один). */
enum class ReminderKind(val requestCode: Int, val section: AzkarSection?) {
    MorningStart(1, AzkarSection.Morning),
    EveningStart(2, AzkarSection.Evening),
    MorningUnfinished(3, AzkarSection.Morning),
    EveningUnfinished(4, AzkarSection.Evening),
    HadithOfDay(5, null),
    ;

    val isUnfinished: Boolean get() = this == MorningUnfinished || this == EveningUnfinished

    /** Канал уведомлений вида — пользователь может выключить его в системе отдельно от остальных. */
    val channel: String get() = if (section == null) ReminderChannels.HADITH else ReminderChannels.AZKAR

    companion object {
        fun start(section: AzkarSection): ReminderKind =
            if (section == AzkarSection.Morning) MorningStart else EveningStart

        fun unfinished(section: AzkarSection): ReminderKind =
            if (section == AzkarSection.Morning) MorningUnfinished else EveningUnfinished

        fun fromName(name: String?): ReminderKind? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Каналы уведомлений напоминаний (видны в настройках системы). Важность «высокая» задаётся только при создании
 * канала, дальше она — пользователя: поэтому id с `.alert` (решение 2026-10-10), прежние — в [LEGACY].
 */
object ReminderChannels {
    const val AZKAR = "reminders.azkar.alert"
    const val HADITH = "reminders.hadith.alert"

    /** Каналы пробных сборок 1.1.1 с важностью «обычная» — удаляются. */
    val LEGACY = listOf("reminders.azkar", "reminders.hadith")
}

/** Запланированное напоминание: [at] — когда, [windowDay] — дата начала окна азкаров (у азкаров). */
data class PlannedReminder(
    val kind: ReminderKind,
    val at: Instant,
    val windowDay: LocalDate? = null,
)

/**
 * За сколько до конца окна — «ещё не прочитаны». Будильники неточные (решение пользователя 2026-10-10:
 * без разрешения на точные), система может доставить их до часа позже — с запасом в 2 часа на чтение
 * остаётся не меньше часа.
 */
val UNFINISHED_LEAD: Duration = Duration.ofHours(2)

/**
 * Ближайшее наступление каждого включённого напоминания строго после [now] (`docs/07-screen-settings.md`,
 * «Напоминания»). Окна — из настроек азкаров; переходы через полночь и перевод часов — правилами
 * [AzkarWindowSchedule]. После срабатывания расписание пересчитывается заново.
 */
fun planReminders(
    preferences: ReminderPreferences,
    windows: Map<AzkarSection, AzkarWindow>,
    now: Instant,
    zone: ZoneId,
): List<PlannedReminder> = buildList {
    for (section in AzkarSection.entries) {
        if (section !in preferences.azkarStart) continue
        val window = windows[section]?.takeIf { it.isValid } ?: continue
        val schedule = AzkarWindowSchedule(window, zone)
        val start = schedule.nextStart(after = now)
        add(PlannedReminder(ReminderKind.start(section), start, start.atZone(zone).toLocalDate()))
        if (preferences.azkarUnfinished) add(unfinished(section, schedule, now, zone))
    }
    if (preferences.hadithOfDay) {
        add(PlannedReminder(ReminderKind.HadithOfDay, preferences.hadithTime.nextOccurrence(after = now, zone)))
    }
}

/** За [UNFINISHED_LEAD] до конца текущего (если ещё не прошло) или следующего окна; окно короче — в его середине. */
private fun unfinished(section: AzkarSection, schedule: AzkarWindowSchedule, now: Instant, zone: ZoneId): PlannedReminder {
    fun remindFor(start: Instant): PlannedReminder {
        val end = schedule.windowEnd(start)
        val beforeEnd = end.minus(UNFINISHED_LEAD)
        val at = if (beforeEnd.isAfter(start)) beforeEnd else start.plus(Duration.between(start, end).dividedBy(2))
        return PlannedReminder(ReminderKind.unfinished(section), at, start.atZone(zone).toLocalDate())
    }
    val current = remindFor(schedule.currentOrNextStart(now))
    return if (current.at.isAfter(now)) current else remindFor(schedule.nextStart(after = now))
}

/**
 * Показать ли сработавшее напоминание азкаров [kind] (`docs/07-screen-settings.md`, «Напоминания»): только пока окно
 * открыто — будильник мог опоздать (телефон спал, время перевели); «ещё не прочитаны» — только если раздел в этом
 * окне не выполнен. [record] — запись истории за день окна: дату его начала (после полуночи — вчерашнюю), как
 * записывает `AzkarStore`. Пустой раздел (`total == 0`) выполненным не считается.
 */
fun isAzkarReminderDue(
    kind: ReminderKind,
    window: AzkarWindow,
    now: Instant,
    zone: ZoneId,
    record: (LocalDate) -> AzkarDayRecord?,
): Boolean {
    val period = AzkarWindowSchedule(window, zone).period(now)
    if (!period.isWindow) return false
    if (!kind.isUnfinished) return true
    return record(period.day)?.isComplete != true
}

/**
 * Хадис дня: первый непрочитанный по порядку сборников с главной ([order]), внутри — по номеру.
 * Всё прочитано — следующий после последнего показанного ([last]), по кругу; нет хадисов — `null`.
 */
fun pickHadithOfDay(
    order: List<HadithCollection>,
    hadiths: Map<HadithCollection, List<Hadith>>,
    isRead: (HadithId) -> Boolean,
    last: HadithId?,
): Hadith? {
    val all = order.flatMap { collection -> hadiths[collection].orEmpty().sortedBy { it.number } }
    if (all.isEmpty()) return null
    all.firstOrNull { !isRead(it.id) }?.let { return it }
    val lastIndex = all.indexOfFirst { it.id == last }
    return all[(lastIndex + 1) % all.size]
}
