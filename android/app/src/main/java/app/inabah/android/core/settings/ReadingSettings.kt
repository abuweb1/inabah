package app.inabah.android.core.settings

import androidx.datastore.preferences.core.doublePreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Кегль арабского текста (iOS `ReadingSettings`): 14…40, шаг 2, по умолчанию 21. */
class ReadingSettings(private val storage: PreferencesStorage) {
    private val _arabicFontSize = MutableStateFlow(
        (storage.snapshot[KEY] ?: DEFAULT_SIZE).coerceIn(MIN_SIZE, MAX_SIZE),
    )
    val arabicFontSize: StateFlow<Double> = _arabicFontSize.asStateFlow()

    val canIncreaseArabicFontSize: Boolean get() = _arabicFontSize.value < MAX_SIZE
    val canDecreaseArabicFontSize: Boolean get() = _arabicFontSize.value > MIN_SIZE

    fun increaseArabicFontSize() = setArabicFontSize(_arabicFontSize.value + STEP)

    fun decreaseArabicFontSize() = setArabicFontSize(_arabicFontSize.value - STEP)

    private fun setArabicFontSize(size: Double) {
        val clamped = size.coerceIn(MIN_SIZE, MAX_SIZE)
        if (clamped == _arabicFontSize.value) return
        _arabicFontSize.value = clamped
        storage.edit { it[KEY] = clamped }
    }

    companion object {
        const val DEFAULT_SIZE = 21.0
        const val MIN_SIZE = 14.0
        const val MAX_SIZE = 40.0
        const val STEP = 2.0

        private val KEY = doublePreferencesKey("reading.arabicFontSize")
    }
}
