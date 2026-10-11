package app.inabah.android.feature.azkar

import app.inabah.android.core.settings.AzkarDay
import app.inabah.android.core.settings.AzkarDayRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Сетка и месяцы «Календаря азкаров». */
class AzkarCalendarTest {
    @Test
    fun `Неделя с понедельника — пустые места до 1-го и до конца недели`() {
        // 1 октября 2026 — четверг.
        val cells = monthCells(YearMonth.of(2026, 10), DayOfWeek.MONDAY)

        assertEquals(35, cells.size)
        assertEquals(List(3) { null }, cells.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), cells[3])
        assertEquals(LocalDate.of(2026, 10, 31), cells[33])
        assertNull(cells[34])
    }

    @Test
    fun `Неделя с воскресенья сдвигает начало`() {
        val cells = monthCells(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 10, 1), cells[4])
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun `Месяц с понедельника 1-го — без пустых мест в начале, февраль високосного года`() {
        val june = monthCells(YearMonth.of(2026, 6), DayOfWeek.MONDAY) // 1 июня 2026 — понедельник
        assertEquals(LocalDate.of(2026, 6, 1), june.first())

        val february = monthCells(YearMonth.of(2028, 2), DayOfWeek.MONDAY)
        assertEquals(29, february.count { it != null })
    }

    @Test
    fun `Месяцы — от первой записи до текущего, пустая история — только текущий`() {
        val today = LocalDate.of(2026, 10, 11)
        val day = AzkarDay(morning = AzkarDayRecord(1, 1))

        assertEquals(listOf(YearMonth.of(2026, 10)), calendarMonths(emptyMap(), today))
        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)),
            calendarMonths(mapOf(LocalDate.of(2026, 8, 30) to day, LocalDate.of(2026, 10, 1) to day), today),
        )
        // Запись «из будущего» (перелёт) не добавляет месяцев после текущего.
        assertEquals(listOf(YearMonth.of(2026, 10)), calendarMonths(mapOf(LocalDate.of(2026, 11, 2) to day), today))
    }
}
