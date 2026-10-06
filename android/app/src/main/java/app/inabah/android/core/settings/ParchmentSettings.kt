package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.designsystem.ParchmentStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Выбранный фон под арабским текстом (блок в «Палитре», 2026-10-06; на iOS пока нет).
 * Неизвестное сохранённое значение — [ParchmentStyle.Classic].
 */
class ParchmentSettings(private val storage: PreferencesStorage) {
    private val _style = MutableStateFlow(
        storage.snapshot[KEY]?.let(ParchmentStyle::fromKey) ?: ParchmentStyle.Classic,
    )
    val style: StateFlow<ParchmentStyle> = _style.asStateFlow()

    /** Выбор того же фона ничего не делает: ни записи, ни нового значения. */
    fun select(style: ParchmentStyle) {
        if (style == _style.value) return
        _style.value = style
        storage.edit { it[KEY] = style.key }
    }

    private companion object {
        val KEY = stringPreferencesKey("appearance.parchment")
    }
}
