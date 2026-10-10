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

/** Что включено в напоминаниях — одним значением: расписание пересчитывается по нему целиком. */
data class ReminderPreferences(
    /** В начале времени азкаров — по разделам. */
    val azkarStart: Set<AzkarSection> = emptySet(),
    /** «Ещё не прочитаны» за 2 часа до конца — у разделов, где включено напоминание в начале. */
    val azkarUnfinished: Boolean = false,
    val hadithOfDay: Boolean = false,
    val hadithTime: DayTime = ReminderSettings.DEFAULT_HADITH_TIME,
) {
    val isAnyEnabled: Boolean get() = azkarStart.isNotEmpty() || hadithOfDay
}

/**
 * Напоминания (решения пользователя 2026-10-10, `docs/07-screen-settings.md`): по умолчанию всё
 * выключено. Хранятся в общем DataStore — попадают в резервную копию. Вызывать с главного потока.
 */
class ReminderSettings(private val storage: PreferencesStorage) {
    private val _preferences = MutableStateFlow(read())
    private val _offerAnswered = MutableStateFlow(storage.snapshot[OFFER_KEY] ?: false)

    val preferences: StateFlow<ReminderPreferences> = _preferences.asStateFlow()

    /** Предложение «Включить напоминания?» на главной азкаров уже получило ответ — больше не показывать. */
    val offerAnswered: StateFlow<Boolean> = _offerAnswered.asStateFlow()

    /** Последний показанный хадис дня — с него продолжается круг, когда всё прочитано. */
    val lastHadithOfDay: HadithId?
        get() = storage.snapshot[LAST_HADITH_KEY]?.let(::parseHadithId)

    fun setAzkarStart(section: AzkarSection, enabled: Boolean) {
        val current = _preferences.value.azkarStart
        update { it.copy(azkarStart = if (enabled) current + section else current - section) }
    }

    fun setAzkarUnfinished(enabled: Boolean) = update { it.copy(azkarUnfinished = enabled) }

    fun setHadithOfDay(enabled: Boolean) = update { it.copy(hadithOfDay = enabled) }

    fun setHadithTime(time: DayTime) = update { it.copy(hadithTime = time) }

    fun answerOffer() {
        if (_offerAnswered.value) return
        _offerAnswered.value = true
        storage.edit { it[OFFER_KEY] = true }
    }

    fun rememberHadithOfDay(id: HadithId) {
        storage.edit { it[LAST_HADITH_KEY] = "${id.collection.key}_${id.number}" }
    }

    private fun update(transform: (ReminderPreferences) -> ReminderPreferences) {
        val next = transform(_preferences.value)
        if (next == _preferences.value) return
        _preferences.value = next
        // Включил напоминание сам (в настройках) — предлагать на главной уже незачем.
        if (next.isAnyEnabled) answerOffer()
        storage.edit { preferences ->
            AzkarSection.entries.forEach { preferences[startKey(it)] = it in next.azkarStart }
            preferences[UNFINISHED_KEY] = next.azkarUnfinished
            preferences[HADITH_KEY] = next.hadithOfDay
            preferences[HADITH_TIME_KEY] = next.hadithTime.minutesSinceMidnight
        }
    }

    private fun read(): ReminderPreferences {
        val snapshot = storage.snapshot
        return ReminderPreferences(
            azkarStart = AzkarSection.entries.filterTo(mutableSetOf()) { snapshot[startKey(it)] == true },
            azkarUnfinished = snapshot[UNFINISHED_KEY] ?: false,
            hadithOfDay = snapshot[HADITH_KEY] ?: false,
            hadithTime = snapshot[HADITH_TIME_KEY]?.let(DayTime::fromMinutes) ?: DEFAULT_HADITH_TIME,
        )
    }

    companion object {
        val DEFAULT_HADITH_TIME: DayTime = DayTime.of(20, 0)

        private fun startKey(section: AzkarSection) = booleanPreferencesKey("reminders.azkar.${section.key}")
        private val UNFINISHED_KEY = booleanPreferencesKey("reminders.azkar.unfinished")
        private val HADITH_KEY = booleanPreferencesKey("reminders.hadith.enabled")
        private val HADITH_TIME_KEY = intPreferencesKey("reminders.hadith.time")
        private val OFFER_KEY = booleanPreferencesKey("reminders.offer.answered")
        private val LAST_HADITH_KEY = stringPreferencesKey("reminders.hadith.last")

        private fun parseHadithId(value: String): HadithId? {
            val collectionKey = value.substringBeforeLast('_')
            val number = value.substringAfterLast('_').toIntOrNull()?.takeIf { it >= 1 } ?: return null
            return HadithCollection.fromKey(collectionKey)?.let { HadithId(it, number) }
        }
    }
}
