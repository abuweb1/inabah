package app.inabah.android.core.settings

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Серия дней азкаров: день — оба раздела полностью, сегодняшний неполный день серию не рвёт (2026-10-11). */
class AzkarStreakTest {
    private val today = LocalDate.of(2026, 10, 11)
    private val full = AzkarDay(AzkarDayRecord(16, 16), AzkarDayRecord(16, 16))
    private val morningOnly = AzkarDay(morning = AzkarDayRecord(16, 16), evening = AzkarDayRecord(3, 16))

    private fun daysAgo(vararg offsets: Long, day: AzkarDay = full) = offsets.associate { today.minusDays(it) to day }

    @Test
    fun `Отметка дня — по разделам, прочитанным полностью`() {
        assertEquals(AzkarDayStatus.Full, full.status)
        assertEquals(AzkarDayStatus.MorningOnly, morningOnly.status)
        assertEquals(AzkarDayStatus.EveningOnly, AzkarDay(evening = AzkarDayRecord(16, 16)).status)
        assertNull(AzkarDay(AzkarDayRecord(15, 16), AzkarDayRecord(1, 16)).status)
        // Пустой раздел (контент не загрузился) полным не считается.
        assertNull(AzkarDay(AzkarDayRecord(0, 0), AzkarDayRecord(0, 0)).status)
    }

    @Test
    fun `Пустая история — нули`() {
        assertEquals(AzkarStreak.Empty, azkarStreak(emptyMap(), today))
    }

    @Test
    fun `Сегодня полный — считается в серии`() {
        assertEquals(AzkarStreak(current = 3, best = 3, fullDays = 3), azkarStreak(daysAgo(0, 1, 2), today))
    }

    @Test
    fun `Сегодня ещё не дочитан — серия до вчера не рвётся`() {
        val days = daysAgo(1, 2) + (today to morningOnly)
        assertEquals(AzkarStreak(current = 2, best = 2, fullDays = 2), azkarStreak(days, today))
    }

    @Test
    fun `Пропуск вчера — текущая серия с нуля, лучшая и итог остаются`() {
        val days = daysAgo(2, 3, 4, 6) + (today.minusDays(1) to morningOnly)
        assertEquals(AzkarStreak(current = 0, best = 3, fullDays = 4), azkarStreak(days, today))
    }

    @Test
    fun `Полдня не продолжает серию`() {
        val days = daysAgo(0, 2) + (today.minusDays(1) to morningOnly)
        assertEquals(AzkarStreak(current = 1, best = 1, fullDays = 2), azkarStreak(days, today))
    }

    @Test
    fun `Дни после сегодня — в лучшей серии и итоге, но не в текущей`() {
        val days = mapOf(today.plusDays(1) to full, today to full)
        assertEquals(AzkarStreak(current = 1, best = 2, fullDays = 2), azkarStreak(days, today))
    }

    @Test
    fun `День заканчивается вместе с вечерним окном`() {
        val evening = AzkarWindowSchedule(AzkarWindow(DayTime.of(17, 0), DayTime.of(2, 0)), BERLIN)
        fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, BERLIN).toInstant()

        assertEquals(today, azkarStreakDay(evening, at(11, 8)))   // утро
        assertEquals(today, azkarStreakDay(evening, at(11, 18)))  // вечернее окно
        assertEquals(today, azkarStreakDay(evening, at(12, 1)))   // вечерние ещё идут
        assertEquals(today.plusDays(1), azkarStreakDay(evening, at(12, 3))) // окно закрылось — новый день
    }

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
