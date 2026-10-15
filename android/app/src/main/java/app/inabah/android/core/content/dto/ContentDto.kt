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
    /** Необязательное: записей пока нет (2026-10-06), свои подключатся этим полем. */
    val audio: String? = null,
    val translations: Map<String, ZikrTranslationDto>,
)

@Serializable
data class ZikrTranslationDto(
    val text: String,
    val translit: String? = null,
    val source: String? = null,
    /** Передатчик, как у хадисов; необязательное (2026-10-10 — пользователь заполнит позже). */
    val rawi: String? = null,
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
    /** Сноски к словам перевода (docs/06-data-structure.md); `[]` — сносок пока нет. */
    val notes: List<HadithNoteDto> = emptyList(),
)

@Serializable
data class HadithNoteDto(
    val term: String = "",
    val text: String = "",
)

// data/collections.json: { <ключ сборника>: CollectionInfoDto }.

@Serializable
data class CollectionInfoDto(
    val translations: Map<String, CollectionTranslationDto> = emptyMap(),
)

@Serializable
data class CollectionTranslationDto(
    val authors: List<CollectionAuthorDto> = emptyList(),
    val about: List<String> = emptyList(),
)

/** `{ "name": "", "years": "" }` — заготовка «составитель не указан» (у своей подборки). */
@Serializable
data class CollectionAuthorDto(
    val name: String = "",
    val years: String? = null,
)

// data/reading.json - «Учимся читать» (docs/06-data-structure.md). Неизвестные типы блоков
// и вопросов не роняют разбор: блок - [ReadingBlockDto.type], поля по типу необязательные.

@Serializable
data class ReadingFileDto(
    val marks: List<ReadingMarkDto>,
    val letters: List<ReadingLetterDto>,
    val steps: List<ReadingStepDto>,
)

@Serializable
data class ReadingMarkDto(
    val id: String,
    val sign: String,
    val translations: Map<String, ReadingMarkTranslationDto>,
)

@Serializable
data class ReadingMarkTranslationDto(
    val name: String,
    val where: String = "",
    val sound: String = "",
)

@Serializable
data class ReadingLetterDto(
    val id: Int,
    val letter: String,
    val name: String,
    val joinsNext: Boolean = true,
    /** `false` - не соединяется и с предыдущей (хамза). */
    val joinsPrevious: Boolean = true,
    /** Подставка слога по знакам: у алифа - хамза на алифе (`kasra` → `إ`); нет знака - сама буква. */
    val syllableBases: Map<String, String> = emptyMap(),
    /** `{ sound, name, <знак> }` → имя файла или `null`; пустой объект - записей нет. */
    val audio: Map<String, String?> = emptyMap(),
    val translations: Map<String, ReadingLetterTranslationDto>,
)

@Serializable
data class ReadingLetterTranslationDto(
    val name: String,
    val nameTranslit: String = "",
    val sound: String = "",
    val syllables: Map<String, String> = emptyMap(),
    /** Пояснение в листе буквы (у алифа); нет - `null`. */
    val note: String? = null,
)

@Serializable
data class ReadingStepDto(
    val id: Int,
    val marks: List<String> = emptyList(),
    val example: String? = null,
    val translations: Map<String, ReadingStepTranslationDto>,
    val blocks: List<ReadingBlockDto> = emptyList(),
    val quiz: List<ReadingQuizDto> = emptyList(),
    /** «Читаем слова»; нет - у шага слов нет. */
    val words: ReadingWordsDto? = null,
    /** Итог курса («Читаем зикр») - последний шаг, без номера. */
    val final: Boolean = false,
)

@Serializable
data class ReadingWordsDto(
    val groupSize: Int,
    val repeat: Int,
    val items: List<ReadingWordDto>,
)

@Serializable
data class ReadingWordDto(
    val arabic: String,
    /** Транскрипция - только у первой группы. */
    val translations: Map<String, ReadingWordTranslationDto> = emptyMap(),
)

@Serializable
data class ReadingWordTranslationDto(
    val translit: String = "",
)

@Serializable
data class ReadingStepTranslationDto(
    val title: String,
    val subtitle: String = "",
)

@Serializable
data class ReadingBlockDto(
    val type: String,
    val marks: List<String> = emptyList(),
    /** `letters`, `syllables`: подмножество алфавита, `null` - все буквы; `rule`: буквы правила. */
    val letters: List<String>? = null,
    /** `rule`: арабское название правила. */
    val arabic: String? = null,
    /** `syllables`: на уроке - столько случайных слогов, весь алфавит - отдельным экраном. */
    val sample: Int? = null,
    /** `forms`: буквы карточками (группы похожего написания). */
    val groups: List<List<String>>? = null,
    /** `forms`: буква → 3 слова, где она в начале, в середине и в конце. */
    val examples: Map<String, List<String>> = emptyMap(),
    /** `forms`: буквы третьей карточки «в словах». */
    val exampleCard: List<String> = emptyList(),
    val translations: Map<String, ReadingTextTranslationDto> = emptyMap(),
)

@Serializable
data class ReadingTextTranslationDto(
    val text: String = "",
    /** `rule`: транскрипция названия правила. */
    val translit: String = "",
    /** `text`: полный текст во всплывающем окне по ⓘ. */
    val info: String = "",
)

@Serializable
data class ReadingQuizDto(
    val type: String,
    val letter: String,
    val mark: String? = null,
    /** `letterForm`: isolated, initial, medial, final. */
    val form: String? = null,
)
