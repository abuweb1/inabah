package app.inabah.android.core.settings

import androidx.datastore.preferences.core.intPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Время ежедневного обнуления разделов (iOS `AzkarResetSettings`): утренние — 17:00, вечерние — 02:00.
 * Хранится в минутах от полуночи; запись того же значения ничего не делает.
 */
class AzkarResetSettings(private val storage: PreferencesStorage) {
    private val _resetTimes = MutableStateFlow(
        AzkarSection.entries.associateWith { section ->
            storage.snapshot[key(section)]?.let(DayTime::fromMinutes) ?: defaultTime(section)
        },
    )
    val resetTimes: StateFlow<Map<AzkarSection, DayTime>> = _resetTimes.asStateFlow()

    private var onChange: (() -> Unit)? = null

    fun resetTime(section: AzkarSection): DayTime = _resetTimes.value.getValue(section)

    fun setResetTime(time: DayTime, section: AzkarSection) {
        if (time == resetTime(section)) return
        _resetTimes.update { it + (section to time) }
        storage.edit { it[key(section)] = time.minutesSinceMidnight }
        onChange?.invoke()
    }

    /** Единственный подписчик на смену времени — стор азкаров. */
    fun onResetTimeChange(listener: () -> Unit) {
        check(onChange == null) { "У времени обнуления уже есть подписчик" }
        onChange = listener
    }

    companion object {
        fun defaultTime(section: AzkarSection): DayTime = when (section) {
            AzkarSection.Morning -> DayTime.of(hour = 17, minute = 0)
            AzkarSection.Evening -> DayTime.of(hour = 2, minute = 0)
        }

        private fun key(section: AzkarSection) = intPreferencesKey("azkar.reset.${section.key}")
    }
}
