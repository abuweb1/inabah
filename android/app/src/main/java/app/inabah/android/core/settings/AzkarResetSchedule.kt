package app.inabah.android.core.settings

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Время суток с точностью до минуты: часы 0…23, минуты 0…59 (вне диапазона — к границе). */
@JvmInline
value class DayTime private constructor(val minutesSinceMidnight: Int) {
    val hour: Int get() = minutesSinceMidnight / MINUTES_PER_HOUR
    val minute: Int get() = minutesSinceMidnight % MINUTES_PER_HOUR
    val localTime: LocalTime get() = LocalTime.of(hour, minute)

    override fun toString(): String = "%02d:%02d".format(hour, minute)

    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val LAST_MINUTE_OF_DAY = 24 * MINUTES_PER_HOUR - 1

        fun of(hour: Int, minute: Int): DayTime =
            DayTime(hour.coerceIn(0, 23) * MINUTES_PER_HOUR + minute.coerceIn(0, MINUTES_PER_HOUR - 1))

        fun fromMinutes(minutesSinceMidnight: Int): DayTime =
            DayTime(minutesSinceMidnight.coerceIn(0, LAST_MINUTE_OF_DAY))
    }
}

/**
 * Ежедневное обнуление в [time] по часовому поясу [zone] (iOS `AzkarResetSchedule`).
 * Считается календарём, а не прибавлением суток: переходы на летнее/зимнее время учитываются.
 *
 * - Несуществующее время (весенний перевод: 02:00–02:59 нет) — начало первого существующего часа
 *   после перехода (03:00), как `.nextTime` в iOS.
 * - Повторяющееся время (осенний перевод) — первое наступление.
 */
class AzkarResetSchedule(val time: DayTime, val zone: ZoneId) {
    /** Ближайшее наступление [time] строго после [instant]. */
    fun nextReset(after: Instant): Instant {
        val date = after.atZone(zone).toLocalDate()
        val today = occurrence(date)
        return if (today.isAfter(after)) today else occurrence(date.plusDays(1))
    }

    /** Последнее наступление [time] не позже [instant]: ровно в момент обнуления начинается новый период. */
    fun periodStart(at: Instant): Instant {
        val date = at.atZone(zone).toLocalDate()
        val today = occurrence(date)
        return if (today.isAfter(at)) occurrence(date.minusDays(1)) else today
    }

    /** Ближайшее к [instant] наступление [time]; при равенстве расстояний — позднее. */
    fun boundary(nearest: Instant): Instant {
        val previous = periodStart(nearest)
        val next = nextReset(nearest)
        val toPrevious = Duration.between(previous, nearest)
        val toNext = Duration.between(nearest, next)
        return if (toPrevious < toNext) previous else next
    }

    private fun occurrence(date: LocalDate): Instant {
        val local = LocalDateTime.of(date, time.localTime)
        val rules = zone.rules
        return if (rules.getValidOffsets(local).isEmpty()) {
            // Время попало в разрыв перевода часов — первый существующий момент после него.
            rules.getTransition(local).instant
        } else {
            // Для повтора берётся раннее смещение — первое наступление.
            ZonedDateTime.ofLocal(local, zone, null).toInstant()
        }
    }
}

/**
 * Период чтения раздела (iOS `AzkarPeriod`): хранится момент окончания, а не начала —
 * проверка «наступило ли обнуление» не зависит от часового пояса и перевода часов.
 *
 * @property scheduledReset граница, назначенная при начале периода (от неё пересчитывается смена времени).
 * @property validUntil фактическая граница: период истёк, когда `now >= validUntil`.
 * @property timeZone пояс, в котором назначена граница.
 */
data class AzkarPeriod(
    val scheduledReset: Instant,
    val validUntil: Instant,
    val timeZone: ZoneId,
) {
    fun isExpired(now: Instant): Boolean = !now.isBefore(validUntil)

    /**
     * Смена времени обнуления в настройках. Пересчёт всегда от [scheduledReset]: прокрутка колеса
     * времени через несколько значений даёт тот же результат, что и сразу конечное. Новое время
     * сегодня уже прошло — обнуление в прежнее время ([scheduledReset]), ближайшее не пропускается.
     */
    fun rescheduled(schedule: AzkarResetSchedule, now: Instant): AzkarPeriod {
        val candidate = schedule.boundary(nearest = scheduledReset)
        return when {
            candidate.isAfter(now) -> copy(validUntil = candidate)
            scheduledReset.isAfter(now) -> copy(validUntil = scheduledReset)
            else -> this
        }
    }

    /** Смена часового пояса устройства: граница переносится на то же время в новом поясе, если оно впереди. */
    fun relocated(schedule: AzkarResetSchedule, now: Instant): AzkarPeriod {
        if (schedule.zone == timeZone) return this
        val candidate = schedule.boundary(nearest = validUntil)
        val boundary = if (candidate.isAfter(now)) candidate else validUntil
        return AzkarPeriod(scheduledReset = boundary, validUntil = boundary, timeZone = schedule.zone)
    }

    companion object {
        /** Новый период с [now] до ближайшего обнуления. */
        fun startingAt(now: Instant, schedule: AzkarResetSchedule): AzkarPeriod {
            val reset = schedule.nextReset(after = now)
            return AzkarPeriod(scheduledReset = reset, validUntil = reset, timeZone = schedule.zone)
        }
    }
}
