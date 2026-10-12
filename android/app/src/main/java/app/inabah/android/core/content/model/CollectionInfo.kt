package app.inabah.android.core.content.model

import app.inabah.android.core.content.ContentLanguage

/**
 * Сведения о сборнике для экрана «О сборнике» (`data/collections.json`, docs/06-data-structure.md):
 * уже на выбранном языке. Названия сборника — в приложении, число хадисов — длина его файла.
 * Пусто, если сведений на доступных языках нет.
 */
data class CollectionInfo(
    /** Составители; заготовка с пустым именем сюда не попадает. */
    val authors: List<CollectionAuthor>,
    /** Абзацы «О книге», без пустых. */
    val about: List<String>,
    /** Язык выбранного перевода — для переносов в абзацах; `null` — сведений нет. */
    val language: ContentLanguage? = null,
) {
    companion object {
        val Empty = CollectionInfo(authors = emptyList(), about = emptyList())
    }
}

data class CollectionAuthor(
    val name: String,
    /** «631–676 гг. х.»; `null` — годы не указаны. */
    val years: String?,
)
