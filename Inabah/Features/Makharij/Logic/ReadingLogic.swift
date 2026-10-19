import Foundation

// Правила «Учимся читать» без SwiftUI (docs/08-screen-makharij.md; как Android `ReadingLogic.kt`): цепочка
// листа буквы, варианты ответа и заход в проверку, шаги, страницы «Читаем слова», зикры итога.

/// Элемент цепочки листа буквы: название → слоги → закрытые слоги.
nonisolated struct LetterChainItem: Hashable, Sendable {
    enum Kind: Hashable, Sendable { case name, syllable, closed }

    let kind: Kind
    let arabic: String
    let transliteration: String
    /// Запись; нет - элемент без ▶.
    let audioFile: String?
    /// Слог с ещё не пройденным знаком - пунктиром, без звука.
    let isLearned: Bool
    /// Название знака слога (`фатха`, у закрытого слога - `сукун`); у названия - `nil`.
    var markName: String?
}

nonisolated extension [LetterChainItem] {
    /// Записи цепочки листа буквы по порядку (название → слоги → закрытые слоги) из тех, что есть в сборке `available`.
    func playableFiles(available: Set<String>) -> [String] {
        compactMap { item in item.audioFile.flatMap { available.contains($0) ? $0 : nil } }
    }
}

/// Вариант ответа: что показать на кнопке и верен ли он.
nonisolated struct QuizOption: Hashable, Sendable {
    let text: String
    let isCorrect: Bool
}

/// Вопрос захода в проверку и его варианты в том порядке, в каком они на экране.
nonisolated struct QuizItem: Hashable, Sendable {
    let question: ReadingQuestion
    let options: [QuizOption]
}

/// Слово шага и его номер в данных: по номеру хранится счёт прочтений.
nonisolated struct IndexedWord: Hashable, Sendable {
    let index: Int
    let word: ReadingWord
}

/// Ряд «Читаем слова»: `columns` плиток в ряд (3, 2 или 1 - во всю ширину). `isPair` - слово и
/// словосочетание с ним (словосочетание шире).
nonisolated struct WordRow: Hashable, Sendable {
    let columns: Int
    let words: [IndexedWord]
    var isPair = false
}

/// Случайный слог карточки урока: буква и знак.
nonisolated struct SyllableSample: Hashable, Sendable {
    let letter: ReadingLetter
    let mark: ReadingMark
}

nonisolated enum ReadingLogic {
    private static let nameKey = "name"
    private static let optionCount = 3
    /// Знаки огласовки (U+064B…U+0652) и кинжальный алиф (U+0670) - часть буквы при подсветке в слове.
    private static let harakat: Set<UInt16> = Set(0x064B...0x0652).union([0x0670])

    // MARK: - Лист буквы

    /// Цепочка буквы `letter` на шаге `stepNumber`: название, слоги всех знаков, для которых у буквы есть
    /// транскрипция (по порядку знаков курса), затем закрытые слоги (`أَبْ إِبْ أُبْ`). Знак, не пройденный к
    /// шагу, - `isLearned == false`, а танвин до его шага не показывается.
    static func letterChain(course: ReadingCourse, letter: ReadingLetter, stepNumber: Int) -> [LetterChainItem] {
        let learned = Set(course.learnedMarks(through: stepNumber).map(\.id))
        var chain: [LetterChainItem] = []
        chain.append(LetterChainItem(
            kind: .name, arabic: letter.arabicName, transliteration: letter.nameTransliteration,
            audioFile: letter.audio[nameKey], isLearned: true
        ))
        for mark in course.marks {
            guard let transliteration = letter.syllables[mark.id] else { continue }
            let isLearned = learned.contains(mark.id)
            // Танвин - только с его шага: до него ещё 3 слога пунктиром загромождали лист.
            if ReadingMark.tanwin.contains(mark.id), !isLearned { continue }
            chain.append(LetterChainItem(
                kind: .syllable, arabic: letter.syllable(mark), transliteration: transliteration,
                audioFile: isLearned ? letter.audio[mark.id] : nil, isLearned: isLearned, markName: mark.name
            ))
        }
        return chain + closedChain(course: course, letter: letter, learned: learned)
    }

    /// Закрытые слоги буквы (согласная с сукуном одна не произносится - «ъаб ъиб ъуб» вместо «б»): только
    /// когда сукун пройден (раньше непонятный знак путает); у алифа - те же, что у хамзы (`أَأْ إِأْ أُأْ`),
    /// у و и ي - без мадда (`ReadingLetter.closedVowels`). Нет в данных сукуна или алифа - пусто.
    private static func closedChain(course: ReadingCourse, letter: ReadingLetter, learned: Set<String>) -> [LetterChainItem] {
        guard let sukun = course.sukun, learned.contains(sukun.id), let alif = course.alif else { return [] }
        let vowels = course.marks.filter { ReadingLetter.closedVowels.contains($0.id) }
        return ReadingClosedSyllable.all(of: letter, alif: alif, vowels: vowels, sukun: sukun).map { closed in
            LetterChainItem(
                kind: .closed, arabic: closed.arabic, transliteration: closed.transliteration,
                audioFile: closed.audio, isLearned: true, markName: sukun.name
            )
        }
    }

    // MARK: - Проверка

    /// Варианты ответа в постоянном порядке (на экране они перемешаны - `quizRound`):
    /// - слог - тот же слог с другими знаками, пройденными к шагу (у которых у буквы есть транскрипция), по
    ///   порядку знаков: «ба · би · бу»; верный - всегда среди них; у танвина варианты - танвины;
    /// - закрытый слог - с фатхьой, касрой и доммой алифа: «ъаб · ъиб · ъуб»;
    /// - слово - варианты из данных (в их порядке);
    /// - буква - она и соседние по алфавиту (у первой и последней - с одной стороны);
    /// - буква по форме - она и соседние в её карточке похожего написания.
    static func quizOptions(course: ReadingCourse, question: ReadingQuestion, stepNumber: Int) -> [QuizOption] {
        switch question {
        case .syllable(let q):
            // Знак вопроса - всегда (сукун: транскрипция - звук буквы), остальные - со слогами; у танвина -
            // только другие танвины («бан · бин · бун»), иначе ответ угадывался по «н».
            let isTanwin = ReadingMark.tanwin.contains(q.mark.id)
            let marks = course.learnedMarks(through: stepNumber)
                .filter { q.letter.syllables[$0.id] != nil || $0.id == q.mark.id }
                .filter { ReadingMark.tanwin.contains($0.id) == isTanwin }
            let others = marks.filter { $0.id != q.mark.id }.prefix(optionCount - 1)
            return marks.filter { $0.id == q.mark.id || others.contains($0) }.map { mark in
                QuizOption(text: q.letter.transliteration(mark) ?? "", isCorrect: mark.id == q.mark.id)
            }
        case .word(let q):
            return q.options.enumerated().map { QuizOption(text: $0.element, isCorrect: $0.offset == q.answer) }
        case .closedSyllable(let q):
            return ReadingQuestion.ClosedSyllable.vowels.map {
                QuizOption(text: q.transliteration(vowel: $0), isCorrect: $0 == q.vowel.id)
            }
        case .letterInForm(let q):
            return neighbours(of: q.letter, in: q.group)
        case .letterName(let letter):
            return neighbours(of: letter, in: course.letters)
        }
    }

    /// Буква и соседние в `letters`: окно из трёх вокруг неё, у краёв - сдвинутое внутрь.
    private static func neighbours(of letter: ReadingLetter, in letters: [ReadingLetter]) -> [QuizOption] {
        let index = letters.firstIndex(of: letter) ?? 0
        let start = Swift.min(Swift.max(index - 1, 0), Swift.max(letters.count - optionCount, 0))
        return letters.dropFirst(start).prefix(optionCount).map { QuizOption(text: $0.name, isCorrect: $0 == letter) }
    }

    /// Заход в проверку: `quizCount` случайных вопросов из запаса шага (меньше в запасе - все) в случайном
    /// порядке, варианты каждого перемешаны. `seed` - одно на заход: тот же заход - те же вопросы и варианты.
    static func quizRound(course: ReadingCourse, step: ReadingStep, seed: UInt64) -> [QuizItem] {
        var generator = SeededGenerator(seed: seed)
        return step.quiz.shuffled(using: &generator).prefix(step.quizCount).map { question in
            QuizItem(
                question: question,
                options: quizOptions(course: course, question: question, stepNumber: step.number).shuffled(using: &generator)
            )
        }
    }

    // MARK: - Шаги

    /// Текущий шаг - первый с уроком, ещё не пройденный; все пройдены - `nil`. Шаги без урока пропускаются.
    static func currentStep(course: ReadingCourse, completed: Set<Int>) -> ReadingStep? {
        course.steps.first { $0.hasLesson && !completed.contains($0.number) }
    }

    /// Число шагов с номером - «M» в «пройдено N из M»; итог не считается.
    static func numberedStepCount(course: ReadingCourse) -> Int { course.steps.count { !$0.isFinal } }

    /// Число пройденных шагов с номером (без итога).
    static func completedCount(course: ReadingCourse, completed: Set<Int>) -> Int {
        course.steps.count { !$0.isFinal && completed.contains($0.number) }
    }

    /// Доля пройденных шагов с номером, 0…1; отметки шагов, которых нет в курсе, и итог не считаются.
    static func completedFraction(course: ReadingCourse, completed: Set<Int>) -> Double {
        let total = numberedStepCount(course: course)
        return total == 0 ? 0 : Double(completedCount(course: course, completed: completed)) / Double(total)
    }

    /// Курс пройден целиком - все шаги, включая итог.
    static func isCourseDone(course: ReadingCourse, completed: Set<Int>) -> Bool {
        !course.steps.isEmpty && course.steps.allSatisfy { completed.contains($0.number) }
    }

    /// Все слова шага прочитаны нужное число раз (слов нет - да).
    static func wordsDone(step: ReadingStep, counts: [Int]?) -> Bool {
        guard let words = step.words else { return true }
        return words.items.indices.allSatisfy { count(counts, at: $0) >= words.repeat }
    }

    /// Сколько слов шага прочитано нужное число раз.
    static func wordsReadCount(step: ReadingStep, counts: [Int]?) -> Int {
        guard let words = step.words else { return 0 }
        return words.items.indices.count { count(counts, at: $0) >= words.repeat }
    }

    private static func count(_ counts: [Int]?, at index: Int) -> Int {
        guard let counts, counts.indices.contains(index) else { return 0 }
        return counts[index]
    }

    /// Пройденные шаги: урок пройден и все слова прочитаны.
    static func completedSteps(course: ReadingCourse, lessonsDone: Set<Int>, wordCounts: [Int: [Int]]) -> Set<Int> {
        Set(course.steps.filter { lessonsDone.contains($0.number) && wordsDone(step: $0, counts: wordCounts[$0.number]) }.map(\.number))
    }

    /// Шаг открыт, если пройдены все шаги с уроком перед ним; шаги без урока не держат следующие.
    static func isUnlocked(course: ReadingCourse, completed: Set<Int>, step: ReadingStep) -> Bool {
        !course.steps.contains { $0.number < step.number && $0.hasLesson && !completed.contains($0.number) }
    }

    /// Есть что сбросить у шага (настройки «Махраджа»): пройден урок, прочитано хоть одно слово или начат
    /// зикр итога.
    static func hasProgress(
        step: ReadingStep,
        lessonsDone: Set<Int>,
        wordCounts: [Int: [Int]],
        zikrs: [ZikrID: ZikrProgress]
    ) -> Bool {
        lessonsDone.contains(step.number)
            || (wordCounts[step.number] ?? []).contains { $0 > 0 }
            || step.zikrs.contains { block in zikrs[block.zikr].map { $0.isRead || !$0.words.isEmpty } ?? false }
    }

    // MARK: - Случайные слоги урока

    /// Случайные слоги для карточки урока: `count` разных букв, у каждой - случайный знак из `marks`.
    /// Одинаковые при том же `seed` (экран хранит его).
    static func sampleSyllables(letters: [ReadingLetter], marks: [ReadingMark], count: Int, seed: UInt64) -> [SyllableSample] {
        guard !marks.isEmpty else { return [] }
        var generator = SeededGenerator(seed: seed)
        return letters.shuffled(using: &generator).prefix(count).map { letter in
            SyllableSample(letter: letter, mark: marks[Int.random(in: marks.indices, using: &generator)])
        }
    }

    /// Случайные закрытые слоги для карточки урока (шаг «Сукун»): `count` разных слогов в порядке таблицы.
    /// Одинаковые при том же `seed` (экран хранит его).
    static func sampleClosedSyllables(items: [ReadingClosedSyllable], count: Int, seed: UInt64) -> [ReadingClosedSyllable] {
        var generator = SeededGenerator(seed: seed)
        let picked = Set(items.indices.shuffled(using: &generator).prefix(count))
        return items.indices.filter(picked.contains).map { items[$0] }
    }

    // MARK: - Слова-примеры форм

    /// Где в слове подсветить букву `letter` формы `form`: диапазон единиц UTF-16 буквы вместе с её знаками.
    /// Начало - первая буква, конец - последняя, середина - первая такая буква не с краю. Нет - `nil`.
    static func letterRange(word: String, letter: String, form: LetterForm) -> ClosedRange<Int>? {
        let code = Array(word.utf16)
        let letterCode = Array(letter.utf16)
        // Буквы слова: позиция и длина вместе со следующими за буквой знаками огласовки.
        var units: [Range<Int>] = []
        var i = 0
        while i < code.count {
            var end = i + 1
            while end < code.count, harakat.contains(code[end]) { end += 1 }
            units.append(i..<end)
            i = end
        }
        let matches = units.enumerated().filter { letterCode.count == 1 && code[$0.element.lowerBound] == letterCode[0] }
        let match: Range<Int>? = switch form {
        case .initial: matches.first { $0.offset == 0 }?.element
        case .final: matches.last { $0.offset == units.count - 1 }?.element
        case .medial: matches.first { $0.offset != 0 && $0.offset != units.count - 1 }?.element
        case .isolated: nil
        }
        return match.map { $0.lowerBound...($0.upperBound - 1) }
    }

    /// Запись слова-примера формы буквы (docs/process/audio.md, раздел 5): `reading_s03_02_medial.m4a` -
    /// шаг 3, буква 2 (ب), слово, где она в середине.
    static func formExampleAudio(step: Int, letter: ReadingLetter, form: LetterForm) -> String {
        "reading_s\(twoDigits(step))_\(twoDigits(letter.number))_\(form.rawValue).m4a"
    }

    private static func twoDigits(_ number: Int) -> String {
        number < 10 && number >= 0 ? "0\(number)" : "\(number)"
    }

    // MARK: - Страницы «Читаем слова»

    /// Страницы «Читаем слова»: `columnsOf` - сколько плиток с этим словом влезает в ряд (экран меряет
    /// слово; 3, 2 или 1), словосочетание - всегда во всю ширину. Сначала короткие (по 3), потом длинные
    /// (по 2), потом во всю ширину; страница - `rowsPerPage` рядов. Неполных рядов нет: лишние слова очереди
    /// переходят в начало следующей, более широкой. `seed` - перемешать слова внутри очередей; нет -
    /// порядок данных.
    static func wordPages(
        words: [ReadingWord],
        columnsOf: (ReadingWord) -> Int,
        rowsPerPage: Int,
        seed: UInt64? = nil
    ) -> [[WordRow]] {
        var ordered = words.enumerated().map { IndexedWord(index: $0.offset, word: $0.element) }
        if let seed {
            var generator = SeededGenerator(seed: seed)
            ordered.shuffle(using: &generator)
        }
        var queues: [Int: [IndexedWord]] = [:]
        for word in ordered {
            let columns = word.word.isPhrase ? 1 : Swift.min(Swift.max(columnsOf(word.word), 1), ReadingWords.maxColumns)
            queues[columns, default: []].append(word)
        }
        var rows: [WordRow] = []
        for columns in stride(from: ReadingWords.maxColumns, through: 1, by: -1) {
            let queue = queues[columns] ?? []
            let rest = queue.count % columns
            if rest > 0 { queues[columns - 1, default: []].insert(contentsOf: queue.suffix(rest), at: 0) }
            rows += queue.dropLast(rest).chunked(by: columns).map { WordRow(columns: columns, words: $0) }
        }
        return rows.chunked(by: Swift.max(rowsPerPage, 1))
    }

    /// Страницы пар (шаг «Мадд перед артиклем»): порядок данных; слово, за которым словосочетание, - с ним в
    /// одном ряду, остальное - во всю ширину. `seed` - перемешать пары между собой; одиночные ряды остаются
    /// на своих местах: среди них аяты подряд.
    static func pairPages(words: [ReadingWord], rowsPerPage: Int, seed: UInt64? = nil) -> [[WordRow]] {
        var rows: [WordRow] = []
        var i = 0
        while i < words.count {
            if !words[i].isPhrase, i + 1 < words.count, words[i + 1].isPhrase {
                rows.append(WordRow(
                    columns: 2,
                    words: [IndexedWord(index: i, word: words[i]), IndexedWord(index: i + 1, word: words[i + 1])],
                    isPair: true
                ))
                i += 2
            } else {
                rows.append(WordRow(columns: 1, words: [IndexedWord(index: i, word: words[i])]))
                i += 1
            }
        }
        if let seed {
            var generator = SeededGenerator(seed: seed)
            let slots = rows.indices.filter { rows[$0].isPair }
            let shuffled = slots.map { rows[$0] }.shuffled(using: &generator)
            for (slot, row) in zip(slots, shuffled) { rows[slot] = row }
        }
        return rows.chunked(by: Swift.max(rowsPerPage, 1))
    }

    // MARK: - Итог «Читаем зикр»

    /// Ряды «По словам» зикра: слова - по порядку зикра; в ряд - сколько влезает у каждого слова ряда
    /// (`columnsOf`: 3, 2 или 1); ряд во всю ширину - `columns` равно числу слов, без пустых мест.
    static func zikrRows(words: [ReadingWord], columnsOf: (ReadingWord) -> Int) -> [WordRow] {
        var rows: [WordRow] = []
        var row: [IndexedWord] = []
        for (index, word) in words.enumerated() {
            let item = IndexedWord(index: index, word: word)
            let size = row.count + 1
            let fits = size <= ReadingWords.maxColumns && (row + [item]).allSatisfy { columnsOf($0.word) >= size }
            if !fits, !row.isEmpty {
                rows.append(WordRow(columns: row.count, words: row))
                row = []
            }
            row.append(item)
        }
        if !row.isEmpty { rows.append(WordRow(columns: row.count, words: row)) }
        return rows
    }

    /// Зикр итога прочитан: все слова отмечены и нажато «Прочитал целиком».
    static func isZikrDone(_ zikr: ReadingBlock.Zikr, progress: ZikrProgress?) -> Bool {
        guard let progress, progress.isRead else { return false }
        return zikr.words.indices.allSatisfy(progress.words.contains)
    }

    /// Итог «Читаем зикр» пройден - прочитаны все его зикры.
    static func allZikrsDone(step: ReadingStep, progress: [ZikrID: ZikrProgress]) -> Bool {
        !step.zikrs.isEmpty && step.zikrs.allSatisfy { isZikrDone($0, progress: progress[$0.zikr]) }
    }

    /// Урок итога с зикрами пройден, когда прочитаны все зикры (кнопки «Готово» у него нет): отметить урок.
    /// `true` - урок стал пройденным сейчас (праздник); уже был отмечен или прочитаны не все - `false`.
    @MainActor
    static func completeFinalIfRead(step: ReadingStep, progress: ReadingProgress) -> Bool {
        guard !progress.lessonsDone.contains(step.number), allZikrsDone(step: step, progress: progress.zikrs) else { return false }
        progress.markLessonDone(step.number)
        return true
    }
}

private nonisolated extension Collection {
    /// Куски по `size` элементов, последний - короче (Kotlin `chunked`).
    func chunked(by size: Int) -> [[Element]] {
        var chunks: [[Element]] = []
        var start = startIndex
        while start != endIndex {
            let end = index(start, offsetBy: size, limitedBy: endIndex) ?? endIndex
            chunks.append(Array(self[start..<end]))
            start = end
        }
        return chunks
    }
}
