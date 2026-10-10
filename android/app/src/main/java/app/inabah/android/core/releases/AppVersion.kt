package app.inabah.android.core.releases

/**
 * Номер версии `X.Y.Z` (`versionName`, `data/versions.json`). Сравнивается и равен по числам: `1.10.0` > `1.9.0`,
 * `1.01.0` = `1.1.0`, не как строка. [text] — как записано, для показа.
 */
class AppVersion private constructor(val text: String, private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        parts.zip(other.parts).firstNotNullOfOrNull { (a, b) -> (a - b).takeIf { it != 0 } } ?: 0

    override fun equals(other: Any?): Boolean = other is AppVersion && parts == other.parts

    override fun hashCode(): Int = parts.hashCode()

    override fun toString(): String = text

    companion object {
        private val FORMAT = Regex("""\d+\.\d+\.\d+""")

        /** Не `X.Y.Z` (или часть не помещается в `Int`) — `null`. */
        fun parse(text: String?): AppVersion? {
            if (text == null || !FORMAT.matches(text)) return null
            val parts = text.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(text, parts)
        }
    }
}
