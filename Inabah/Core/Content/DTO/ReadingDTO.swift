import Foundation

// «Учимся читать» - `data/reading.json` один в один (docs/06-data-structure.md, «Учимся читать»; Android
// `ReadingFileDto`). Поля без значения по умолчанию обязательны: нет поля - ошибка разбора.

nonisolated struct ReadingFileDTO: Decodable, Sendable {
    let marks: [ReadingMarkDTO]
    let letters: [ReadingLetterDTO]
    let steps: [ReadingStepDTO]
    /// Руководства по произношению (2026-10-21, «Межзубные буквы и дод»).
    let guides: [ReadingGuideDTO]

    private enum CodingKeys: String, CodingKey { case marks, letters, steps, guides }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        marks = try c.decode([ReadingMarkDTO].self, forKey: .marks)
        letters = try c.decode([ReadingLetterDTO].self, forKey: .letters)
        steps = try c.decode([ReadingStepDTO].self, forKey: .steps)
        guides = try c.decode(.guides, default: [])
    }
}

/// Руководство: отдельный экран курса для своих букв (у буквы - не больше одного руководства).
nonisolated struct ReadingGuideDTO: Decodable, Sendable {
    let id: String
    let letters: [String]
    let translations: [String: ReadingTextTranslationDTO]
    let blocks: [ReadingBlockDTO]

    private enum CodingKeys: String, CodingKey { case id, letters, translations, blocks }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        letters = try c.decode([String].self, forKey: .letters)
        translations = try c.decode([String: ReadingTextTranslationDTO].self, forKey: .translations)
        blocks = try c.decode(.blocks, default: [])
    }
}

/// Пункт легенды рисунка: цвет метки на самом рисунке (`#RRGGBB`) и подпись.
nonisolated struct ReadingLegendDTO: Decodable, Sendable {
    let color: String
    let translations: [String: ReadingTextTranslationDTO]
}

nonisolated struct ReadingMarkDTO: Decodable, Sendable {
    let id: String
    let sign: String
    let translations: [String: ReadingMarkTranslationDTO]
}

nonisolated struct ReadingMarkTranslationDTO: Decodable, Sendable {
    let name: String
    let `where`: String
    let sound: String

    private enum CodingKeys: String, CodingKey { case name, `where`, sound }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decode(String.self, forKey: .name)
        `where` = try c.decode(.where, default: "")
        sound = try c.decode(.sound, default: "")
    }
}

nonisolated struct ReadingLetterDTO: Decodable, Sendable {
    let id: Int
    let letter: String
    let name: String
    let joinsNext: Bool
    let joinsPrevious: Bool
    let syllableBases: [String: String]
    /// Буква с сукуном в закрытом слоге, если не сама буква: у алифа и хамзы - `أ` (`إِأْ`).
    let closedBase: String?
    /// После каких огласовок у буквы бывает закрытый слог (у و ي - без мадда); нет - после всех.
    let closedVowels: [String]?
    /// Записи: `name`, `<знак>`, `closedFatha` / `closedKasra` / `closedDamma` → файл; `null` - записи нет.
    let audio: [String: String?]
    let translations: [String: ReadingLetterTranslationDTO]

    private enum CodingKeys: String, CodingKey {
        case id, letter, name, joinsNext, joinsPrevious, syllableBases, closedBase, closedVowels, audio, translations
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        letter = try c.decode(String.self, forKey: .letter)
        name = try c.decode(String.self, forKey: .name)
        joinsNext = try c.decode(.joinsNext, default: true)
        joinsPrevious = try c.decode(.joinsPrevious, default: true)
        syllableBases = try c.decode(.syllableBases, default: [:])
        closedBase = try c.decodeIfPresent(String.self, forKey: .closedBase)
        closedVowels = try c.decodeIfPresent([String].self, forKey: .closedVowels)
        audio = try c.decode(.audio, default: [:])
        translations = try c.decode([String: ReadingLetterTranslationDTO].self, forKey: .translations)
    }
}

nonisolated struct ReadingLetterTranslationDTO: Decodable, Sendable {
    let name: String
    let nameTranslit: String
    let sound: String
    let syllables: [String: String]
    let note: String?

    private enum CodingKeys: String, CodingKey { case name, nameTranslit, sound, syllables, note }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        name = try c.decode(String.self, forKey: .name)
        nameTranslit = try c.decode(.nameTranslit, default: "")
        sound = try c.decode(.sound, default: "")
        syllables = try c.decode(.syllables, default: [:])
        note = try c.decodeIfPresent(String.self, forKey: .note)
    }
}

nonisolated struct ReadingStepDTO: Decodable, Sendable {
    let id: Int
    let marks: [String]
    let example: String?
    let translations: [String: ReadingStepTranslationDTO]
    let blocks: [ReadingBlockDTO]
    let quiz: [ReadingQuizDTO]
    let quizCount: Int?
    let words: ReadingWordsDTO?
    let final: Bool
    let extra: ReadingExtraDTO?

    private enum CodingKeys: String, CodingKey {
        case id, marks, example, translations, blocks, quiz, quizCount, words, final, extra
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        marks = try c.decode(.marks, default: [])
        example = try c.decodeIfPresent(String.self, forKey: .example)
        translations = try c.decode([String: ReadingStepTranslationDTO].self, forKey: .translations)
        blocks = try c.decode(.blocks, default: [])
        quiz = try c.decode(.quiz, default: [])
        quizCount = try c.decodeIfPresent(Int.self, forKey: .quizCount)
        words = try c.decodeIfPresent(ReadingWordsDTO.self, forKey: .words)
        final = try c.decode(.final, default: false)
        extra = try c.decodeIfPresent(ReadingExtraDTO.self, forKey: .extra)
    }
}

nonisolated struct ReadingExtraDTO: Decodable, Sendable {
    let translations: [String: ReadingStepTranslationDTO]
    let blocks: [ReadingBlockDTO]

    private enum CodingKeys: String, CodingKey { case translations, blocks }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        translations = try c.decode([String: ReadingStepTranslationDTO].self, forKey: .translations)
        blocks = try c.decode(.blocks, default: [])
    }
}

nonisolated struct ReadingWordsDTO: Decodable, Sendable {
    let groupSize: Int
    let `repeat`: Int
    let items: [ReadingWordDTO]
    let pairs: Bool

    private enum CodingKeys: String, CodingKey { case groupSize, `repeat`, items, pairs }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        groupSize = try c.decode(Int.self, forKey: .groupSize)
        `repeat` = try c.decode(Int.self, forKey: .repeat)
        items = try c.decode([ReadingWordDTO].self, forKey: .items)
        pairs = try c.decode(.pairs, default: false)
    }
}

nonisolated struct ReadingWordDTO: Decodable, Sendable {
    let arabic: String
    let translations: [String: ReadingWordTranslationDTO]

    private enum CodingKeys: String, CodingKey { case arabic, translations }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        arabic = try c.decode(String.self, forKey: .arabic)
        translations = try c.decode(.translations, default: [:])
    }
}

nonisolated struct ReadingFormulaDTO: Decodable, Sendable {
    let parts: [String]
    let result: String
    let translations: [String: ReadingWordTranslationDTO]
    /// Запись примера (шадда, `reading_s09_NN.m4a`); `null` или нет поля - не записан.
    let audio: String?

    private enum CodingKeys: String, CodingKey { case parts, result, translations, audio }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        parts = try c.decode([String].self, forKey: .parts)
        result = try c.decode(String.self, forKey: .result)
        translations = try c.decode(.translations, default: [:])
        audio = try c.decodeIfPresent(String.self, forKey: .audio)
    }
}

/// Транскрипция слова или формулы; у места слитного чтения зикра - и пояснение.
nonisolated struct ReadingWordTranslationDTO: Decodable, Sendable {
    let translit: String
    let note: String

    private enum CodingKeys: String, CodingKey { case translit, note }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        translit = try c.decode(.translit, default: "")
        note = try c.decode(.note, default: "")
    }
}

nonisolated struct ReadingStepTranslationDTO: Decodable, Sendable {
    let title: String
    let subtitle: String

    private enum CodingKeys: String, CodingKey { case title, subtitle }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try c.decode(String.self, forKey: .title)
        subtitle = try c.decode(.subtitle, default: "")
    }
}

/// Блок урока - один плоский тип на все виды (`type`); лишние для вида поля не заданы.
nonisolated struct ReadingBlockDTO: Decodable, Sendable {
    let type: String
    let marks: [String]
    let letters: [String]?
    let arabic: String?
    let sample: Int?
    let groups: [[String]]?
    /// Буква → 3 слова, где она в начале, в середине и в конце.
    let examples: [String: [String]]
    /// Буква не из `groups` (хӏамза, 2026-10-21) → 4 строки написаний: отдельно, в начале, в середине, в конце.
    let variants: [String: [String]]
    let exampleCard: [String]
    let columns: Int
    let items: [ReadingFormulaDTO]
    let blocks: [ReadingBlockDTO]
    let section: String?
    let id: Int?
    let audio: String?
    let words: [ReadingWordDTO]
    let joins: [ReadingWordDTO]
    /// Рисунок руководства: имя файла `images/reading/<image>.webp` без расширения.
    let image: String?
    let legend: [ReadingLegendDTO]
    let translations: [String: ReadingTextTranslationDTO]

    private enum CodingKeys: String, CodingKey {
        case type, marks, letters, arabic, sample, groups, examples, variants, exampleCard, columns, items, blocks
        case section, id, audio, words, joins, image, legend, translations
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decode(String.self, forKey: .type)
        marks = try c.decode(.marks, default: [])
        letters = try c.decodeIfPresent([String].self, forKey: .letters)
        arabic = try c.decodeIfPresent(String.self, forKey: .arabic)
        sample = try c.decodeIfPresent(Int.self, forKey: .sample)
        groups = try c.decodeIfPresent([[String]].self, forKey: .groups)
        examples = try c.decode(.examples, default: [:])
        variants = try c.decode(.variants, default: [:])
        exampleCard = try c.decode(.exampleCard, default: [])
        columns = try c.decode(.columns, default: 1)
        items = try c.decode(.items, default: [])
        blocks = try c.decode(.blocks, default: [])
        section = try c.decodeIfPresent(String.self, forKey: .section)
        id = try c.decodeIfPresent(Int.self, forKey: .id)
        audio = try c.decodeIfPresent(String.self, forKey: .audio)
        words = try c.decode(.words, default: [])
        joins = try c.decode(.joins, default: [])
        image = try c.decodeIfPresent(String.self, forKey: .image)
        legend = try c.decode(.legend, default: [])
        translations = try c.decode(.translations, default: [:])
    }
}

nonisolated struct ReadingTextTranslationDTO: Decodable, Sendable {
    let text: String
    let translit: String
    let info: String
    let title: String
    /// Транскрипция слов-примеров формы: буква → 3 строки.
    let examples: [String: [String]]
    /// Таблица форм: буква → текст на её карточке (хӏамза: подставка не читается).
    let notes: [String: ReadingNoteDTO]

    private enum CodingKeys: String, CodingKey { case text, translit, info, title, examples, notes }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        text = try c.decode(.text, default: "")
        translit = try c.decode(.translit, default: "")
        info = try c.decode(.info, default: "")
        title = try c.decode(.title, default: "")
        examples = try c.decode(.examples, default: [:])
        notes = try c.decode(.notes, default: [:])
    }
}

/// Текст на карточке буквы; `info` - полный текст во всплывающем окне по ⓘ.
nonisolated struct ReadingNoteDTO: Decodable, Sendable {
    let text: String
    let info: String

    private enum CodingKeys: String, CodingKey { case text, info }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        text = try c.decode(.text, default: "")
        info = try c.decode(.info, default: "")
    }
}

nonisolated struct ReadingQuizDTO: Decodable, Sendable {
    let type: String
    let letter: String
    let mark: String?
    let form: String?
    let arabic: String?
    let answer: Int
    let translations: [String: ReadingQuizTranslationDTO]

    private enum CodingKeys: String, CodingKey { case type, letter, mark, form, arabic, answer, translations }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        type = try c.decode(String.self, forKey: .type)
        letter = try c.decode(.letter, default: "")
        mark = try c.decodeIfPresent(String.self, forKey: .mark)
        form = try c.decodeIfPresent(String.self, forKey: .form)
        arabic = try c.decodeIfPresent(String.self, forKey: .arabic)
        answer = try c.decode(.answer, default: 0)
        translations = try c.decode(.translations, default: [:])
    }
}

nonisolated struct ReadingQuizTranslationDTO: Decodable, Sendable {
    let options: [String]
    let explain: String
    let question: String

    private enum CodingKeys: String, CodingKey { case options, explain, question }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        options = try c.decode(.options, default: [])
        explain = try c.decode(.explain, default: "")
        question = try c.decode(.question, default: "")
    }
}

private nonisolated extension KeyedDecodingContainer {
    /// Необязательное поле со значением по умолчанию (как в Kotlin DTO); `null` - тоже ошибка, как там.
    func decode<T: Decodable>(_ key: Key, default value: T) throws -> T {
        contains(key) ? try decode(T.self, forKey: key) : value
    }
}
