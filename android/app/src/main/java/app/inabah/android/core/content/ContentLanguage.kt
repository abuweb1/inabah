package app.inabah.android.core.content

import java.util.Locale

/** Язык перевода контента — код ISO 639-1 в нижнем регистре (`"RU"` → `"ru"`). */
@JvmInline
value class ContentLanguage private constructor(val code: String) {
    companion object {
        /** Базовый язык: перевод на него обязателен у каждой записи. */
        val Base = ContentLanguage("ru")

        fun of(code: String): ContentLanguage = ContentLanguage(code.lowercase(Locale.ROOT))
    }
}

/** Выбранный перевод записи и его язык. */
data class LanguageSelection<out T>(val language: ContentLanguage, val translation: T)

/**
 * Порядок языков для выбора перевода (iOS `ContentLanguagePriority`):
 * языки системы по порядку, затем базовый — без повторов; базовый в конце, если его не было раньше.
 */
class ContentLanguagePriority(languages: List<ContentLanguage>) {
    val languages: List<ContentLanguage> = (languages + ContentLanguage.Base).distinct()

    /** Первый язык из приоритета, на который запись переведена; `null` — подходящего нет. */
    fun <T> select(translations: Map<ContentLanguage, T>): LanguageSelection<T>? =
        languages.firstNotNullOfOrNull { language ->
            translations[language]?.let { LanguageSelection(language, it) }
        }

    companion object {
        /**
         * Из тегов локалей устройства (`en-US` → `en`); неопределённый язык пропускается.
         * Код — из нормализованного тега, а не `Locale.language`: тот на старых Android отдаёт
         * устаревшие коды (`iw` вместо `he`, `in` вместо `id`).
         */
        fun fromLanguageTags(tags: List<String>): ContentLanguagePriority =
            ContentLanguagePriority(
                tags.map { Locale.forLanguageTag(it).toLanguageTag().substringBefore('-') }
                    .filter { it.isNotEmpty() && it != UNDETERMINED }
                    .map(ContentLanguage::of),
            )

        private const val UNDETERMINED = "und"
    }
}
