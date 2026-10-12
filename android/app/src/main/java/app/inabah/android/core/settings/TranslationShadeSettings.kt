package app.inabah.android.core.settings

import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сдвиг фона перевода — ползунок в «Палитре» (2026-10-10; на iOS пока нет): от −1 (темнее) до +1 (светлее),
 * 0 — как задумано. Цвета — `Theme.withTranslationShade`. Неизвестное сохранённое значение — 0, вне диапазона — край.
 */
class TranslationShadeSettings(private val storage: PreferencesStorage) {
    private val _shade = MutableStateFlow(normalized(storage.snapshot[KEY] ?: DEFAULT))
    val shade: StateFlow<Float> = _shade.asStateFlow()

    /** То же значение ничего не делает: ни записи, ни нового значения. */
    fun set(value: Float) {
        val shade = normalized(value)
        if (shade == _shade.value) return
        _shade.value = shade
        storage.edit { it[KEY] = shade }
    }

    companion object {
        const val DEFAULT = 0f
        private val KEY = floatPreferencesKey("appearance.translationShade")

        private fun normalized(value: Float): Float = if (value.isNaN()) DEFAULT else value.coerceIn(-1f, 1f)
    }
}
