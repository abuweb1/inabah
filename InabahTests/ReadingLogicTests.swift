import Foundation
import Testing
@testable import Inabah

/// «Учимся читать»: цепочка листа буквы, варианты ответа, шаги, страницы слов (как Android `ReadingLogicTest`).
@MainActor
@Suite("Курс «Учимся читать»: правила")
struct ReadingLogicTests {
    private let storage: IsolatedDefaults

    init() throws {
        storage = try IsolatedDefaults("ReadingLogicTests")
    }

    // MARK: - Руководства

    @Test("Руководство - на уроке, где его буквы в сетке или таблице форм; пометка - у всех его букв",
          arguments: [(1, true), (2, false), (3, true), (4, true), (6, true), (12, false)])
    func stepGuides(step number: Int, shows: Bool) async throws {
        let course = try await ReadingCourseFixture.load()
        let guide = try #require(course.guide(id: "articulation"))

        let guides = ReadingLogic.guides(for: try course.requiredStep(number), course: course)

        #expect(guides == (shows ? [guide] : []))
        #expect(ReadingLogic.markedLetters(course: course) == Set(guide.letters))
    }

    // MARK: - Лист буквы

    @Test("Цепочка - название и слоги, закрытые слоги только с шага «Сукун», до шага 2 огласовки не пройдены")
    func letterChain() async throws {
        let course = try await ReadingCourseFixture.load()
        let lam = try course.letter("ل")
        let sukunStep = try #require(course.steps.first { $0.markIds.contains("sukun") }).number

        let chain = ReadingLogic.letterChain(course: course, letter: lam, stepNumber: 1)

        #expect(chain.map(\.kind) == [.name, .syllable, .syllable, .syllable])
        #expect(chain.map(\.transliteration) == ["льаам", "льа", "ли", "льу"])
        #expect(chain.map(\.isLearned) == [true, false, false, false])
        #expect(ReadingLogic.letterChain(course: course, letter: lam, stepNumber: 2).allSatisfy { $0.isLearned })
        let closed = ReadingLogic.letterChain(course: course, letter: lam, stepNumber: sukunStep).filter { $0.kind == .closed }
        #expect(closed.map(\.arabic) == ["أَلْ", "إِلْ", "أُلْ"], "с шага «Сукун» - закрытые слоги")
        #expect(closed.map(\.transliteration) == ["ъаль", "ъиль", "ъуль"])
        #expect(closed.allSatisfy { $0.markName == "сукун" })
        #expect(ReadingLogic.letterChain(course: course, letter: lam, stepNumber: sukunStep).last?.kind == .closed, "закрытые слоги - в конце")
        #expect(!ReadingLogic.letterChain(course: course, letter: lam, stepNumber: sukunStep - 1).contains { $0.kind == .closed })
    }

    @Test("Закрытые слоги в цепочке: у алифа - хамзы на алифе, у و и ي - без мадда", arguments: [
        ("ا", ["ъаъ", "ъиъ", "ъуъ"]),
        ("و", ["ъав", "ъив"]),
        ("ي", ["ъай", "ъуй"]),
        ("ء", ["ъаъ", "ъиъ", "ъуъ"]),
    ])
    func closedChain(letter: String, expected: [String]) async throws {
        let course = try await ReadingCourseFixture.load()
        let sukunStep = try #require(course.steps.first { $0.markIds.contains("sukun") }).number

        let chain = ReadingLogic.letterChain(course: course, letter: try course.letter(letter), stepNumber: sukunStep)

        #expect(chain.filter { $0.kind == .closed }.map(\.transliteration) == expected)
    }

    @Test("Записи цепочки - по порядку, только есть в сборке и только у пройденных знаков")
    func playableFiles() async throws {
        let course = try await ReadingCourseFixture.load()
        let lam = try course.letter("ل")
            .with(audio: ["name": "n.m4a", "fatha": "f.m4a", "kasra": "k.m4a", "damma": "d.m4a"])
        let all: Set = ["n.m4a", "f.m4a", "k.m4a", "d.m4a"]

        #expect(ReadingLogic.letterChain(course: course, letter: lam, stepNumber: 2).playableFiles(available: all)
                == ["n.m4a", "f.m4a", "k.m4a", "d.m4a"])
        #expect(ReadingLogic.letterChain(course: course, letter: lam, stepNumber: 1).playableFiles(available: all)
                == ["n.m4a"], "до огласовок - только название")
        #expect(ReadingLogic.letterChain(course: course, letter: lam, stepNumber: 2).playableFiles(available: ["n.m4a", "d.m4a"])
                == ["n.m4a", "d.m4a"])
        #expect(lam.nameAudio == "n.m4a")
        let sukunStep = try #require(course.steps.first { $0.markIds.contains("sukun") }).number
        let closed = lam.with(audio: lam.audio.merging(["closedKasra": "ck.m4a"]) { $1 })
        #expect(ReadingLogic.letterChain(course: course, letter: closed, stepNumber: sukunStep).playableFiles(available: all.union(["ck.m4a"])).last
                == "ck.m4a", "закрытый слог - последним")
        #expect(course.letters.first?.nameAudio == nil, "записей в данных пока нет")
    }

    // MARK: - Варианты ответа

    @Test("Варианты слога - тот же слог с пройденными знаками, по порядку")
    func syllableOptions() async throws {
        let course = try await ReadingCourseFixture.load()
        let question = try #require(course.requiredStep(2).quiz.compactMap(\.syllable)
            .first { $0.letter.letter == "ب" && $0.mark.id == "damma" })

        let options = ReadingLogic.quizOptions(course: course, question: .syllable(question), stepNumber: 2)

        #expect(options.map(\.text) == ["ба", "би", "бу"])
        #expect(options.filter(\.isCorrect).map(\.text) == [question.letter.transliteration(question.mark)])
    }

    @Test("Варианты буквы - она и соседние, у первой и последней - с одной стороны")
    func letterNameOptions() async throws {
        let course = try await ReadingCourseFixture.load()
        func names(_ letter: String) throws -> [String] {
            ReadingLogic.quizOptions(course: course, question: .letterName(try course.letter(letter)), stepNumber: 1).map(\.text)
        }

        #expect(try names("ب") == ["Алиф", "Ба", "Та"])
        #expect(try names("ا") == ["Алиф", "Ба", "Та"])
        #expect(try names("ي") == course.letters.suffix(3).map(\.name))
        #expect(ReadingLogic.quizOptions(course: course, question: .letterName(course.letters[5]), stepNumber: 1)
            .filter(\.isCorrect).count == 1)
    }

    @Test("Вопрос с сукуном - верный вариант есть, его транскрипция - звук буквы")
    func sukunOptions() async throws {
        let course = try await ReadingCourseFixture.load()
        let question = ReadingQuestion.syllable(.init(letter: try course.letter("ب"), mark: try #require(course.sukun)))
        let sukunStep = try #require(course.steps.first { $0.markIds.contains("sukun") }).number

        let options = ReadingLogic.quizOptions(course: course, question: question, stepNumber: sukunStep)

        #expect(options.filter(\.isCorrect).map(\.text) == ["б"])
        #expect(options.count == 3)
    }

    @Test("Закрытый слог - варианты с фатхьой, касрой и доммой алифа, верный - своя огласовка")
    func closedSyllableOptions() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(7)
        let questions = step.quiz.compactMap(\.closedSyllable)
        let bi = try #require(questions.first { $0.letter.letter == "ب" && $0.vowel.id == "kasra" })

        let options = ReadingLogic.quizOptions(course: course, question: .closedSyllable(bi), stepNumber: step.number)

        #expect(options.map(\.text) == ["ъаб", "ъиб", "ъуб"])
        #expect(options.filter(\.isCorrect).map(\.text) == ["ъиб"])
        let places = Set(questions.compactMap { q in
            ReadingLogic.quizOptions(course: course, question: .closedSyllable(q), stepNumber: step.number).firstIndex(where: \.isCorrect)
        })
        #expect(places == [0, 1, 2], "верный - не всегда на одном месте")
    }

    @Test("Буква по форме - варианты из её карточки похожего написания")
    func letterInFormOptions() async throws {
        let course = try await ReadingCourseFixture.load()
        let questions = try course.requiredStep(3).quiz.compactMap(\.letterInForm)
        let tha = try #require(questions.first { $0.letter.letter == "ث" })
        let khaa = try #require(questions.first { $0.letter.letter == "خ" })

        #expect(ReadingLogic.quizOptions(course: course, question: .letterInForm(tha), stepNumber: 3).map(\.text) == ["Та", "Са", "Нун"])
        let khaaOptions = ReadingLogic.quizOptions(course: course, question: .letterInForm(khaa), stepNumber: 3)
        #expect(khaaOptions.map(\.text) == ["Джим", "Хьа", "Хо"], "карточка ج ح خ")
        #expect(khaaOptions.filter(\.isCorrect).count == 1)
    }

    @Test("Вопрос с танвином - варианты только танвины, верный один")
    func tanwinOptions() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try #require(course.steps.first { $0.markIds.contains("fathatan") })
        let ba = try course.letter("ب")
        let question = ReadingQuestion.syllable(.init(letter: ba, mark: try course.mark("kasratan")))

        let options = ReadingLogic.quizOptions(course: course, question: question, stepNumber: step.number)

        #expect(options.map(\.text) == ["бан", "бин", "бун"])
        #expect(options.filter(\.isCorrect).map(\.text) == ["бин"])
        let chain = ReadingLogic.letterChain(course: course, letter: ba, stepNumber: step.number).filter { $0.kind == .syllable }.suffix(3)
        #expect(chain.map(\.transliteration) == ["бан", "бин", "бун"])
        #expect(chain.allSatisfy { $0.isLearned })
        #expect(!ReadingLogic.letterChain(course: course, letter: ba, stepNumber: step.number - 1).contains { $0.transliteration == "бан" })
    }

    @Test("Вопрос со словом - варианты из данных в их порядке, верный - по answer, место верного меняется")
    func wordOptions() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(9)
        let words = step.quiz.compactMap(\.word)
        let first = try #require(words.first)

        let options = ReadingLogic.quizOptions(course: course, question: .word(first), stepNumber: step.number)

        #expect(options.map(\.text) == first.options)
        #expect(options.filter(\.isCorrect).map(\.text) == [first.options[first.answer]])
        #expect(Set(words.map(\.answer)) == [0, 1, 2])
    }

    // MARK: - Заход в проверку

    @Test("Заход в проверку - 16 разных вопросов из запаса, варианты перемешаны, верный один")
    func quizRound() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(2)

        let round = ReadingLogic.quizRound(course: course, step: step, seed: 1)

        #expect(round.count == 16)
        #expect(Set(round.map(\.question)).count == 16, "без повторов")
        #expect(round.allSatisfy { step.quiz.contains($0.question) })
        for item in round {
            #expect(Set(item.options) == Set(ReadingLogic.quizOptions(course: course, question: item.question, stepNumber: step.number)))
            #expect(item.options.filter(\.isCorrect).count == 1)
        }
        let places = Set((1...20).flatMap { seed in
            ReadingLogic.quizRound(course: course, step: step, seed: UInt64(seed)).compactMap { $0.options.firstIndex(where: \.isCorrect) }
        })
        #expect(places == [0, 1, 2], "верный - на любом месте")
    }

    @Test("Заход в проверку - тот же для одного зерна, другие зёрна - другие вопросы")
    func quizRoundSeed() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(9)

        #expect(ReadingLogic.quizRound(course: course, step: step, seed: 7) == ReadingLogic.quizRound(course: course, step: step, seed: 7),
                "поворот экрана - тот же заход")
        let rounds = Set((1...5).map { seed in Set(ReadingLogic.quizRound(course: course, step: step, seed: UInt64(seed)).map(\.question)) })
        #expect(rounds.count > 1, "каждый заход - свои вопросы")
    }

    @Test("Заход в экзамен - все 20 вопросов, только в другом порядке")
    func examRound() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(12)

        #expect(Set(ReadingLogic.quizRound(course: course, step: step, seed: 3).map(\.question)) == Set(step.quiz))
    }

    @Test("Экзамен шага 12 - 20 вопросов, форма буквы прошлого шага - варианты из её карточки")
    func examForms() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(12)

        #expect(step.title == "Особые случаи и экзамен")
        #expect(step.quiz.count == 20)
        let form = try #require(step.quiz.compactMap(\.letterInForm).first)
        #expect(form.group.count > 1, "карточка из шага форм, не одна буква")
        #expect(ReadingLogic.quizOptions(course: course, question: .letterInForm(form), stepNumber: step.number).filter(\.isCorrect).count == 1)
    }

    // MARK: - Шаги

    @Test("Текущий шаг - первый непройденный с уроком, все пройдены - нет")
    func currentStep() async throws {
        let course = try await ReadingCourseFixture.load()

        #expect(ReadingLogic.currentStep(course: course, completed: [])?.number == 1)
        for done in 1...12 {
            #expect(ReadingLogic.currentStep(course: course, completed: Set(1...done))?.number == done + 1)
        }
        #expect(ReadingLogic.currentStep(course: course, completed: Set(course.steps.filter(\.hasLesson).map(\.number))) == nil)
        #expect(ReadingLogic.completedFraction(course: course, completed: [1, 2, 99]) == 2.0 / 12, "итог не считается")
    }

    @Test("Итог не считается в «N из M», курс пройден - только вместе с итогом")
    func finalNotCounted() async throws {
        let course = try await ReadingCourseFixture.load()
        let all = Set(course.steps.map(\.number))
        let final = try #require(course.steps.first { $0.isFinal }).number

        #expect(ReadingLogic.numberedStepCount(course: course) == 12)
        #expect(ReadingLogic.completedCount(course: course, completed: all) == 12)
        #expect(ReadingLogic.completedFraction(course: course, completed: all.subtracting([final])) == 1)
        #expect(!ReadingLogic.isCourseDone(course: course, completed: all.subtracting([final])))
        #expect(ReadingLogic.isCourseDone(course: course, completed: all))
    }

    @Test("Шаг со словами пройден, только когда пройдены урок и все слова, следующий до этого закрыт")
    func completedAndUnlocked() async throws {
        let course = try await ReadingCourseFixture.load()
        let step2 = try course.requiredStep(2)
        let words = try #require(step2.words)
        let all = Array(repeating: words.repeat, count: words.items.count)
        var almost = all
        almost[almost.count - 1] = words.repeat - 1

        #expect(ReadingLogic.completedSteps(course: course, lessonsDone: [1, 2], wordCounts: [2: almost]) == [1], "одно слово не дочитано")
        #expect(ReadingLogic.completedSteps(course: course, lessonsDone: [1], wordCounts: [2: all]) == [1], "урок не пройден")
        #expect(ReadingLogic.completedSteps(course: course, lessonsDone: [1, 2], wordCounts: [2: all]) == [1, 2])

        let step3 = try course.requiredStep(3)
        #expect(!ReadingLogic.isUnlocked(course: course, completed: [1], step: step3))
        #expect(ReadingLogic.isUnlocked(course: course, completed: [1, 2], step: step3))
        #expect(ReadingLogic.isUnlocked(course: course, completed: [], step: try course.requiredStep(1)))
        #expect(!ReadingLogic.isUnlocked(course: course, completed: [], step: step2), "шаг 2 - после шага 1")
    }

    @Test("Состояние строки - пройден, текущий, закрыт, открыт; «Открыть все шаги» снимает замки")
    func stepStates() async throws {
        let course = try await ReadingCourseFixture.load()
        let completed: Set = [1, 2]
        let current = ReadingLogic.currentStep(course: course, completed: completed)
        func state(_ number: Int, allOpen: Bool = false) throws -> ReadingStepState {
            ReadingLogic.stepState(course: course, step: try course.requiredStep(number), completed: completed, current: current, allStepsOpen: allOpen)
        }

        #expect(try state(1) == .done)
        #expect(try state(3) == .current)
        #expect(try state(4) == .locked)
        #expect(try state(13) == .locked, "итог - после всех шагов")
        #expect(try state(4, allOpen: true) == .open)
        #expect(try state(1, allOpen: true) == .done, "пройденный не становится открытым")
    }

    @Test("Последний открытый шаг и его «отщёлкивание» - один раз, не у закрытого")
    func lastUnlocked() async throws {
        let course = try await ReadingCourseFixture.load()
        let step3 = try course.requiredStep(3)

        #expect(ReadingLogic.lastUnlocked(course: course, completed: []) == 1)
        #expect(ReadingLogic.lastUnlocked(course: course, completed: [1, 2]) == 3)
        #expect(ReadingLogic.isJustUnlocked(step: step3, state: .current, unlockSeen: 2, lastUnlocked: 3))
        #expect(!ReadingLogic.isJustUnlocked(step: step3, state: .current, unlockSeen: 3, lastUnlocked: 3), "уже видели")
        #expect(!ReadingLogic.isJustUnlocked(step: step3, state: .locked, unlockSeen: 2, lastUnlocked: 3))
        #expect(!ReadingLogic.isJustUnlocked(step: try course.requiredStep(2), state: .done, unlockSeen: 1, lastUnlocked: 3),
                "только последний открытый")
    }

    @Test("Подпись карточки курса - текущий шаг, итог, всё пройдено")
    func homeSummary() async throws {
        let course = try await ReadingCourseFixture.load()
        let all = Set(course.steps.map(\.number))
        let final = try #require(course.steps.last)

        #expect(ReadingLogic.homeSummary(course: course, completed: []) == .step(number: 1, total: 12, title: "Буквы"))
        #expect(ReadingLogic.homeSummary(course: course, completed: all.subtracting([final.number])) == .final(title: final.title))
        #expect(ReadingLogic.homeSummary(course: course, completed: all) == .allDone)
    }

    @Test("Подпись карточки - «пройдено N из M», если шаги с уроком пройдены, а есть шаги без урока")
    func homeSummaryWithEmptySteps() {
        let lesson: [ReadingBlock] = [.text(.init(text: "Урок"))]
        let course = ReadingCourse(marks: [], letters: [], steps: [
            ReadingStep(number: 1, markIds: [], example: nil, title: "Первый", subtitle: "", blocks: lesson, quiz: []),
            ReadingStep(number: 2, markIds: [], example: nil, title: "Готовится", subtitle: "", blocks: [], quiz: []),
        ])

        #expect(ReadingLogic.homeSummary(course: course, completed: [1]) == .progress(done: 1, total: 2))
    }

    @Test("Следующий шаг после проверки - следующий с уроком, без замка; у последнего - нет")
    func nextAfterQuiz() async throws {
        let course = try await ReadingCourseFixture.load()

        #expect(ReadingLogic.nextStepAfterQuiz(course: course, step: try course.requiredStep(1))?.number == 2)
        #expect(ReadingLogic.nextStepAfterQuiz(course: course, step: try #require(course.steps.last)) == nil)
    }

    @Test("Следующий шаг после слов - только открытый, «Открыть все шаги» открывает")
    func nextAfterWords() async throws {
        let course = try await ReadingCourseFixture.load()
        let step2 = try course.requiredStep(2)

        #expect(ReadingLogic.nextStepAfterWords(course: course, step: step2, completed: [1, 2], allStepsOpen: false)?.number == 3)
        #expect(ReadingLogic.nextStepAfterWords(course: course, step: step2, completed: [1], allStepsOpen: false) == nil,
                "шаг 2 не пройден - шаг 3 закрыт")
        #expect(ReadingLogic.nextStepAfterWords(course: course, step: step2, completed: [1], allStepsOpen: true)?.number == 3)
    }

    @Test("Плиток в ряду по ширине слова: 3, 2 или 1 - слово не шире 80 % места под текст")
    func wordColumns() {
        // Ряд 300: плитка по 3 - (300 - 16) / 3 = 94,7, под текст (94,7 - 12) × 0,8 = 66,1;
        // по 2 - (300 - 8) / 2 = 146, под текст (146 - 12) × 0,8 = 107,2.
        #expect(ReadingLogic.wordColumns(wordWidth: 60, rowWidth: 300) == 3)
        #expect(ReadingLogic.wordColumns(wordWidth: 66, rowWidth: 300) == 3)
        #expect(ReadingLogic.wordColumns(wordWidth: 67, rowWidth: 300) == 2)
        #expect(ReadingLogic.wordColumns(wordWidth: 107, rowWidth: 300) == 2)
        #expect(ReadingLogic.wordColumns(wordWidth: 108, rowWidth: 300) == 1)
    }

    @Test("Есть что сбросить - пройденный урок, прочитанное слово или начатый зикр итога")
    func hasProgress() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(3)
        let final = try #require(course.steps.last)
        let zikr = try #require(final.zikrs.first).zikr

        #expect(!ReadingLogic.hasProgress(step: step, lessonsDone: [], wordCounts: [:], zikrs: [:]))
        #expect(!ReadingLogic.hasProgress(step: step, lessonsDone: [], wordCounts: [3: [0, 0]], zikrs: [:]), "слова без счёта")
        #expect(ReadingLogic.hasProgress(step: step, lessonsDone: [3], wordCounts: [:], zikrs: [:]))
        #expect(ReadingLogic.hasProgress(step: step, lessonsDone: [], wordCounts: [3: [0, 1]], zikrs: [:]))
        #expect(!ReadingLogic.hasProgress(step: step, lessonsDone: [2], wordCounts: [2: [1]], zikrs: [zikr: ZikrProgress(words: [0])]),
                "чужой шаг")
        #expect(!ReadingLogic.hasProgress(step: final, lessonsDone: [], wordCounts: [:], zikrs: [zikr: ZikrProgress()]))
        #expect(ReadingLogic.hasProgress(step: final, lessonsDone: [], wordCounts: [:], zikrs: [zikr: ZikrProgress(words: [0])]))
        #expect(ReadingLogic.hasProgress(step: final, lessonsDone: [], wordCounts: [:], zikrs: [zikr: ZikrProgress(isRead: true)]))
    }

    // MARK: - Случайные слоги и формулы

    @Test("Случайные слоги - разные буквы, знаки из таблицы, тот же набор при том же зерне")
    func sampleSyllables() async throws {
        let table = try #require(try await ReadingCourseFixture.load().requiredStep(2).syllablesBlocks.only)

        let sample = ReadingLogic.sampleSyllables(letters: table.letters, marks: table.marks, count: 12, seed: 7)

        #expect(Set(sample.map(\.letter)).count == 12)
        #expect(sample.allSatisfy { table.marks.contains($0.mark) })
        #expect(sample == ReadingLogic.sampleSyllables(letters: table.letters, marks: table.marks, count: 12, seed: 7))
    }

    @Test("Случайные закрытые слоги - разные, в порядке таблицы, те же при том же зерне")
    func sampleClosedSyllables() async throws {
        let items = try #require(try await ReadingCourseFixture.load().requiredStep(7).closedSyllablesBlocks.only).all

        let sample = ReadingLogic.sampleClosedSyllables(items: items, count: 6, seed: 7)

        #expect(Set(sample).count == 6)
        #expect(sample.compactMap { items.firstIndex(of: $0) } == sample.compactMap { items.firstIndex(of: $0) }.sorted())
        #expect(sample == ReadingLogic.sampleClosedSyllables(items: items, count: 6, seed: 7))
        #expect(ReadingLogic.sampleClosedSyllables(items: items, count: 100, seed: 1) == items, "больше, чем есть - все")
    }

    // MARK: - Слова-примеры

    @Test("Буква в слове-примере - диапазон буквы со знаком по её месту")
    func letterRange() {
        #expect(ReadingLogic.letterRange(word: "بَحَثَ", letter: "ب", form: .initial) == 0...1)
        #expect(ReadingLogic.letterRange(word: "جَبُنَ", letter: "ب", form: .medial) == 2...3)
        #expect(ReadingLogic.letterRange(word: "ثَبَتَ", letter: "ت", form: .final) == 4...5)
        #expect(ReadingLogic.letterRange(word: "لِيَرِثَ", letter: "ي", form: .medial) == 2...3)
        #expect(ReadingLogic.letterRange(word: "بَحَثَ", letter: "ب", form: .final) == nil)
    }

    @Test("Имя записи слова-примера - шаг, номер буквы и место")
    func formExampleAudio() async throws {
        let ba = try await ReadingCourseFixture.load().letter("ب")

        #expect(ReadingLogic.formExampleAudio(step: 3, letter: ba, form: .medial) == "reading_s03_02_medial.m4a")
    }

    // MARK: - Страницы «Читаем слова»

    @Test("Страницы слов - короткие по 3 впереди, длинные по 2, словосочетания во всю ширину, без неполных рядов")
    func wordPages() {
        // «д» - длинное (по 2), «к» - короткое (по 3), с пробелом - словосочетание.
        let words = ["д1", "к1", "т т", "к2", "д2", "к3", "к4", "д3"].map { ReadingWord(arabic: $0, transliteration: nil) }
        let columnsOf: (ReadingWord) -> Int = { $0.arabic.hasPrefix("д") ? 2 : 3 }

        let pages = ReadingLogic.wordPages(words: words, columnsOf: columnsOf, rowsPerPage: 2)

        // к4 - лишнее в очереди по 3 - уходит в начало очереди по 2.
        #expect(pages.map { $0.map(\.shape) } == [[RowShape(3, [1, 3, 5]), RowShape(2, [6, 0])], [RowShape(2, [4, 7]), RowShape(1, [2])]])
        let odd = ["д1", "д2", "д3"].map { ReadingWord(arabic: $0, transliteration: nil) }
        #expect(ReadingLogic.wordPages(words: odd, columnsOf: columnsOf, rowsPerPage: 5).only?.map(\.columns) == [2, 1])
        #expect(ReadingLogic.wordPages(words: [ReadingWord(arabic: "т т", transliteration: nil)], columnsOf: { _ in 3 }, rowsPerPage: 5)
            .only?.only?.columns == 1, "словосочетание - всегда 1")
        #expect(ReadingLogic.wordPages(words: words, columnsOf: { _ in 7 }, rowsPerPage: 5).first?.first?.columns == 3,
                "больше 3 в ряд не бывает")
    }

    @Test("Перемешанные слова - не по порядку данных, все на месте, короткие впереди, порядок постоянен при том же зерне")
    func shuffledWordPages() {
        let words = (1...30).map { ReadingWord(arabic: $0 <= 20 ? "к\($0)" : "д\($0)", transliteration: nil) }
        let columnsOf: (ReadingWord) -> Int = { $0.arabic.hasPrefix("д") ? 2 : 3 }
        func order(_ seed: UInt64) -> [Int] {
            ReadingLogic.wordPages(words: words, columnsOf: columnsOf, rowsPerPage: 4, seed: seed).flatMap { $0.flatMap { $0.words.map(\.index) } }
        }

        let shuffled = order(7)

        #expect(Set(shuffled) == Set(words.indices))
        #expect(shuffled.count == words.count)
        #expect(shuffled != shuffled.sorted(), "не в порядке данных")
        #expect(shuffled.prefix(18).allSatisfy { $0 < 20 }, "короткие - на первых страницах")
        #expect(order(7) == shuffled)
    }

    @Test("Пары - слово и словосочетание за ним в одном ряду, остальное во всю ширину, порядок данных")
    func pairPages() {
        let words = ["مَا", "مَا ٱلْقَارِعَةُ", "ذُو", "ذُو ٱلْفَضْلِ", "وَإِذَا ٱلصُّحُفُ نُشِرَتْ"].map { ReadingWord(arabic: $0, transliteration: nil) }

        let pages = ReadingLogic.pairPages(words: words, rowsPerPage: 2)

        #expect(pages.map { $0.map { $0.words.map(\.index) } } == [[[0, 1], [2, 3]], [[4]]])
        #expect(pages.flatMap { $0 }.map(\.isPair) == [true, true, false])
    }

    @Test("Перемешанные пары - пары меняются местами, одиночные ряды остаются на своих местах")
    func shuffledPairPages() {
        let pairs = (1...10).flatMap { ["ك\($0)", "ك\($0) ٱل"] }
        let words = (pairs + ["آية ١", "آية ٢", "آية ٣"]).map { ReadingWord(arabic: $0, transliteration: nil) }

        let rows = ReadingLogic.pairPages(words: words, rowsPerPage: 50, seed: 7).flatMap { $0 }

        #expect(rows.flatMap { $0.words.map(\.index) }.sorted() == Array(words.indices))
        #expect(rows.prefix(10).allSatisfy { $0.isPair })
        #expect(rows.prefix(10).compactMap { $0.words.first?.index } != Array(stride(from: 0, to: 20, by: 2)), "пары перемешаны")
        #expect(rows.dropFirst(10).compactMap { $0.words.only?.index } == [20, 21, 22], "аяты - по порядку")
    }

    // MARK: - Особые случаи и итог

    @Test("Особые случаи - три темы своими экранами, номер темы - по порядку, без номера - справка шага")
    func topics() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(12)

        #expect(step.topics.count == 3)
        #expect(step.topics.allSatisfy { !$0.extra.title.isEmpty && !$0.summary.isEmpty && !$0.extra.blocks.isEmpty })
        #expect(step.extra(topic: 1) == step.topics[1].extra)
        #expect(step.extra(topic: 3) == nil)
        #expect(step.extra(topic: nil) == nil, "у шага 12 своей справки нет")
        let step10 = try course.requiredStep(10)
        #expect(step10.extra(topic: nil) == step10.extra)
        #expect(step.topics[1].extra.formulasBlocks.first?.items.allSatisfy(\.parts.isEmpty) == true, "слово - строкой без «=»")
        let wasl = step.topics[0].extra.formulasBlocks.dropLast().flatMap(\.items)
        #expect(wasl.allSatisfy { $0.parts.isEmpty && $0.result.hasPrefix("ٱ") })
        #expect(wasl.first { $0.result == "ٱسْجُدُوا۟" }?.transliteration == "ъусджудуу")
        #expect(step.quiz.compactMap(\.word).last?.prompt == "Как читается на паузе?")
    }

    @Test("Слова зикра - по порядку зикра, в ряд сколько влезает у каждого слова, ряд без пустых мест")
    func zikrRows() {
        let words = ["к1", "к2", "к3", "к4", "д1", "к5", "т1"].map { ReadingWord(arabic: $0, transliteration: nil) }
        let columnsOf: (ReadingWord) -> Int = { word in
            switch word.arabic.first {
            case "к": 3
            case "д": 2
            default: 1
            }
        }

        let rows = ReadingLogic.zikrRows(words: words, columnsOf: columnsOf)

        #expect(rows.map { $0.words.map(\.word.arabic) } == [["к1", "к2", "к3"], ["к4", "д1"], ["к5"], ["т1"]])
        #expect(rows.map(\.columns) == rows.map(\.words.count), "во всю ширину")
        #expect(rows.flatMap { $0.words.map(\.index) } == Array(0...6), "индексы - по порядку зикра")
    }

    @Test("Итог «Читаем зикр» - 4 зикра по нарастанию, зикр прочитан - все слова и «целиком», итог - все зикры")
    func finalZikrs() async throws {
        let step = try #require(try await ReadingCourseFixture.load().steps.last)
        #expect(step.isFinal)

        let zikrs = step.zikrs
        #expect(zikrs.count == 4)
        #expect(zikrs.map(\.words.count) == zikrs.map(\.words.count).sorted(), "от короткого к длинному")
        #expect(zikrs.allSatisfy { $0.arabic == $0.words.map(\.arabic).joined(separator: " ") && $0.audio != nil })
        let first = try #require(zikrs.first)
        let allWords = Set(first.words.indices)
        #expect(!ReadingLogic.isZikrDone(first, progress: ZikrProgress(words: allWords)), "без «прочитал целиком»")
        #expect(!ReadingLogic.isZikrDone(first, progress: ZikrProgress(words: allWords.subtracting([0]), isRead: true)), "не все слова")
        #expect(ReadingLogic.isZikrDone(first, progress: ZikrProgress(words: allWords, isRead: true)))
        var done = Dictionary(uniqueKeysWithValues: zikrs.map { ($0.zikr, ZikrProgress(words: Set($0.words.indices), isRead: true)) })
        #expect(ReadingLogic.allZikrsDone(step: step, progress: done))
        done[zikrs[zikrs.count - 1].zikr] = nil
        #expect(!ReadingLogic.allZikrsDone(step: step, progress: done))
    }

    @Test("Урок итога отмечается один раз - когда прочитан последний зикр, шаг без зикров - никогда")
    func completeFinal() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try #require(course.steps.last)
        let last = try #require(step.zikrs.last)
        let progress = ReadingProgress(defaults: storage.defaults)
        for zikr in step.zikrs {
            zikr.words.indices.forEach { progress.readZikrWord(zikr.zikr, word: $0) }
            if zikr != last { progress.markZikrRead(zikr.zikr) }
        }

        #expect(!ReadingLogic.completeFinalIfRead(step: step, progress: progress), "последний не прочитан целиком")
        #expect(!progress.lessonsDone.contains(step.number))

        progress.markZikrRead(last.zikr)
        #expect(ReadingLogic.completeFinalIfRead(step: step, progress: progress), "праздник")
        #expect(progress.lessonsDone.contains(step.number))
        #expect(!ReadingLogic.completeFinalIfRead(step: step, progress: progress), "повторный вход - без праздника")
        #expect(!ReadingLogic.completeFinalIfRead(step: try course.requiredStep(1), progress: progress), "урок без зикров")
        #expect(!progress.lessonsDone.contains(1))
    }

    // MARK: - «Послушать алфавит» в таблице

    @Test("Таблица слогов звучит ряд за рядом, в ряду - знаки по порядку (на экране справа налево)", arguments: [2, 8])
    func syllablesPlayOrder(stepNumber: Int) async throws {
        let step = try await ReadingCourseFixture.load().requiredStep(stepNumber)
        let block = try #require(step.syllablesBlocks.first)

        let order = ReadingLogic.syllablesPlayOrder(block)

        #expect(order.count == block.letters.count * block.marks.count)
        #expect(order.prefix(block.marks.count).map(\.letter) == Array(repeating: block.letters[0], count: block.marks.count))
        #expect(order.prefix(block.marks.count).map(\.mark) == block.marks)
        #expect(order[block.marks.count].letter == block.letters[1])
        #expect(Set(order.map(\.id)).count == order.count, "место каждого слога - своё")
    }

    @Test("Закрытые слоги звучат без мест мадда: 82, у و - фатхьа и касра, у ي - фатхьа и домма")
    func closedSyllablesPlayOrder() async throws {
        let course = try await ReadingCourseFixture.load()
        let block = try #require(try course.requiredStep(7).closedSyllablesBlocks.first)

        let order = ReadingLogic.closedSyllablesPlayOrder(block)

        #expect(order.count == 82)
        #expect(order.filter { $0.letter.letter == "و" }.map(\.mark.id) == ["fatha", "kasra"])
        #expect(order.filter { $0.letter.letter == "ي" }.map(\.mark.id) == ["fatha", "damma"])
        let first = try #require(order.first)
        #expect(AlphabetItem.syllable(first).title == "\(first.letter.name) · \(first.mark.name)")
    }
}

/// Ряд страницы для сравнения: число плиток и номера слов.
private struct RowShape: Equatable {
    let columns: Int
    let indices: [Int]

    init(_ columns: Int, _ indices: [Int]) {
        self.columns = columns
        self.indices = indices
    }
}

private extension WordRow {
    var shape: RowShape { RowShape(columns, words.map(\.index)) }
}

private extension ReadingLetter {
    /// Та же буква с другими записями (Kotlin `copy(audio = …)`).
    func with(audio: [String: String]) -> ReadingLetter {
        ReadingLetter(
            number: number, letter: letter, arabicName: arabicName, joinsNext: joinsNext, joinsPrevious: joinsPrevious,
            syllableBases: syllableBases, name: name, nameTransliteration: nameTransliteration, sound: sound,
            syllables: syllables, audio: audio, note: note, closedBase: closedBase, closedVowels: closedVowels
        )
    }
}
