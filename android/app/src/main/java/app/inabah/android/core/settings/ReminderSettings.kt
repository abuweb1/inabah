package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Напоминание пройти урок «Учимся читать» (решение пользователя 2026-10-20): до трёх в день, у каждого своё время;
 * [key] - в ключах хранения.
 */
enum class ReadingReminderSlot(val key: String, val defaultTime: DayTime) {
    Morning("morning", DayTime.of(9, 0)),
    Day("day", DayTime.of(14, 0)),
    Evening("evening", DayTime.of(19, 0)),
}

/** Что включено в напоминаниях — одним значением: расписание пересчитывается по нему целиком. */
data class ReminderPreferences(
    /** В начале времени азкаров — по разделам. */
    val azkarStart: Set<AzkarSection> = emptySet(),
    /** «Ещё не прочитаны» за 2 часа до конца — у разделов, где включено напоминание в начале. */
    val azkarUnfinished: Boolean = false,
    val hadithOfDay: Boolean = false,
    val hadithTime: DayTime = ReminderSettings.DEFAULT_HADITH_TIME,
    /** «Серия под угрозой» за 2 часа до конца — у обоих разделов, независимо от [azkarStart] (2026-10-11). */
    val azkarStreak: Boolean = false,
    /** Включённые напоминания пройти урок. */
    val readingSlots: Set<ReadingReminderSlot> = emptySet(),
    /** Время каждого напоминания урока; нет слота - его [ReadingReminderSlot.defaultTime]. */
    val readingTimes: Map<ReadingReminderSlot, DayTime> = emptyMap(),
) {
    val isAnyEnabled: Boolean get() = azkarStart.isNotEmpty() || hadithOfDay || azkarStreak || readingSlots.isNotEmpty()

    fun readingTime(slot: ReadingReminderSlot): DayTime = readingTimes[slot] ?: slot.defaultTime
}

/**
 * Напоминания (решения пользователя 2026-10-10, `docs/07-screen-settings.md`): по умолчанию всё
 * выключено. Хранятся в общем DataStore — попадают в резервную копию. Вызывать с главного потока.
 */
class ReminderSettings(private val storage: PreferencesStorage) {
    private val _preferences = MutableStateFlow(read())
    private val _offerAnswered = MutableStateFlow(storage.snapshot[OFFER_KEY] ?: false)
    private val _readingOfferAnswered = MutableStateFlow(storage.snapshot[READING_OFFER_KEY] ?: false)

    val preferences: StateFlow<ReminderPreferences> = _preferences.asStateFlow()

    /** Предложение «Включить напоминания?» на главной азкаров уже получило ответ — больше не показывать. */
    val offerAnswered: StateFlow<Boolean> = _offerAnswered.asStateFlow()

    /** Предложение «Напоминать о занятиях?» на списке шагов «Учимся читать» уже получило ответ (2026-10-20). */
    val readingOfferAnswered: StateFlow<Boolean> = _readingOfferAnswered.asStateFlow()

    /** Последний показанный хадис дня — с него продолжается круг, когда всё прочитано. */
    val lastHadithOfDay: HadithId?
        get() = storage.snapshot[LAST_HADITH_KEY]?.let(::parseHadithId)

    fun setAzkarStart(section: AzkarSection, enabled: Boolean) {
        val current = _preferences.value.azkarStart
        update { it.copy(azkarStart = if (enabled) current + section else current - section) }
    }

    fun setAzkarUnfinished(enabled: Boolean) = update { it.copy(azkarUnfinished = enabled) }

    fun setAzkarStreak(enabled: Boolean) = update { it.copy(azkarStreak = enabled) }

    fun setHadithOfDay(enabled: Boolean) = update { it.copy(hadithOfDay = enabled) }

    fun setHadithTime(time: DayTime) = update { it.copy(hadithTime = time) }

    fun setReadingSlot(slot: ReadingReminderSlot, enabled: Boolean) {
        val current = _preferences.value.readingSlots
        update { it.copy(readingSlots = if (enabled) current + slot else current - slot) }
    }

    fun setReadingTime(slot: ReadingReminderSlot, time: DayTime) =
        update { it.copy(readingTimes = it.readingTimes + (slot to time)) }

    fun answerOffer() {
        if (_offerAnswered.value) return
        _offerAnswered.value = true
        storage.edit { it[OFFER_KEY] = true }
    }

    fun answerReadingOffer() {
        if (_readingOfferAnswered.value) return
        _readingOfferAnswered.value = true
        storage.edit { it[READING_OFFER_KEY] = true }
    }

    fun rememberHadithOfDay(id: HadithId) {
        storage.edit { it[LAST_HADITH_KEY] = "${id.collection.key}_${id.number}" }
    }

    private fun update(transform: (ReminderPreferences) -> ReminderPreferences) {
        val next = transform(_preferences.value)
        if (next == _preferences.value) return
        _preferences.value = next
        // Включил напоминание сам (в настройках) — предлагать его уже незачем: азкары и хадис — на главной
        // азкаров, урок — на списке шагов.
        if (next.azkarStart.isNotEmpty() || next.azkarStreak || next.hadithOfDay) answerOffer()
        if (next.readingSlots.isNotEmpty()) answerReadingOffer()
        storage.edit { preferences ->
            AzkarSection.entries.forEach { preferences[startKey(it)] = it in next.azkarStart }
            preferences[UNFINISHED_KEY] = next.azkarUnfinished
            preferences[STREAK_KEY] = next.azkarStreak
            preferences[HADITH_KEY] = next.hadithOfDay
            preferences[HADITH_TIME_KEY] = next.hadithTime.minutesSinceMidnight
            ReadingReminderSlot.entries.forEach { preferences[readingKey(it)] = it in next.readingSlots }
            // Только выбранное время: значение по умолчанию не записывается, его можно поменять в новой версии.
            next.readingTimes.forEach { (slot, time) -> preferences[readingTimeKey(slot)] = time.minutesSinceMidnight }
        }
    }

    private fun read(): ReminderPreferences {
        val snapshot = storage.snapshot
        return ReminderPreferences(
            azkarStart = AzkarSection.entries.filterTo(mutableSetOf()) { snapshot[startKey(it)] == true },
            azkarUnfinished = snapshot[UNFINISHED_KEY] ?: false,
            azkarStreak = snapshot[STREAK_KEY] ?: false,
            hadithOfDay = snapshot[HADITH_KEY] ?: false,
            hadithTime = snapshot[HADITH_TIME_KEY]?.let(DayTime::fromMinutes) ?: DEFAULT_HADITH_TIME,
            readingSlots = ReadingReminderSlot.entries.filterTo(mutableSetOf()) { snapshot[readingKey(it)] == true },
            readingTimes = ReadingReminderSlot.entries.mapNotNull { slot ->
                snapshot[readingTimeKey(slot)]?.let(DayTime::fromMinutes)?.let { slot to it }
            }.toMap(),
        )
    }

    companion object {
        val DEFAULT_HADITH_TIME: DayTime = DayTime.of(20, 0)

        private fun startKey(section: AzkarSection) = booleanPreferencesKey("reminders.azkar.${section.key}")
        private fun readingKey(slot: ReadingReminderSlot) = booleanPreferencesKey("reminders.reading.${slot.key}")
        private fun readingTimeKey(slot: ReadingReminderSlot) = intPreferencesKey("reminders.reading.${slot.key}.time")
        private val UNFINISHED_KEY = booleanPreferencesKey("reminders.azkar.unfinished")
        private val STREAK_KEY = booleanPreferencesKey("reminders.azkar.streak")
        private val HADITH_KEY = booleanPreferencesKey("reminders.hadith.enabled")
        private val HADITH_TIME_KEY = intPreferencesKey("reminders.hadith.time")
        private val OFFER_KEY = booleanPreferencesKey("reminders.offer.answered")
        private val READING_OFFER_KEY = booleanPreferencesKey("reminders.reading.offer.answered")
        private val LAST_HADITH_KEY = stringPreferencesKey("reminders.hadith.last")

        private fun parseHadithId(value: String): HadithId? {
            val collectionKey = value.substringBeforeLast('_')
            val number = value.substringAfterLast('_').toIntOrNull()?.takeIf { it >= 1 } ?: return null
            return HadithCollection.fromKey(collectionKey)?.let { HadithId(it, number) }
        }
    }
}
