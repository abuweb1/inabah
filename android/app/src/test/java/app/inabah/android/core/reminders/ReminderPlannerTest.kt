package app.inabah.android.core.reminders

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.settings.AzkarWindow
import app.inabah.android.core.settings.DayTime
import app.inabah.android.core.settings.ReadingReminderSlot
import app.inabah.android.core.settings.ReminderPreferences
import app.inabah.android.core.settings.berlin
import app.inabah.android.core.settings.date
import app.inabah.android.core.settings.testWindows
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** «Напоминания»: когда срабатывает каждое (окна азкаров — `testWindows`: 05:00–12:00 и 17:00–02:00). */
class ReminderPlannerTest {
    private val windows = testWindows
    private val all = ReminderPreferences(azkarStart = AzkarSection.entries.toSet(), azkarUnfinished = true, hadithOfDay = true)

    private fun plan(preferences: ReminderPreferences, now: java.time.Instant, windows: Map<AzkarSection, AzkarWindow> = this.windows) =
        planReminders(preferences, windows, now, berlin).associateBy { it.kind }

    @Test
    fun `Выключено всё — ничего не планируется`() {
        assertTrue(planReminders(ReminderPreferences(), windows, date(2026, 10, 10, 8), berlin).isEmpty())
    }

    @Test
    fun `Начало окна — ближайшее после сейчас, «не прочитаны» — за 2 часа до конца текущего окна`() {
        val planned = plan(all, date(2026, 10, 10, 8))

        assertEquals(date(2026, 10, 11, 5), planned.getValue(ReminderKind.MorningStart).at, "утро уже началось — завтра")
        assertEquals(date(2026, 10, 10, 10), planned.getValue(ReminderKind.MorningUnfinished).at, "сегодня в 10:00")
        assertEquals(LocalDate.of(2026, 10, 10), planned.getValue(ReminderKind.MorningUnfinished).windowDay)
        assertEquals(date(2026, 10, 10, 17), planned.getValue(ReminderKind.EveningStart).at)
        assertEquals(date(2026, 10, 10, 20), planned.getValue(ReminderKind.HadithOfDay).at, "по умолчанию 20:00")
    }

    @Test
    fun `Вечерние через полночь — «не прочитаны» в полночь, день окна — дата начала`() {
        val planned = plan(all, date(2026, 10, 10, 23, 30))

        val unfinished = planned.getValue(ReminderKind.EveningUnfinished)
        assertEquals(date(2026, 10, 11, 0), unfinished.at)
        assertEquals(LocalDate.of(2026, 10, 10), unfinished.windowDay)
    }

    @Test
    fun `Момент «не прочитаны» уже прошёл — следующего окна`() {
        val planned = plan(all, date(2026, 10, 10, 11, 30))

        assertEquals(date(2026, 10, 11, 10), planned.getValue(ReminderKind.MorningUnfinished).at)
        assertEquals(LocalDate.of(2026, 10, 11), planned.getValue(ReminderKind.MorningUnfinished).windowDay)
    }

    @Test
    fun `Окно короче запаса — «не прочитаны» в его середине`() {
        val short = windows + (AzkarSection.Morning to AzkarWindow(DayTime.of(6, 0), DayTime.of(6, 40)))

        val planned = plan(all, date(2026, 10, 10, 4), short)

        assertEquals(date(2026, 10, 10, 6, 20), planned.getValue(ReminderKind.MorningUnfinished).at)
    }

    @Test
    fun `«Не прочитаны» — только у разделов с напоминанием в начале`() {
        val planned = plan(ReminderPreferences(azkarStart = setOf(AzkarSection.Evening), azkarUnfinished = true), date(2026, 10, 10, 8))

        assertEquals(setOf(ReminderKind.EveningStart, ReminderKind.EveningUnfinished), planned.keys)
    }

    @Test
    fun `Перевод часов — время суток по местному времени`() {
        // Сейчас — до перехода на зимнее время (25.10.2026, 03:00 CEST → 02:00 CET), напоминание — после него.
        val planned = plan(ReminderPreferences(hadithOfDay = true, hadithTime = DayTime.of(20, 0)), utc("2026-10-24T22:00:00Z"))

        assertEquals(utc("2026-10-25T19:00:00Z"), planned.getValue(ReminderKind.HadithOfDay).at, "20:00 CET, не 20:00 CEST")
    }

    @Test
    fun `Весной время в разрыве — момент перехода`() {
        // 29.03.2026 в Берлине 02:00 CET → 03:00 CEST: 02:30 не бывает. Сейчас — 01:00 CET.
        val planned = plan(ReminderPreferences(hadithOfDay = true, hadithTime = DayTime.of(2, 30)), utc("2026-03-29T00:00:00Z"))

        assertEquals(utc("2026-03-29T01:00:00Z"), planned.getValue(ReminderKind.HadithOfDay).at, "03:00 CEST")
    }

    @Test
    fun `Осенью повтор часа — первое наступление и без второго срабатывания`() {
        // 25.10.2026 02:30 бывает дважды: 00:30Z (CEST) и 01:30Z (CET).
        val preferences = ReminderPreferences(hadithOfDay = true, hadithTime = DayTime.of(2, 30))

        val before = plan(preferences, utc("2026-10-24T23:00:00Z"))
        assertEquals(utc("2026-10-25T00:30:00Z"), before.getValue(ReminderKind.HadithOfDay).at, "раннее — 02:30 CEST")

        val afterFirst = plan(preferences, utc("2026-10-25T00:30:00Z"))
        assertEquals(utc("2026-10-26T01:30:00Z"), afterFirst.getValue(ReminderKind.HadithOfDay).at, "повтор часа пропущен — завтра")
    }

    private fun utc(text: String): Instant = Instant.parse(text)

    private fun hadiths(collection: HadithCollection, count: Int) =
        (1..count).map { Hadith(HadithId(collection, it), "حديث", translation = null) }

    @Test
    fun `Хадис дня — первый непрочитанный по порядку сборников`() {
        val content = mapOf(HadithCollection.Nawawi to hadiths(HadithCollection.Nawawi, 3), HadithCollection.Qudsi to hadiths(HadithCollection.Qudsi, 2))
        val read = setOf(HadithId(HadithCollection.Qudsi, 1))

        val picked = pickHadithOfDay(listOf(HadithCollection.Qudsi, HadithCollection.Nawawi), content, { it in read }, last = null)

        assertEquals(HadithId(HadithCollection.Qudsi, 2), picked?.id)
    }

    @Test
    fun `Всё прочитано — следующий после последнего показанного, по кругу`() {
        val content = mapOf(HadithCollection.Nawawi to hadiths(HadithCollection.Nawawi, 2))
        val order = listOf(HadithCollection.Nawawi)

        assertEquals(HadithId(HadithCollection.Nawawi, 2), pickHadithOfDay(order, content, { true }, HadithId(HadithCollection.Nawawi, 1))?.id)
        assertEquals(HadithId(HadithCollection.Nawawi, 1), pickHadithOfDay(order, content, { true }, HadithId(HadithCollection.Nawawi, 2))?.id)
        assertEquals(HadithId(HadithCollection.Nawawi, 1), pickHadithOfDay(order, content, { true }, last = null)?.id)
        assertNull(pickHadithOfDay(order, emptyMap(), { false }, last = null))
    }

    // «Серия под угрозой» (2026-10-11): за час до конца, без напоминаний в начале.
    @Test
    fun `Серия под угрозой — у обоих разделов за час до конца, независимо от начала`() {
        val planned = plan(ReminderPreferences(azkarStreak = true), date(2026, 10, 10, 8))

        assertEquals(setOf(ReminderKind.MorningStreak, ReminderKind.EveningStreak), planned.keys)
        assertEquals(date(2026, 10, 10, 11), planned.getValue(ReminderKind.MorningStreak).at)
        assertEquals(date(2026, 10, 11, 1), planned.getValue(ReminderKind.EveningStreak).at, "вечерние до 02:00 — в 01:00")
        assertTrue(ReminderPreferences(azkarStreak = true).isAnyEnabled)
        assertTrue(ReminderKind.MorningStreak !in plan(all, date(2026, 10, 10, 8)).keys, "выключено — не планируется")
    }

    // Решение пользователя 2026-10-11: при обоих включённых приходят оба, каждое в своё время.
    @Test
    fun `Серия и не прочитаны — оба, в разное время`() {
        val planned = plan(all.copy(azkarStreak = true), date(2026, 10, 10, 8))

        assertEquals(date(2026, 10, 10, 10), planned.getValue(ReminderKind.MorningUnfinished).at)
        assertEquals(date(2026, 10, 10, 11), planned.getValue(ReminderKind.MorningStreak).at)
        assertEquals(date(2026, 10, 11, 0), planned.getValue(ReminderKind.EveningUnfinished).at)
        assertEquals(date(2026, 10, 11, 1), planned.getValue(ReminderKind.EveningStreak).at)
    }

    @Test
    fun `Пора на урок — у каждого включённого слота своё время, прошедшее — завтра`() {
        val preferences = ReminderPreferences(
            readingSlots = setOf(ReadingReminderSlot.Morning, ReadingReminderSlot.Evening),
            readingTimes = mapOf(ReadingReminderSlot.Evening to DayTime.of(21, 30)),
        )
        val planned = plan(preferences, date(2026, 10, 10, 12))

        assertEquals(date(2026, 10, 11, 9), planned.getValue(ReminderKind.ReadingMorning).at, "9:00 уже прошло - завтра")
        assertEquals(date(2026, 10, 10, 21, 30), planned.getValue(ReminderKind.ReadingEvening).at)
        assertNull(planned[ReminderKind.ReadingDay], "днём выключено")
    }

    @Test
    fun `Напоминания урока — свой канал и одно уведомление на всех`() {
        val reading = ReminderKind.entries.filter { it.readingSlot != null }

        assertEquals(ReadingReminderSlot.entries, reading.map { it.readingSlot })
        reading.forEach { kind ->
            assertEquals(ReminderChannels.READING, kind.channel)
            assertEquals(ReminderKind.ReadingMorning.notificationId, kind.notificationId)
        }
        assertEquals(ReminderKind.entries.size, ReminderKind.entries.map { it.requestCode }.toSet().size, "будильники - у каждого вида свой")
        assertEquals(ReminderChannels.HADITH, ReminderKind.HadithOfDay.channel)
    }
}
