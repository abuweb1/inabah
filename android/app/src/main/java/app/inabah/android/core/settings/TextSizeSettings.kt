package app.inabah.android.core.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Шаг «Размера переводов» (iOS `ContentTextSize`): множитель базы 17 / 15 / 13 (docs/android/10-typography.md, 10.2). */
enum class ContentTextSize(val key: String, val scale: Float) {
    // «Мельче» — мельче iOS (0,9 и 0,94): на телефоне пользователя этого было мало (2026-10-04).
    Smaller("smaller", 0.8f),
    Standard("standard", 1f),
    Larger("larger", 1.15f),
    Large("large", 1.3f),
    Largest("largest", 1.5f),
}

/**
 * Шаг «Размера интерфейса» (iOS `InterfaceTextSize` = Dynamic Type `.medium / .large / .xLarge`):
 * на Android — `fontScale` содержимого экранов вкладок, body 16 / 17 / 19.
 */
enum class InterfaceTextSize(val key: String, val fontScale: Float) {
    Smaller("smaller", 0.88f),
    Standard("standard", 1f),
    Larger("larger", 1.12f),
}

/**
 * Размер текста (iOS `TextSizeSettings`): переводы и интерфейс, системный размер шрифта не влияет.
 * Неизвестное сохранённое значение (в том числе прежнее iOS `largest` у интерфейса) — «Обычный»;
 * выбор того же шага ничего не делает — от шага интерфейса зависит всё приложение.
 */
class TextSizeSettings(private val storage: PreferencesStorage) {
    private val _content = MutableStateFlow(read(CONTENT_KEY, ContentTextSize.entries, ContentTextSize::key, ContentTextSize.Standard))
    val content: StateFlow<ContentTextSize> = _content.asStateFlow()

    private val _interface = MutableStateFlow(
        read(INTERFACE_KEY, InterfaceTextSize.entries, InterfaceTextSize::key, InterfaceTextSize.Standard),
    )
    val interfaceSize: StateFlow<InterfaceTextSize> = _interface.asStateFlow()

    fun select(size: ContentTextSize) {
        if (size == _content.value) return
        _content.value = size
        storage.edit { it[CONTENT_KEY] = size.key }
    }

    fun select(size: InterfaceTextSize) {
        if (size == _interface.value) return
        _interface.value = size
        storage.edit { it[INTERFACE_KEY] = size.key }
    }

    private fun <T> read(key: Preferences.Key<String>, entries: List<T>, keyOf: (T) -> String, default: T): T {
        val stored = storage.snapshot[key] ?: return default
        return entries.firstOrNull { keyOf(it) == stored } ?: default
    }

    private companion object {
        val CONTENT_KEY = stringPreferencesKey("appearance.contentTextSize")
        val INTERFACE_KEY = stringPreferencesKey("appearance.interfaceTextSize")
    }
}
