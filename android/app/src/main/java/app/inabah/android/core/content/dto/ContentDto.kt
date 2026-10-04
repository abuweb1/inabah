package app.inabah.android.core.content.dto

import kotlinx.serialization.Serializable

// Формат data/<файл>.json (docs/06-data-structure.md). Неизвестные поля игнорирует Json
// репозитория, чтобы будущие поля данных не ломали старые версии.
// Map из kotlinx.serialization сохраняет порядок ключей JSON.

@Serializable
data class AzkarFileDto(
    val morning: List<ZikrDto>,
    val evening: List<ZikrDto>,
)

@Serializable
data class ZikrDto(
    val id: Int,
    val arabic: String,
    val max: Int,
    val audio: String,
    val translations: Map<String, ZikrTranslationDto>,
)

@Serializable
data class ZikrTranslationDto(
    val text: String,
    val translit: String? = null,
    val source: String? = null,
)

@Serializable
data class HadithDto(
    val id: Int,
    val arabic: String,
    val translations: Map<String, HadithTranslationDto>,
)

@Serializable
data class HadithTranslationDto(
    val text: String,
    val rawi: String? = null,
    val source: String? = null,
)
