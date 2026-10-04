package app.inabah.android.core.settings

import app.inabah.android.core.content.model.AzkarSection
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Часовой пояс с переходом на летнее время — тесты не зависят от машины. */
val berlin: ZoneId = ZoneId.of("Europe/Berlin")

/** Москва — на час впереди Берлина осенью (летнего времени нет). */
val moscow: ZoneId = ZoneId.of("Europe/Moscow")

/** Момент по берлинскому времени. */
fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, berlin).toInstant()

/** Момент по московскому времени. */
fun moscowDate(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, moscow).toInstant()

/** «Расписание обнуления». */
class AzkarResetScheduleTest {
    private val morning = AzkarResetSchedule(DayTime.of(17, 0), berlin)
    private val evening = AzkarResetSchedule(DayTime.of(2, 0), berlin)

    @Test
    fun `Утренние — до 17·00 период с вчерашних 17·00, после — с сегодняшних`() {
        val cases = listOf(
            date(2026, 10, 3, 16, 59) to date(2026, 10, 2, 17),
            date(2026, 10, 3, 17, 0) to date(2026, 10, 3, 17),
            date(2026, 10, 3, 23, 30) to date(2026, 10, 3, 17),
            date(2026, 10, 4, 6, 0) to date(2026, 10, 3, 17),
        )
        for ((now, expected) in cases) assertEquals(expected, morning.periodStart(at = now), "в $now")
    }

    @Test
    fun `Вечерние — прочитанное до 02·00 ночи относится к вчерашнему вечеру`() {
        val cases = listOf(
            date(2026, 10, 3, 21, 0) to date(2026, 10, 3, 2),
            date(2026, 10, 4, 1, 59) to date(2026, 10, 3, 2),
            date(2026, 10, 4, 2, 0) to date(2026, 10, 4, 2),
        )
        for ((now, expected) in cases) assertEquals(expected, evening.periodStart(at = now), "в $now")
    }

    @Test
    fun `Следующее обнуление`() {
        assertEquals(date(2026, 10, 3, 17), morning.nextReset(after = date(2026, 10, 3, 10)))
        assertEquals(date(2026, 10, 4, 17), morning.nextReset(after = date(2026, 10, 3, 17)))
        assertEquals(date(2026, 10, 4, 2), evening.nextReset(after = date(2026, 10, 3, 21)))
    }

    @Test
    fun `Ночь перевода часов на летнее время — 02·00 нет, обнуление в 03·00`() {
        // 29 марта 2026, Европа/Берлин: 02:00 → 03:00.
        val reset = evening.nextReset(after = date(2026, 3, 28, 21)).atZone(berlin)

        assertEquals(3, reset.hour)
        assertEquals(29, reset.dayOfMonth)
    }

    @Test
    fun `Ночь перевода часов — обнуление в 02·30 тоже в 03·00, как в iOS`() {
        val halfPast = AzkarResetSchedule(DayTime.of(2, 30), berlin)

        val reset = halfPast.nextReset(after = date(2026, 3, 28, 21)).atZone(berlin)

        assertEquals(29, reset.dayOfMonth)
        assertEquals(3, reset.hour)
        assertEquals(0, reset.minute)
    }

    @Test
    fun `Ближайшее наступление времени — при равенстве расстояний позднее`() {
        val five = AzkarResetSchedule(DayTime.of(5, 0), berlin)

        assertEquals(date(2026, 10, 3, 17), morning.boundary(nearest = date(2026, 10, 3, 17)))
        assertEquals(date(2026, 10, 3, 17), morning.boundary(nearest = date(2026, 10, 3, 6)))
        assertEquals(date(2026, 10, 2, 17), morning.boundary(nearest = date(2026, 10, 3, 4)))
        assertEquals(date(2026, 10, 4, 5), five.boundary(nearest = date(2026, 10, 3, 17)))
    }
}

/** «Период чтения раздела». */
class AzkarPeriodTest {
    private fun morning(hour: Int, minute: Int = 0, zone: ZoneId = berlin) =
        AzkarResetSchedule(DayTime.of(hour, minute), zone)

    @Test
    fun `Новый период длится до ближайшего обнуления`() {
        val period = AzkarPeriod.startingAt(date(2026, 10, 3, 8), morning(17))

        assertEquals(date(2026, 10, 3, 17), period.validUntil)
        assertFalse(period.isExpired(date(2026, 10, 3, 16, 59)))
        assertTrue(period.isExpired(date(2026, 10, 3, 17)))
    }

    /** Прочитано в 08:00, время обнуления (было 17:00) меняют в 16:45. */
    @Test
    fun `Смена времени — ближайшее обнуление не пропускается и не происходит дважды`() {
        val cases = listOf(
            // Новое время сегодня уже прошло — обнуление в прежние 17:00, а не завтра в 16:30.
            DayTime.of(16, 30) to date(2026, 10, 3, 17),
            // Ещё впереди — обнуление в новое время.
            DayTime.of(16, 50) to date(2026, 10, 3, 16, 50),
            // Позже прежнего — одно обнуление в 18:00, без промежуточного в 17:00.
            DayTime.of(18, 0) to date(2026, 10, 3, 18),
            // Утреннее время — уже прошло сегодня: обнуление в прежние 17:00.
            DayTime.of(7, 0) to date(2026, 10, 3, 17),
        )
        for ((newTime, expected) in cases) {
            val period = AzkarPeriod.startingAt(date(2026, 10, 3, 8), morning(17))
                .rescheduled(morning(newTime.hour, newTime.minute), now = date(2026, 10, 3, 16, 45))
            assertEquals(expected, period.validUntil, "новое время $newTime")
        }
    }

    @Test
    fun `Прокрутка колеса времени — результат не зависит от промежуточных значений`() {
        val now = date(2026, 10, 3, 16, 45)
        var period = AzkarPeriod.startingAt(date(2026, 10, 3, 8), morning(17))
        for (minute in listOf(55, 50, 46, 40, 30)) {
            period = period.rescheduled(morning(16, minute), now)
        }

        assertEquals(date(2026, 10, 3, 17), period.validUntil)
        assertFalse(period.isExpired(now))
    }

    @Test
    fun `Москва → Берлин — обнуление в 17·00 по Берлину, прочитанное не теряется`() {
        // Прочитано в 08:00 по Москве: граница — 17:00 МСК (16:00 по Берлину).
        val period = AzkarPeriod.startingAt(moscowDate(2026, 10, 3, 8), morning(17, zone = moscow))
            .relocated(morning(17), now = date(2026, 10, 3, 16, 30))

        assertEquals(date(2026, 10, 3, 17), period.validUntil)
        assertEquals(date(2026, 10, 3, 17), period.scheduledReset)
        assertEquals(berlin, period.timeZone)
    }

    @Test
    fun `Берлин → Москва — 17·00 по Москве уже прошло, обнуление в прежнюю границу`() {
        // Граница — 17:00 по Берлину = 18:00 МСК; в Москве уже 17:30.
        val period = AzkarPeriod.startingAt(date(2026, 10, 3, 8), morning(17))
            .relocated(morning(17, zone = moscow), now = moscowDate(2026, 10, 3, 17, 30))

        assertEquals(moscowDate(2026, 10, 3, 18), period.validUntil)
    }

    @Test
    fun `Тот же часовой пояс — граница не меняется`() {
        val period = AzkarPeriod.startingAt(date(2026, 10, 3, 8), morning(17))

        assertEquals(period, period.relocated(morning(18), now = date(2026, 10, 3, 9)))
    }

    @Test
    fun `Осенний перевод часов — обнуление в 02·00 ровно одно`() {
        // 25 октября 2026, Европа/Берлин: 03:00 летнего → 02:00 зимнего, час 02:00–03:00 повторяется.
        val evening = morning(2)
        val firstTwo = date(2026, 10, 25, 1).plusSeconds(60 * 60)
        val fromEvening = AzkarPeriod.startingAt(date(2026, 10, 24, 21), evening)
        assertEquals(firstTwo, fromEvening.validUntil)

        // Новый период, начатый в момент обнуления, длится до следующих суток, а не до повтора 02:00.
        val afterReset = AzkarPeriod.startingAt(firstTwo, evening)
        assertEquals(date(2026, 10, 26, 2), afterReset.validUntil)
    }
}

/** «Время обнуления в настройках». */
class AzkarResetSettingsTest {
    @Test
    fun `По умолчанию утренние — 17·00, вечерние — 02·00`() = TestStorage.run { storage ->
        val settings = AzkarResetSettings(storage.storage)

        assertEquals(DayTime.of(17, 0), settings.resetTime(AzkarSection.Morning))
        assertEquals(DayTime.of(2, 0), settings.resetTime(AzkarSection.Evening))
    }

    @Test
    fun `Новое время сохраняется между запусками`() = TestStorage.run { storage ->
        AzkarResetSettings(storage.storage).setResetTime(DayTime.of(16, 30), AzkarSection.Morning)

        assertEquals(DayTime.of(16, 30), AzkarResetSettings(storage.restart()).resetTime(AzkarSection.Morning))
    }
}
