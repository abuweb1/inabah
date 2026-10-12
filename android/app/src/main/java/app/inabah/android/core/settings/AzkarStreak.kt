package app.inabah.android.core.settings

import java.time.Instant
import java.time.LocalDate

/** Самое большое число серии на экране и в уведомлении; дальше — «9999+» (решение пользователя 2026-10-11). */
const val STREAK_DISPLAY_MAX = 9999

/** Отметка дня в календаре: оба раздела полностью — день, один — половина. */
enum class AzkarDayStatus { Full, MorningOnly, EveningOnly }

/** Отметка дня; ни один раздел не прочитан полностью — `null` (начатый раздел половиной не считается). */
val AzkarDay.status: AzkarDayStatus?
    get() {
        val morning = morning?.isComplete == true
        val evening = evening?.isComplete == true
        return when {
            morning && evening -> AzkarDayStatus.Full
            morning -> AzkarDayStatus.MorningOnly
            evening -> AzkarDayStatus.EveningOnly
            else -> null
        }
    }

/**
 * Серия дней азкаров (решения пользователя 2026-10-11, `docs/analysis/2026-10-11-azkar-streak-calendar.md`):
 * день — утренние и вечерние полностью, без «заморозки».
 *
 * @property current дней подряд до сегодня включительно; сегодняшний неполный день серию не рвёт.
 * @property best самая длинная серия за всю историю.
 * @property fullDays всего полных дней.
 */
data class AzkarStreak(val current: Int, val best: Int, val fullDays: Int) {
    companion object {
        val Empty = AzkarStreak(0, 0, 0)
    }
}

/**
 * Серия по истории [days] на день [today] ([azkarStreakDay]). Дни позже [today] (перелёт на запад)
 * учитываются в лучшей серии и в итоге, но не в текущей.
 */
fun azkarStreak(days: Map<LocalDate, AzkarDay>, today: LocalDate): AzkarStreak {
    val full = days.filterValues { it.status == AzkarDayStatus.Full }.keys
    if (full.isEmpty()) return AzkarStreak.Empty
    var current = 0
    var day = if (today in full) today else today.minusDays(1)
    while (day in full) {
        current++
        day = day.minusDays(1)
    }
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (date in full.sorted()) {
        run = if (previous != null && date == previous.plusDays(1)) run + 1 else 1
        best = maxOf(best, run)
        previous = date
    }
    return AzkarStreak(current, best, full.size)
}

/**
 * Какой день азкаров «сегодня»: дата начала окна вечерних, если оно идёт, иначе следующего (`AzkarPeriod.day`).
 * День заканчивается вместе с его вечерним окном: в 01:00 при вечерних 17:00–02:00 ещё вчерашний день,
 * в 03:00 — уже новый, и вчерашний без вечерних становится пропуском.
 */
fun azkarStreakDay(evening: AzkarWindowSchedule, now: Instant): LocalDate = evening.period(now).day
