package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Настройки «Прослушать все» (iOS `PlaylistSettings`). Пауза и скорость — только из допустимых
 * значений; недопустимое сохранённое — значение по умолчанию.
 */
class PlaylistSettings(private val storage: PreferencesStorage) {
    private val _repeatsByCount = MutableStateFlow(storage.snapshot[REPEATS_KEY] ?: DEFAULT_REPEATS_BY_COUNT)
    private val _pauseBetween = MutableStateFlow(
        storage.snapshot[PAUSE_KEY]?.takeIf { it in PAUSE_OPTIONS } ?: DEFAULT_PAUSE,
    )
    private val _rate = MutableStateFlow(storage.snapshot[RATE_KEY]?.takeIf { it in RATE_OPTIONS } ?: DEFAULT_RATE)

    /** Повторять каждый зикр столько раз, сколько его читают. */
    val repeatsByCount: StateFlow<Boolean> = _repeatsByCount.asStateFlow()

    /** Пауза между зикрами, секунды. */
    val pauseBetween: StateFlow<Double> = _pauseBetween.asStateFlow()

    val rate: StateFlow<Float> = _rate.asStateFlow()

    fun setRepeatsByCount(value: Boolean) {
        if (value == _repeatsByCount.value) return
        _repeatsByCount.value = value
        storage.edit { it[REPEATS_KEY] = value }
    }

    fun setPauseBetween(seconds: Double) {
        require(seconds in PAUSE_OPTIONS) { "Пауза $seconds не из $PAUSE_OPTIONS" }
        if (seconds == _pauseBetween.value) return
        _pauseBetween.value = seconds
        storage.edit { it[PAUSE_KEY] = seconds }
    }

    fun setRate(value: Float) {
        require(value in RATE_OPTIONS) { "Скорость $value не из $RATE_OPTIONS" }
        if (value == _rate.value) return
        _rate.value = value
        storage.edit { it[RATE_KEY] = value }
    }

    companion object {
        const val DEFAULT_REPEATS_BY_COUNT = true
        const val DEFAULT_PAUSE = 1.0
        const val DEFAULT_RATE = 1f
        val PAUSE_OPTIONS = listOf(0.0, 1.0, 3.0, 5.0)
        val RATE_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f)

        private val REPEATS_KEY = booleanPreferencesKey("playlist.repeatsByCount")
        private val PAUSE_KEY = doublePreferencesKey("playlist.pauseBetween")
        private val RATE_KEY = floatPreferencesKey("playlist.rate")
    }
}
