package app.inabah.android.core.settings

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

/** Время азкаров раздела: «с [start] до [end]». Конец раньше начала — на следующий день (вечерние 17:00–02:00). */
data class AzkarWindow(val start: DayTime, val end: DayTime) {
    /** Начало = концу — у окна нет длины: такое время не сохраняется (iOS `isValid`). */
    val isValid: Boolean get() = start != end
}

/**
 * Отрезок, к которому относится прогресс раздела (iOS `AzkarPeriod`): само время азкаров или
 * промежуток до него. На каждой границе счётчики обнуляются.
 *
 * Отрезок узнаётся по виду и дате, а не по мгновениям: после перелёта в другой часовой пояс
 * то же окно — то же самое, и прочитанное не теряется.
 *
 * @property day дата начала окна: своего — у окна, следующего — у промежутка. Вечерние 17:00–02:00,
 *   прочитанные в 00:30, относятся к дате 17:00.
 * @property validUntil граница: конец окна или начало следующего.
 */
data class AzkarPeriod(val kind: Kind, val day: LocalDate, val validUntil: Instant) {
    enum class Kind(val key: String) {
        /** Время азкаров: прогресс идёт в отметку на главной и в историю. */
        Window("window"),

        /** Промежуток до следующего окна: счёт работает, но никуда не идёт. */
        Gap("gap"),
        ;

        companion object {
            fun fromKey(key: String): Kind? = entries.firstOrNull { it.key == key }
        }
    }

    val isWindow: Boolean get() = kind == Kind.Window

    /** Тот же отрезок — счёт сохраняется, даже если граница сдвинулась (новое время в настройках, другой пояс). */
    fun isSameSpan(other: AzkarPeriod): Boolean = kind == other.kind && day == other.day
}

/**
 * Расписание времени азкаров раздела (iOS `AzkarWindowSchedule`): в каком отрезке момент и когда граница.
 * Считается календарём, а не прибавлением суток: переходы на летнее/зимнее время учитываются.
 *
 * - Несуществующее время (весенний перевод: 02:00–02:59 нет) — начало первого существующего часа
 *   после перехода (03:00), как `.nextTime` в iOS.
 * - Повторяющееся время (осенний перевод) — первое наступление.
 */
class AzkarWindowSchedule(val window: AzkarWindow, val zone: ZoneId) {
    /** Отрезок, в который попадает [now]. Ровно в момент начала окна — уже окно, ровно в момент конца — уже промежуток. */
    fun period(now: Instant): AzkarPeriod {
        val start = lastOccurrence(window.start, notAfter = now)
        val end = nextOccurrence(window.end, after = start)
        if (now.isBefore(end)) return AzkarPeriod(AzkarPeriod.Kind.Window, localDate(start), validUntil = end)
        val nextStart = nextOccurrence(window.start, after = now)
        return AzkarPeriod(AzkarPeriod.Kind.Gap, localDate(nextStart), validUntil = nextStart)
    }

    private fun localDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    private fun lastOccurrence(time: DayTime, notAfter: Instant): Instant {
        val date = localDate(notAfter)
        val sameDay = occurrence(time, date)
        return if (sameDay.isAfter(notAfter)) occurrence(time, date.minusDays(1)) else sameDay
    }

    private fun nextOccurrence(time: DayTime, after: Instant): Instant {
        val date = localDate(after)
        val sameDay = occurrence(time, date)
        return if (sameDay.isAfter(after)) sameDay else occurrence(time, date.plusDays(1))
    }

    private fun occurrence(time: DayTime, date: LocalDate): Instant {
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
