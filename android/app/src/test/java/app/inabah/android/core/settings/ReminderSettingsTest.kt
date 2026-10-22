package app.inabah.android.core.settings

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** «Напоминания»: по умолчанию всё выключено, выбор переживает перезапуск. */
class ReminderSettingsTest {
    @Test
    fun `По умолчанию всё выключено, хадис дня — в 20 00, предложение не отвечено`() = TestStorage.run { storage ->
        val settings = ReminderSettings(storage.storage)

        assertEquals(ReminderPreferences(), settings.preferences.value)
        assertFalse(settings.preferences.value.isAnyEnabled)
        assertEquals(DayTime.of(20, 0), settings.preferences.value.hadithTime)
        assertFalse(settings.offerAnswered.value)
        assertNull(settings.lastHadithOfDay)
    }

    @Test
    fun `Выбор сохраняется и переживает перезапуск`() = TestStorage.run { storage ->
        val settings = ReminderSettings(storage.storage)
        settings.setAzkarStart(AzkarSection.Morning, true)
        settings.setAzkarUnfinished(true)
        settings.setHadithOfDay(true)
        settings.setHadithTime(DayTime.of(21, 30))
        settings.answerOffer()
        settings.rememberHadithOfDay(HadithId(HadithCollection.Qudsi, 7))

        val restarted = ReminderSettings(storage.restart())

        assertEquals(
            ReminderPreferences(setOf(AzkarSection.Morning), azkarUnfinished = true, hadithOfDay = true, hadithTime = DayTime.of(21, 30)),
            restarted.preferences.value,
        )
        assertTrue(restarted.offerAnswered.value)
        assertEquals(HadithId(HadithCollection.Qudsi, 7), restarted.lastHadithOfDay)

        restarted.setAzkarStart(AzkarSection.Morning, false)
        assertEquals(emptySet(), ReminderSettings(storage.restart()).preferences.value.azkarStart)
    }

    @Test
    fun `Включил напоминание в настройках — предложение на главной больше не нужно`() = TestStorage.run { storage ->
        val settings = ReminderSettings(storage.storage)

        settings.setHadithOfDay(true)

        assertTrue(settings.offerAnswered.value)
        assertTrue(ReminderSettings(storage.restart()).offerAnswered.value)
    }

    @Test
    fun `Серия под угрозой — сохраняется и сама снимает предложение на главной`() = TestStorage.run { storage ->
        val settings = ReminderSettings(storage.storage)

        settings.setAzkarStreak(true)

        assertTrue(settings.offerAnswered.value)
        assertTrue(ReminderSettings(storage.restart()).preferences.value.azkarStreak)
    }

    @Test
    fun `Напоминания урока — выключены, время по умолчанию 9 14 19, выбор и время переживают перезапуск`() = TestStorage.run { storage ->
        val settings = ReminderSettings(storage.storage)
        assertEquals(emptySet(), settings.preferences.value.readingSlots)
        assertEquals(
            listOf(DayTime.of(9, 0), DayTime.of(14, 0), DayTime.of(19, 0)),
            ReadingReminderSlot.entries.map(settings.preferences.value::readingTime),
        )

        settings.setReadingSlot(ReadingReminderSlot.Evening, true)
        settings.setReadingTime(ReadingReminderSlot.Evening, DayTime.of(21, 15))

        val restarted = ReminderSettings(storage.restart()).preferences.value
        assertEquals(setOf(ReadingReminderSlot.Evening), restarted.readingSlots)
        assertEquals(DayTime.of(21, 15), restarted.readingTime(ReadingReminderSlot.Evening))
        assertEquals(DayTime.of(9, 0), restarted.readingTime(ReadingReminderSlot.Morning))
        assertTrue(restarted.isAnyEnabled)
    }

    @Test
    fun `Напоминание урока отвечает на своё предложение, не на предложение азкаров`() = TestStorage.run { storage ->
        val settings = ReminderSettings(storage.storage)
        assertFalse(settings.readingOfferAnswered.value)

        settings.setReadingSlot(ReadingReminderSlot.Morning, true)

        assertTrue(settings.readingOfferAnswered.value)
        assertFalse(settings.offerAnswered.value, "карточка азкаров остаётся")
        val restarted = ReminderSettings(storage.restart())
        assertTrue(restarted.readingOfferAnswered.value, "после перезапуска")

        restarted.setHadithOfDay(true)
        assertTrue(restarted.offerAnswered.value)
    }
}
