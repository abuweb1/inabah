package app.inabah.android.core.designsystem

/**
 * Стиль оформления (iOS `ThemeStyle`): свой цвет у каждого раздела или единая палитра.
 * `key` — значение в настройках. Палитры стилей — этап 2 (docs/android/05-design-system.md).
 */
enum class ThemeStyle(val key: String) {
    Sections("sections"),
    Violet("violet"),
    Emerald("emerald"),
    Amber("amber"),
    Graphite("graphite");

    companion object {
        fun fromKey(key: String): ThemeStyle? = entries.firstOrNull { it.key == key }
    }
}
