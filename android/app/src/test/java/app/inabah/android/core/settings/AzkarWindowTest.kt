package app.inabah.android.core.settings

import androidx.datastore.preferences.core.intPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.settings.AzkarPeriod.Kind
import java.time.Instant
import java.time.LocalDate
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

/** «Расписание времени азкаров» (iOS `AzkarWindowTests`). */
class AzkarWindowScheduleTest {
    private val morning = AzkarWindowSchedule(AzkarWindowSettings.defaultWindow(AzkarSection.Morning), berlin)
    private val evening = AzkarWindowSchedule(AzkarWindowSettings.defaultWindow(AzkarSection.Evening), berlin)

    private fun period(kind: Kind, day: Int, until: Instant) = AzkarPeriod(kind, LocalDate.of(2026, 10, day), until)

    @Test
    fun `Утренние 5·00–12·00 — начало уже окно, конец уже промежуток`() {
        val cases = listOf(
            date(2026, 10, 3, 4, 59) to period(Kind.Gap, 3, until = date(2026, 10, 3, 5)),
            date(2026, 10, 3, 5, 0) to period(Kind.Window, 3, until = date(2026, 10, 3, 12)),
            date(2026, 10, 3, 11, 59) to period(Kind.Window, 3, until = date(2026, 10, 3, 12)),
            date(2026, 10, 3, 12, 0) to period(Kind.Gap, 4, until = date(2026, 10, 4, 5)),
            date(2026, 10, 3, 23, 0) to period(Kind.Gap, 4, until = date(2026, 10, 4, 5)),
        )
        for ((now, expected) in cases) assertEquals(expected, morning.period(now), "в $now")
    }

    @Test
    fun `Вечерние 17·00–02·00 — прочитанное после полуночи относится к дате начала`() {
        val cases = listOf(
            date(2026, 10, 3, 16, 59) to period(Kind.Gap, 3, until = date(2026, 10, 3, 17)),
            date(2026, 10, 3, 21, 0) to period(Kind.Window, 3, until = date(2026, 10, 4, 2)),
            date(2026, 10, 4, 0, 30) to period(Kind.Window, 3, until = date(2026, 10, 4, 2)),
            date(2026, 10, 4, 2, 0) to period(Kind.Gap, 4, until = date(2026, 10, 4, 17)),
        )
        for ((now, expected) in cases) assertEquals(expected, evening.period(now), "в $now")
    }

    @Test
    fun `Ночь перевода часов на летнее время — 02·00 нет, окно до 03·00`() {
        // В ночь на 29 марта 2026 в Берлине часы переводят с 02:00 на 03:00.
        val period = evening.period(date(2026, 3, 28, 22))
        assertEquals(Kind.Window, period.kind)
        assertEquals(ZonedDateTime.of(2026, 3, 29, 3, 0, 0, 0, berlin).toInstant(), period.validUntil)
    }

    @Test
    fun `Начало окна в ночь перевода часов на летнее время — окно начинается в 03·00`() {
        val night = AzkarWindowSchedule(AzkarWindow(DayTime.of(2, 30), DayTime.of(6, 0)), berlin)
        val threeAm = ZonedDateTime.of(2026, 3, 29, 3, 0, 0, 0, berlin).toInstant()

        assertEquals(AzkarPeriod(Kind.Gap, LocalDate.of(2026, 3, 29), threeAm), night.period(date(2026, 3, 29, 1, 59)))
        assertEquals(Kind.Window, night.period(threeAm).kind)
    }

    @Test
    fun `Осенний перевод часов — 02·00 повторяется, но окно заканчивается один раз`() {
        // 25 октября 2026: в 03:00 летнего времени часы переводят на 02:00 зимнего.
        val firstTwo = ZonedDateTime.of(2026, 10, 25, 2, 0, 0, 0, berlin).withEarlierOffsetAtOverlap().toInstant()
        val secondTwo = ZonedDateTime.of(2026, 10, 25, 2, 0, 0, 0, berlin).withLaterOffsetAtOverlap().toInstant()

        val window = evening.period(date(2026, 10, 24, 23))
        assertEquals(firstTwo, window.validUntil)
        val gap = evening.period(secondTwo)
        assertEquals(Kind.Gap, gap.kind)
        assertEquals(date(2026, 10, 25, 17), gap.validUntil)
        assertTrue(gap.isSameSpan(evening.period(window.validUntil)), "второе 02:00 — тот же промежуток")
    }

    @Test
    fun `Москва → Берлин внутри окна — то же окно`() {
        val inMoscow = AzkarWindowSchedule(AzkarWindowSettings.defaultWindow(AzkarSection.Morning), moscow)
            .period(moscowDate(2026, 10, 3, 8))
        val inBerlin = morning.period(date(2026, 10, 3, 9))

        assertTrue(inBerlin.isSameSpan(inMoscow))
        assertEquals(date(2026, 10, 3, 12), inBerlin.validUntil)
    }
}

/** «Время азкаров в настройках». */
class AzkarWindowSettingsTest {
    @Test
    fun `По умолчанию утренние — 5·00–12·00, вечерние — 17·00–02·00`() = TestStorage.run { storage ->
        val settings = AzkarWindowSettings(storage.storage)

        assertEquals(AzkarWindow(DayTime.of(5, 0), DayTime.of(12, 0)), settings.window(AzkarSection.Morning))
        assertEquals(AzkarWindow(DayTime.of(17, 0), DayTime.of(2, 0)), settings.window(AzkarSection.Evening))
    }

    @Test
    fun `Новое время сохраняется между запусками`() = TestStorage.run { storage ->
        val settings = AzkarWindowSettings(storage.storage)
        settings.setStart(DayTime.of(4, 30), AzkarSection.Morning)
        settings.setEnd(DayTime.of(1, 0), AzkarSection.Evening)

        val restored = AzkarWindowSettings(storage.restart())
        assertEquals(AzkarWindow(DayTime.of(4, 30), DayTime.of(12, 0)), restored.window(AzkarSection.Morning))
        assertEquals(AzkarWindow(DayTime.of(17, 0), DayTime.of(1, 0)), restored.window(AzkarSection.Evening))
    }

    @Test
    fun `Начало, совпадающее с концом, не сохраняется`() = TestStorage.run { storage ->
        val settings = AzkarWindowSettings(storage.storage)
        settings.setStart(DayTime.of(12, 0), AzkarSection.Morning)
        settings.setEnd(DayTime.of(5, 0), AzkarSection.Morning)

        val default = AzkarWindowSettings.defaultWindow(AzkarSection.Morning)
        assertEquals(default, settings.window(AzkarSection.Morning))
        assertEquals(default, AzkarWindowSettings(storage.restart()).window(AzkarSection.Morning))
    }

    @Test
    fun `Время обнуления версии 1·0·0 не переносится и удаляется`() = TestStorage.run { storage ->
        val legacy = intPreferencesKey("azkar.reset.morning")
        val settings = AzkarWindowSettings(storage.seed { it[legacy] = 16 * 60 })

        assertEquals(AzkarWindowSettings.defaultWindow(AzkarSection.Morning), settings.window(AzkarSection.Morning))
        assertFalse(legacy in storage.persisted())
    }
}
