package app.inabah.android.core.settings

import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** «Добавьте ещё азкаров»: с 14-го полного дня серии, повтор через 7, после обрыва — снова с 14 (2026-10-25). */
class AzkarMotivationTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val full = AzkarDay(AzkarDayRecord(16, 16), AzkarDayRecord(16, 16))
    private val morningOnly = AzkarDay(morning = AzkarDayRecord(16, 16), evening = AzkarDayRecord(3, 16))

    /** Полные дни подряд с [from], [count] штук. */
    private fun series(from: LocalDate, count: Int) = (0 until count).associate { from.plusDays(it.toLong()) to full }

    /** День [n] серии от [start] (1 — сам [start]). */
    private fun day(n: Int, from: LocalDate = start): LocalDate = from.plusDays(n - 1L)

    @Test
    fun `Предлагается с 14-го полного дня, не раньше`() {
        assertNull(motivationDue(series(start, 13), day(13), last = null, canAddMore = true)?.streak)
        assertEquals(14, motivationDue(series(start, 14), day(14), last = null, canAddMore = true)?.streak)
    }

    @Test
    fun `Выбраны все азкары — не предлагается`() {
        assertNull(motivationDue(series(start, 30), day(30), last = null, canAddMore = false))
    }

    @Test
    fun `Сегодня не дочитан полностью — не предлагается, даже если серия длинная`() {
        val days = series(start, 20) + (day(21) to morningOnly)
        assertNull(motivationDue(days, day(21), last = null, canAddMore = true)?.streak)
    }

    @Test
    fun `После закрытия — снова через 7 дней серии`() {
        val last = MotivationOffer(start, 14)
        for (n in 14..20) assertNull(motivationDue(series(start, n), day(n), last, canAddMore = true), "день $n")
        assertEquals(21, motivationDue(series(start, 21), day(21), last, canAddMore = true)?.streak)
        assertNull(motivationDue(series(start, 22), day(22), MotivationOffer(start, 21), canAddMore = true)?.streak)
        assertEquals(28, motivationDue(series(start, 28), day(28), MotivationOffer(start, 21), canAddMore = true)?.streak)
    }

    @Test
    fun `Серия прервалась — новая серия снова с 14 дней`() {
        val last = MotivationOffer(start, 14)
        // 16 дней, пропуск, новая серия.
        val restart = day(18)
        val days = series(start, 16) + series(restart, 14)
        assertNull(motivationDue(days - day(14, restart), day(13, restart), last, canAddMore = true)?.streak)
        assertEquals(14, motivationDue(days, day(14, restart), last, canAddMore = true)?.streak)
    }

    @Test
    fun `Пропущенный 14-й день — на ближайшем полном, следующий через 7 от него`() {
        assertEquals(16, motivationDue(series(start, 16), day(16), last = null, canAddMore = true)?.streak)
        assertNull(motivationDue(series(start, 22), day(22), MotivationOffer(start, 16), canAddMore = true)?.streak)
        assertEquals(23, motivationDue(series(start, 23), day(23), MotivationOffer(start, 16), canAddMore = true)?.streak)
    }

    @Test
    fun `Закрытие запоминается и переживает перезапуск, испорченное — как не предлагали`() = TestStorage.run { storage ->
        val motivation = AzkarMotivation(storage.storage)
        assertNull(motivation.last)
        motivation.offered(MotivationOffer(start, 14))
        assertEquals(day(14), MotivationOffer(start, 14).day)
        assertEquals(MotivationOffer(start, 14), AzkarMotivation(storage.restart()).last)
        assertNull(AzkarMotivation(storage.storage).due(series(start, 20), day(20), canAddMore = true)?.streak)

        storage.storage.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("azkar.motivation.seriesStart")] = "не дата" }
        assertNull(AzkarMotivation(storage.storage).last)
    }
}
