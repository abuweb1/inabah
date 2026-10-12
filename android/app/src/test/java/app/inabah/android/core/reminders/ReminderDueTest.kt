package app.inabah.android.core.reminders

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.settings.AzkarDay
import app.inabah.android.core.settings.AzkarDayRecord
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.berlin
import app.inabah.android.core.settings.date
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Сработавшее напоминание азкаров — показывать ли (аудит 2026-10-10, Т1). Окна по умолчанию: 05:00–12:00 и
 * 17:00–02:00, Берлин.
 */
class ReminderDueTest {
    private fun due(kind: ReminderKind, now: Instant, record: (LocalDate) -> AzkarDayRecord? = { null }) =
        isAzkarReminderDue(kind, AzkarWindowSettings.defaultWindow(kind.section!!), now, berlin, record)

    @Test
    fun `Начало окна — показывается, пока окно открыто`() {
        assertTrue(due(ReminderKind.MorningStart, date(2026, 10, 10, 5)))
        assertTrue(due(ReminderKind.EveningStart, date(2026, 10, 10, 17)))
    }

    @Test
    fun `Будильник опоздал и окно кончилось — не показывается`() {
        assertFalse(due(ReminderKind.MorningStart, date(2026, 10, 10, 12, 30)), "утро кончилось в 12:00")
        assertFalse(due(ReminderKind.MorningUnfinished, date(2026, 10, 10, 12, 30)))
        assertFalse(due(ReminderKind.EveningStart, date(2026, 10, 10, 13)), "промежуток до вечера")
    }

    @Test
    fun `«Не прочитаны» — только если раздел в этом окне не выполнен`() {
        val now = date(2026, 10, 10, 10)
        assertFalse(due(ReminderKind.MorningUnfinished, now) { AzkarDayRecord(completed = 16, total = 16) }, "выполнено")
        assertTrue(due(ReminderKind.MorningUnfinished, now) { AzkarDayRecord(completed = 5, total = 16) }, "не всё")
        assertTrue(due(ReminderKind.MorningUnfinished, now), "записи нет — ничего не прочитано")
        assertTrue(due(ReminderKind.MorningUnfinished, now) { AzkarDayRecord(completed = 0, total = 0) }, "пустой раздел — не выполнен")
    }

    @Test
    fun `Начало окна не смотрит на выполненное`() {
        assertTrue(due(ReminderKind.MorningStart, date(2026, 10, 10, 6)) { AzkarDayRecord(completed = 16, total = 16) })
    }

    @Test
    fun `Вечер после полуночи — запись за день начала окна`() {
        val asked = mutableListOf<LocalDate>()
        val shown = due(ReminderKind.EveningUnfinished, date(2026, 10, 11, 1)) { day ->
            asked += day
            AzkarDayRecord(completed = 10, total = 10).takeIf { day == LocalDate.of(2026, 10, 10) }
        }

        assertEquals(listOf(LocalDate.of(2026, 10, 10)), asked)
        assertFalse(shown, "вечерние 10.10 выполнены — напоминание 11.10 в 01:00 не нужно")
    }

    @Test
    fun `У каждого вида свой будильник и канал по разделу`() {
        assertEquals(ReminderKind.entries.size, ReminderKind.entries.map { it.requestCode }.distinct().size)
        assertEquals(ReminderChannels.HADITH, ReminderKind.HadithOfDay.channel)
        ReminderKind.entries.filter { it.section != null }.forEach { assertEquals(ReminderChannels.AZKAR, it.channel, it.name) }
        assertTrue(AzkarSection.entries.all { ReminderKind.start(it).section == it && ReminderKind.unfinished(it).section == it })
    }

    // «Серия под угрозой» (решения пользователя 2026-10-11).
    private val windows = AzkarSection.entries.associateWith { AzkarWindowSettings.defaultWindow(it) }
    private val full = AzkarDay(AzkarDayRecord(16, 16), AzkarDayRecord(16, 16))
    private val october10: LocalDate = LocalDate.of(2026, 10, 10)
    private fun twoFullDaysBefore(day: LocalDate) = mapOf(day.minusDays(1) to full, day.minusDays(2) to full)
    private fun atRisk(section: AzkarSection, now: Instant, days: Map<LocalDate, AzkarDay>) = streakAtRisk(section, windows, days, now, berlin)

    @Test
    fun `Серия под угрозой утром — утренние не дочитаны`() {
        val morning = date(2026, 10, 10, 10)
        assertEquals(2, atRisk(AzkarSection.Morning, morning, twoFullDaysBefore(october10)))
        assertEquals(2, atRisk(AzkarSection.Morning, morning, twoFullDaysBefore(october10) + (october10 to AzkarDay(morning = AzkarDayRecord(5, 16)))))
        assertNull(atRisk(AzkarSection.Morning, morning, twoFullDaysBefore(october10) + (october10 to AzkarDay(morning = AzkarDayRecord(16, 16)))), "дочитаны")
        assertNull(atRisk(AzkarSection.Morning, morning, emptyMap()), "серии нет")
        assertNull(atRisk(AzkarSection.Morning, date(2026, 10, 10, 13), twoFullDaysBefore(october10)), "окно закрыто")
    }

    @Test
    fun `Серия под угрозой вечером — только если утренние этого дня прочитаны`() {
        val morningDone = twoFullDaysBefore(october10) + (october10 to AzkarDay(morning = AzkarDayRecord(16, 16)))
        // Вечерние 17:00–02:00: в полночь — ещё день 10 октября.
        val midnight = date(2026, 10, 11, 0)
        assertEquals(2, atRisk(AzkarSection.Evening, midnight, morningDone))
        assertNull(atRisk(AzkarSection.Evening, midnight, twoFullDaysBefore(october10)), "утренние пропущены — серия уже прервана")
        assertNull(atRisk(AzkarSection.Evening, midnight, morningDone + (october10 to full)), "день полный")
    }
}
