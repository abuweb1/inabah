package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.designsystem.ThemeStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Выбранная палитра (iOS `AppearanceSettings`); неизвестное сохранённое значение — [ThemeStyle.Sections]. */
class AppearanceSettings(private val storage: PreferencesStorage) {
    private val _style = MutableStateFlow(
        storage.snapshot[KEY]?.let(ThemeStyle::fromKey) ?: ThemeStyle.Sections,
    )
    val style: StateFlow<ThemeStyle> = _style.asStateFlow()

    /** Выбор той же палитры ничего не делает: ни записи, ни нового значения. */
    fun select(style: ThemeStyle) {
        if (style == _style.value) return
        _style.value = style
        storage.edit { it[KEY] = style.key }
    }

    private companion object {
        val KEY = stringPreferencesKey("appearance.themeStyle")
    }
}
