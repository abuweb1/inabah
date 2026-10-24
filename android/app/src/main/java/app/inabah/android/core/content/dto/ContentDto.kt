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
    /** `true` — не входит в набор по умолчанию («Состав азкаров», 2026-10-24); нет поля — входит. */
    val optional: Boolean = false,
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
    /** Руководства по произношению (2026-10-21): экран для букв [ReadingGuideDto.letters], открывается из разных мест курса. */
    val guides: List<ReadingGuideDto> = emptyList(),
)

@Serializable
data class ReadingGuideDto(
    val id: String,
    /** Буквы руководства: на сетке шага 1 и в таблицах форм помечены, в листе и карточке буквы - переход. */
    val letters: List<String>,
    /** `title` - название (кнопка, навбар). */
    val translations: Map<String, ReadingTextTranslationDto>,
    val blocks: List<ReadingBlockDto> = emptyList(),
)

/** `image`: строка легенды под рисунком - цвет метки на рисунке и что она значит. */
@Serializable
data class ReadingLegendDto(
    /** `#RRGGBB` - цвет метки самого рисунка, не темы. */
    val color: String,
    val translations: Map<String, ReadingTextTranslationDto>,
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
    /** Буква с сукуном в закрытом слоге, если не сама буква: у алифа и хамзы - `أ` (`إِأْ`). */
    val closedBase: String? = null,
    /** После каких огласовок у буквы бывает закрытый слог (у و ي - только `fatha`: أُوْ, إِيْ - мадд); нет - после всех. */
    val closedVowels: List<String>? = null,
    /** `{ name, <знак>, closedFatha, closedKasra, closedDamma }` → имя файла или `null`; пустой объект - записей нет. */
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
    /** Вопросов в одном заходе (экзамен - 20); нет - [app.inabah.android.core.content.model.ReadingStep.QUIZ_ROUND]. */
    val quizCount: Int? = null,
    /** «Читаем слова»; нет - у шага слов нет. */
    val words: ReadingWordsDto? = null,
    /** Итог курса («Читаем зикр») - последний шаг, без номера. */
    val final: Boolean = false,
    /** Справка не для чтения (солнечные и лунные буквы) - отдельным экраном по кнопке; нет - без неё. */
    val extra: ReadingExtraDto? = null,
)

@Serializable
data class ReadingExtraDto(
    val translations: Map<String, ReadingStepTranslationDto>,
    val blocks: List<ReadingBlockDto> = emptyList(),
)

@Serializable
data class ReadingWordsDto(
    val groupSize: Int,
    val repeat: Int,
    val items: List<ReadingWordDto>,
    /** Пары «слово → словосочетание с ним» - в порядке данных, рядом (шаг «Мадд перед артиклем»). */
    val pairs: Boolean = false,
)

@Serializable
data class ReadingWordDto(
    val arabic: String,
    /** Транскрипция - только у первой группы. */
    val translations: Map<String, ReadingWordTranslationDto> = emptyMap(),
)

@Serializable
data class ReadingFormulaDto(
    val parts: List<String>,
    val result: String,
    /** Транскрипция итога; нет - без подписи. */
    val translations: Map<String, ReadingWordTranslationDto> = emptyMap(),
    /** Запись итога (примеры шадды, 2026-10-20); `null` - записи нет (ещё не записана или не нужна). */
    val audio: String? = null,
)

@Serializable
data class ReadingWordTranslationDto(
    val translit: String = "",
    /** Место слитного чтения зикра: почему читается так. */
    val note: String = "",
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
    /** `syllables`, `formulas`: на уроке - столько случайных слогов (формул), все - отдельным экраном. */
    val sample: Int? = null,
    /** `forms`: буквы карточками (группы похожего написания). */
    val groups: List<List<String>>? = null,
    /** `forms`: буква → 3 слова, где она в начале, в середине и в конце. */
    val examples: Map<String, List<String>> = emptyMap(),
    /**
     * `forms`: буква не из [groups] (хӏамза, 2026-10-21) → 4 строки написаний - отдельно, в начале, в середине,
     * в конце; плитка во всю строку после последней группы.
     */
    val variants: Map<String, List<String>> = emptyMap(),
    /** `forms`: буквы третьей карточки «в словах». */
    val exampleCard: List<String> = emptyList(),
    /** `formulas`: формул в ряду (1…3). */
    val columns: Int = 1,
    /** `formulas`: «بَ + ا = بَا», «تَشَٰبَهَ = تَشَابَهَ». */
    val items: List<ReadingFormulaDto> = emptyList(),
    /** `topic`: содержимое своего экрана - блоки, как в справке. */
    val blocks: List<ReadingBlockDto> = emptyList(),
    /** `zikr`: раздел и номер зикра в «Азкарах», его запись (голое имя файла). */
    val section: String? = null,
    val id: Int? = null,
    val audio: String? = null,
    /** `zikr`: слова по порядку и места слитного чтения (слова подряд, транскрипция и подпись). */
    val words: List<ReadingWordDto> = emptyList(),
    val joins: List<ReadingWordDto> = emptyList(),
    /** `image`: имя рисунка в `images/reading/` без расширения. */
    val image: String? = null,
    /** `image`: легенда меток рисунка. */
    val legend: List<ReadingLegendDto> = emptyList(),
    val translations: Map<String, ReadingTextTranslationDto> = emptyMap(),
)

@Serializable
data class ReadingTextTranslationDto(
    val text: String = "",
    /** `rule`: транскрипция названия правила. */
    val translit: String = "",
    /** `text`: полный текст во всплывающем окне по ⓘ. */
    val info: String = "",
    /** `topic`: название (на карточке и в навбаре экрана темы); `text` - краткое описание. `text`, `image`: заголовок блока. */
    val title: String = "",
    /** `forms`: буква → транскрипция её 3 слов-примеров (по порядку `examples`). */
    val examples: Map<String, List<String>> = emptyMap(),
    /** `forms`: буква → текст на её карточке (хӏамза: подставка не читается). */
    val notes: Map<String, ReadingNoteDto> = emptyMap(),
)

@Serializable
data class ReadingNoteDto(
    val text: String = "",
    /** Полный текст во всплывающем окне по ⓘ. */
    val info: String = "",
)

@Serializable
data class ReadingQuizDto(
    val type: String,
    /** У `word` буквы нет. */
    val letter: String = "",
    val mark: String? = null,
    /** `letterForm`: isolated, initial, medial, final. */
    val form: String? = null,
    /** `word`: слово вопроса. */
    val arabic: String? = null,
    /** `word`: номер верного варианта в `translations.<язык>.options`. */
    val answer: Int = 0,
    val translations: Map<String, ReadingQuizTranslationDto> = emptyMap(),
)

/** `word`: варианты транскрипции (в показанном порядке) и пояснение к ответу. */
@Serializable
data class ReadingQuizTranslationDto(
    val options: List<String> = emptyList(),
    val explain: String = "",
    /** Свой вопрос («Как читается на паузе?»); нет - «Как читается это слово?». */
    val question: String = "",
)
