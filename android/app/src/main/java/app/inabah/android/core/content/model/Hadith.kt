package app.inabah.android.core.content.model

import app.inabah.android.core.content.ContentLanguage
import kotlinx.serialization.Serializable

/** Сборник хадисов; `key` — имя файла в `data/` и часть ключей отметок. */
enum class HadithCollection(val key: String) {
    Nawawi("nawawi"),
    Qudsi("qudsi"),
    Ajurri("ajurri");

    companion object {
        fun fromKey(key: String): HadithCollection? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
data class HadithId(val collection: HadithCollection, val number: Int)

data class Hadith(
    val id: HadithId,
    /** Строки иснада и текста разделены `\n` (строки печатного издания). */
    val arabic: String,
    val translation: HadithTranslation?,
) {
    val number: Int get() = id.number
}

data class HadithTranslation(
    val language: ContentLanguage,
    val narrator: String?,
    val text: String,
    val source: String?,
)
