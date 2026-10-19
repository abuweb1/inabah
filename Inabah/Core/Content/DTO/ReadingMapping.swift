import Foundation

// reading.json → курс на выбранном языке (как Android `ReadingMapping.kt`). Ссылки на неизвестные знак
// или букву, запись без перевода - ошибка данных (`ReadingMappingError`; их ловит check-data, а
// репозиторий превращает в `ContentError.decodingFailed`). Неизвестные типы блоков и вопросов
// пропускаются - их добавят будущие версии данных.

/// Ошибка данных курса: что именно не так (для журнала и `ContentError.decodingFailed`).
nonisolated struct ReadingMappingError: Error, Equatable, CustomStringConvertible {
    let description: String
}

nonisolated extension ReadingCourse {
    private static let exampleLetter = "ب"
    private static let alif = "ا"
    private static let formExamples = 3
    private static let maxFormulaColumns = 3
    /// Места слов-примеров по порядку `examples`.
    private static let placedForms: [LetterForm] = [.initial, .medial, .final]

    init(dto: ReadingFileDTO, languages: ContentLanguagePriority) throws {
        let context = try Context(dto: dto, languages: languages)
        var learned: Set<String> = []
        // Карточки форм пройденных шагов: свой шаг - первым (экзамен шага 12 спрашивает формы прошлых шагов).
        var learnedFormGroups: [[String]] = []
        var steps: [ReadingStep] = []
        for step in dto.steps {
            for id in step.marks { _ = try context.mark(id) }
            learned.formUnion(step.marks)
            learnedFormGroups.insert(contentsOf: step.blocks.filter { $0.type == "forms" }.flatMap { $0.groups ?? [] }, at: 0)
            let learnedMarks = context.marks.filter { learned.contains($0.id) }
            let text = try context.require(step.translations, "шаг \(step.id)")
            let groups = learnedFormGroups
            steps.append(ReadingStep(
                number: step.id,
                markIds: step.marks,
                example: step.example,
                title: text.title,
                subtitle: text.subtitle,
                blocks: try step.blocks.compactMap { try context.block($0, step: step, learnedMarks: learnedMarks) },
                quiz: try step.quiz.compactMap { quiz in
                    // Варианты «буквы по форме» - буквы её карточки в таблице форм этого или прошлого шага.
                    try context.question(quiz) { character in
                        try (groups.first { $0.contains(character) } ?? []).map(context.letter)
                    }
                },
                words: step.words.map { words in
                    ReadingWords(
                        groupSize: words.groupSize,
                        repeat: words.repeat,
                        items: words.items.map { ReadingWord(arabic: $0.arabic, transliteration: context.transliteration($0.translations)) },
                        pairs: words.pairs
                    )
                },
                isFinal: step.final,
                extra: try step.extra.map { extra in
                    ReadingExtra(
                        title: try context.require(extra.translations, "шаг \(step.id), справка").title,
                        blocks: try extra.blocks.compactMap { try context.block($0, step: step, learnedMarks: learnedMarks) }
                    )
                },
                quizCount: step.quizCount ?? ReadingStep.quizRound
            ))
        }
        self.init(marks: context.marks, letters: context.letters, steps: steps)
    }

    /// Знаки и буквы курса и поиск по ним - общие для всех шагов.
    private struct Context {
        let languages: ContentLanguagePriority
        let marks: [ReadingMark]
        let letters: [ReadingLetter]
        let markByID: [String: ReadingMark]
        let letterByCharacter: [String: ReadingLetter]

        init(dto: ReadingFileDTO, languages: ContentLanguagePriority) throws {
            self.languages = languages
            var marks: [ReadingMark] = []
            for mark in dto.marks {
                let text = try Self.require(mark.translations, "знак \(mark.id)", languages)
                marks.append(ReadingMark(id: mark.id, sign: mark.sign, name: text.name, where: text.where, sound: text.sound))
            }
            var letters: [ReadingLetter] = []
            for letter in dto.letters {
                let text = try Self.require(letter.translations, "буква \(letter.letter)", languages)
                letters.append(ReadingLetter(
                    number: letter.id,
                    letter: letter.letter,
                    arabicName: letter.name,
                    joinsNext: letter.joinsNext,
                    joinsPrevious: letter.joinsPrevious,
                    syllableBases: letter.syllableBases,
                    name: text.name,
                    nameTransliteration: text.nameTranslit,
                    sound: text.sound,
                    syllables: text.syllables,
                    audio: letter.audio.compactMapValues { $0 },
                    note: text.note.nonEmpty
                ))
            }
            self.marks = marks
            self.letters = letters
            markByID = Dictionary(marks.map { ($0.id, $0) }, uniquingKeysWith: { _, last in last })
            letterByCharacter = Dictionary(letters.map { ($0.letter, $0) }, uniquingKeysWith: { _, last in last })
        }

        func mark(_ id: String) throws -> ReadingMark {
            guard let mark = markByID[id] else { throw ReadingMappingError(description: "reading.json: неизвестный знак \(id)") }
            return mark
        }

        func letter(_ character: String) throws -> ReadingLetter {
            guard let letter = letterByCharacter[character] else {
                throw ReadingMappingError(description: "reading.json: неизвестная буква \(character)")
            }
            return letter
        }

        func require<T>(_ translations: [String: T], _ what: String) throws -> T {
            try Self.require(translations, what, languages)
        }

        static func require<T>(_ translations: [String: T], _ what: String, _ languages: ContentLanguagePriority) throws -> T {
            guard let selected = languages.select(from: translations.keyedByLanguage) else {
                throw ReadingMappingError(description: "reading.json: нет перевода - \(what)")
            }
            return selected.translation
        }

        /// Транскрипция слова или формулы: необязательна, пустая - `nil`.
        func transliteration(_ translations: [String: ReadingWordTranslationDTO]) -> String? {
            languages.select(from: translations.keyedByLanguage).flatMap { Optional($0.translation.translit).nonEmpty }
        }

        func block(_ block: ReadingBlockDTO, step: ReadingStepDTO, learnedMarks: [ReadingMark]) throws -> ReadingBlock? {
            switch block.type {
            case "text":
                let text = try require(block.translations, "шаг \(step.id), текст")
                return .text(.init(text: text.text, info: Optional(text.info).nonEmpty))
            case "forms":
                return .forms(try forms(block))
            case "marks":
                // Знаки - на первой букве таблицы слогов шага без подставки (у алифа знак - на хамзе), иначе на «ب».
                let tableLetters = step.blocks.first { $0.type == "syllables" }?.letters ?? []
                var example: ReadingLetter?
                for character in tableLetters {
                    let letter = try letter(character)
                    if letter.syllableBases.isEmpty { example = letter; break }
                }
                return .marks(.init(marks: try block.marks.map(mark), example: try example ?? letter(ReadingCourse.exampleLetter)))
            case "letters":
                return .letters(.init(letters: try block.letters?.map(letter) ?? letters))
            case "syllables":
                // Без букв - весь алфавит. Столбцы - знаки блока (танвин) или пройденные знаки; только те, у
                // которых у всех букв есть транскрипция слога (сукун и шадда - нет).
                let rows = try block.letters?.map(letter) ?? letters
                let columns = block.marks.isEmpty ? learnedMarks : try block.marks.map(mark)
                let filtered = columns.filter { mark in rows.allSatisfy { $0.syllables[mark.id] != nil } }
                return .syllables(.init(letters: rows, marks: filtered, sample: block.sample))
            case "rule":
                let text = try require(block.translations, "шаг \(step.id), правило")
                guard let arabic = block.arabic else { throw ReadingMappingError(description: "reading.json: правило без arabic") }
                return .rule(.init(arabic: arabic, transliteration: text.translit, text: text.text, letters: try (block.letters ?? []).map(letter)))
            case "topic":
                let text = try require(block.translations, "шаг \(step.id), тема")
                let blocks = try block.blocks.compactMap { try self.block($0, step: step, learnedMarks: learnedMarks) }
                return .topic(.init(summary: text.text, example: block.arabic, extra: ReadingExtra(title: text.title, blocks: blocks)))
            case "formulas":
                return .formulas(.init(
                    columns: Swift.min(Swift.max(block.columns, 1), ReadingCourse.maxFormulaColumns),
                    items: block.items.map {
                        ReadingFormula(parts: $0.parts, result: $0.result, transliteration: transliteration($0.translations))
                    },
                    sample: block.sample
                ))
            case "zikr":
                return .zikr(try zikr(block, step: step))
            default:
                return nil
            }
        }

        private func forms(_ block: ReadingBlockDTO) throws -> ReadingBlock.Forms {
            let translation = languages.select(from: block.translations.keyedByLanguage)?.translation
            var examples: [ReadingLetter: FormExamples] = [:]
            for (character, words) in block.examples where words.count == ReadingCourse.formExamples {
                // Транскрипция - необязательна: нет перевода или не 3 строки - слова без подписи.
                var transliterations: [LetterForm: String] = [:]
                if let lines = translation?.examples[character], lines.count == ReadingCourse.formExamples {
                    for (form, line) in zip(ReadingCourse.placedForms, lines) {
                        if let text = Optional(line).nonEmpty { transliterations[form] = text }
                    }
                }
                examples[try letter(character)] = FormExamples(
                    initial: words[0], medial: words[1], final: words[2], transliterations: transliterations
                )
            }
            return ReadingBlock.Forms(
                groups: try (block.groups ?? []).map { try $0.map(letter) },
                examples: examples,
                exampleCard: try block.exampleCard.map(letter)
            )
        }

        private func zikr(_ block: ReadingBlockDTO, step: ReadingStepDTO) throws -> ReadingBlock.Zikr {
            let text = try require(block.translations, "шаг \(step.id), зикр")
            guard let section = block.section.flatMap(AzkarSection.init(rawValue:)) else {
                throw ReadingMappingError(description: "reading.json: зикр - неизвестный раздел \(block.section ?? "")")
            }
            guard let number = block.id else { throw ReadingMappingError(description: "reading.json: зикр без id") }
            guard let arabic = block.arabic else { throw ReadingMappingError(description: "reading.json: зикр без arabic") }
            var joins: [ReadingJoin] = []
            for join in block.joins {
                let joinText = try require(join.translations, "шаг \(step.id), зикр, \(join.arabic)")
                joins.append(ReadingJoin(arabic: join.arabic, transliteration: joinText.translit, note: joinText.note))
            }
            return ReadingBlock.Zikr(
                zikr: ZikrID(section: section, number: number),
                audio: block.audio,
                arabic: arabic,
                words: block.words.map { ReadingWord(arabic: $0.arabic, transliteration: transliteration($0.translations)) },
                joins: joins,
                summary: text.text,
                transliteration: text.translit
            )
        }

        func question(
            _ quiz: ReadingQuizDTO,
            formGroup: (String) throws -> [ReadingLetter]
        ) throws -> ReadingQuestion? {
            switch quiz.type {
            case "letterForm":
                let letter = try letter(quiz.letter)
                guard let form = quiz.form.flatMap(LetterForm.init(rawValue:)) else {
                    throw ReadingMappingError(description: "reading.json: неизвестная форма \(quiz.form ?? "")")
                }
                // Варианты - буквы карточки; буквы нет в таблицах форм - вопрос с одним вариантом, ошибка данных.
                let group = try formGroup(quiz.letter)
                guard group.count > 1 else {
                    throw ReadingMappingError(description: "reading.json: «letterForm» \(quiz.letter) - нет карточки в таблицах форм пройденных шагов")
                }
                return .letterInForm(.init(letter: letter, form: form, group: group))
            case "syllable":
                guard let mark = quiz.mark else { throw ReadingMappingError(description: "reading.json: вопрос «syllable» без знака") }
                return .syllable(.init(letter: try letter(quiz.letter), mark: try self.mark(mark)))
            case "letterName":
                return .letterName(try letter(quiz.letter))
            case "word":
                let text = try require(quiz.translations, "вопрос «word» \(quiz.arabic ?? "")")
                guard text.options.indices.contains(quiz.answer) else {
                    throw ReadingMappingError(description: "reading.json: вопрос «word» - answer вне вариантов")
                }
                guard let arabic = quiz.arabic else { throw ReadingMappingError(description: "reading.json: вопрос «word» без arabic") }
                return .word(.init(
                    arabic: arabic,
                    options: text.options,
                    answer: quiz.answer,
                    explanation: Optional(text.explain).nonEmpty,
                    prompt: Optional(text.question).nonEmpty
                ))
            case "closedSyllable":
                // Закрытый слог: алиф-хамза с огласовкой `mark` и буква с сукуном (`أِبْ`).
                guard let vowel = quiz.mark else {
                    throw ReadingMappingError(description: "reading.json: вопрос «closedSyllable» без огласовки")
                }
                return .closedSyllable(.init(
                    letter: try letter(quiz.letter),
                    vowel: try mark(vowel),
                    alif: try letter(ReadingCourse.alif),
                    sukun: try mark(ReadingMark.sukun)
                ))
            default:
                return nil
            }
        }
    }
}
