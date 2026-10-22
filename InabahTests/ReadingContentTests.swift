import Foundation
import Testing
@testable import Inabah

/// «Учимся читать»: `reading.json` из Bundle и ошибки данных (как Android `ReadingContentTest`).
@Suite("Курс «Учимся читать»: данные")
struct ReadingContentTests {
    @Test("Курс из Bundle - 8 знаков, 28 букв и хамза по порядку, 12 шагов и итог")
    func courseShape() async throws {
        let course = try await ReadingCourseFixture.load()

        #expect(course.marks.map(\.id) == ["fatha", "kasra", "damma", "sukun", "shadda", "fathatan", "kasratan", "dammatan"])
        #expect(course.letters.map(\.number) == Array(1...29))
        #expect(course.letters.last?.letter == "ء")
        #expect(course.steps.map(\.number) == Array(1...13))
        #expect(course.steps.filter(\.isFinal).map(\.number) == [13], "итог - только последний")
        #expect(Set(course.letters.filter { !$0.joinsNext }.map(\.letter)) == ["ا", "د", "ذ", "ر", "ز", "و", "ء"])
        #expect(course.steps.allSatisfy { !$0.title.trimmingCharacters(in: .whitespaces).isEmpty && !($0.title + $0.subtitle).contains("—") })
    }

    @Test("Слоги и закрытые слоги строятся из буквы и знака - у алифа хамза на подставке")
    func syllablesAndSound() async throws {
        let course = try await ReadingCourseFixture.load()
        let fatha = try course.mark("fatha")
        let kasra = try course.mark("kasra")
        let fathatan = try course.mark("fathatan")
        let sukun = try #require(course.sukun)
        let lam = try course.letter("ل")
        let alif = try course.letter("ا")
        let hamza = try #require(course.letters.last)

        #expect(lam.syllable(fatha) == "لَ")
        #expect(lam.transliteration(fatha) == "льа")
        #expect(lam.transliteration(sukun) == "ль", "с сукуном - звук буквы")
        #expect(lam.closedSyllable(alif: alif, vowel: fatha, sukun: sukun) == "أَلْ",
                "закрытый слог - алиф-хамза с огласовкой и буква с сукуном")
        #expect(lam.closedTransliteration(alif: alif, vowel: "fatha") == "ъаль")
        #expect(alif.syllable(fatha) == "أَ")
        #expect(alif.syllable(kasra) == "إِ")
        #expect(alif.note != nil, "у алифа - пояснение")
        #expect(hamza.syllable(fatha) == "ءَ")
        #expect(hamza.closedSyllable(alif: alif, vowel: kasra, sukun: sukun) == "إِأْ", "хамза с сукуном - на алифе при любой огласовке")
        #expect(alif.closedSyllable(alif: alif, vowel: kasra, sukun: sukun) == "إِأْ", "у алифа - те же закрытые слоги, что у хамзы")
        #expect(hamza.closedTransliteration(alif: alif, vowel: "kasra") == "ъиъ")
        #expect(try course.letter("و").closedVowels == ["fatha", "kasra"], "у вав - «ъав», «ъив»: أُوْ - мадд")
        #expect(lam.closedVowels == ["fatha", "kasra", "damma"])
        #expect(lam.syllable(fathatan) == "لًا", "после танвина фатхьи - алиф, он не читается")
        #expect(alif.syllable(fathatan) == "أً", "у алифа второго алифа нет")
        #expect(lam.transliteration(fathatan) == "льан")
    }

    @Test("Шаг 2 - знаки, весь алфавит с огласовками, правила и вопросы")
    func step2() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(2)
        #expect(step.title == "Огласовки")

        let marks = try #require(step.marksBlocks.only)
        #expect(marks.marks.map(\.id) == ["fatha", "kasra", "damma"])
        #expect(marks.example.letter == "ب", "знаки - на первой букве таблицы")
        let table = try #require(step.syllablesBlocks.only)
        #expect(table.marks.map(\.id) == ["fatha", "kasra", "damma"], "сукун и шадда - не столбцы")
        #expect(table.letters == Array(course.letters.dropLast()), "28 букв: хамза - в слогах алифа")
        #expect(table.sample == 12, "на уроке - 12 случайных слогов, весь алфавит - отдельным экраном")
        let rules = step.ruleBlocks
        #expect(rules.map(\.letters.count) == [7, 1], "твёрдые буквы и ро")
        #expect(rules.allSatisfy { !$0.transliteration.isEmpty && !$0.text.isEmpty })
        #expect(!step.quiz.isEmpty && step.quiz.allSatisfy { $0.syllable != nil })
    }

    @Test("Неизвестный знак в шаге - ошибка, неизвестный тип блока пропускается")
    func unknownMarkAndBlock() throws {
        #expect(throws: ReadingMappingError.self) {
            try Self.course(Self.minimalJSON(stepMarks: #"["unknown"]"#))
        }

        let future = try Self.course(Self.minimalJSON(blocks: #"[{"type":"video"},{"type":"letters"}]"#))
        #expect(future.steps.first?.blocks == [.letters(ReadingBlock.Letters(letters: future.letters))])
    }

    @Test("Шаг 6: хӏамза - написания и текст на карточке, вне групп и вопросов; слова с мад алифом")
    func hamzaVariants() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(6)
        let forms = try #require(step.formsBlocks.only)
        let hamza = try course.letter("ء")
        let alif = try course.letter("ا")

        #expect(forms.variants[hamza]?.count == LetterForm.allCases.count)
        #expect(forms.variants[hamza]?.first == "ء")
        #expect(!forms.groups.joined().contains(hamza), "хӏамза - не в группах")
        #expect(forms.letters.last == hamza, "карточка буквы листается до хӏамзы последней")
        let note = try #require(forms.notes[hamza])
        #expect(note.text.hasPrefix("Хӏамза пишется по-разному"))
        #expect(note.info != nil)
        #expect(forms.examples[alif]?.initial == "أَنَا")
        #expect(forms.examples[hamza]?.medial == "سُئِلَ")
        let asked = step.quiz.compactMap { if case .letterInForm(let item) = $0 { item.letter } else { nil } }
        #expect(!asked.contains(hamza), "вопросы «форма буквы» - только о буквах групп")
    }

    @Test("Написания не из 4 строк - ошибка; текст на карточке без текста пропускается")
    func variantsAndNotes() throws {
        #expect(throws: ReadingMappingError.self) {
            try Self.course(Self.minimalJSON(blocks: #"[{"type":"forms","groups":[["ب"]],"variants":{"ب":["ب","ب"]}}]"#))
        }
        let course = try Self.course(Self.minimalJSON(blocks: #"""
            [{"type":"forms","groups":[],"variants":{"ب":[" ب ","بـ","ـبـ","ـب"]},
              "translations":{"ru":{"notes":{"ب":{"text":" "}}}}}]
            """#))
        let forms = try #require(course.steps.first?.formsBlocks.only)
        let ba = try course.letter("ب")
        #expect(forms.variants[ba] == ["ب", "بـ", "ـبـ", "ـب"])
        #expect(forms.notes.isEmpty)
        #expect(forms.letters == [ba])
    }

    @Test("Руководство «Межзубные буквы и дод» из Bundle: буквы, рисунки, легенда (как Android)")
    func articulationGuide() async throws {
        let course = try await ReadingCourseFixture.load()
        let guide = try #require(course.guide(id: "articulation"))

        #expect(guide.title == "Межзубные буквы и дод")
        #expect(guide.letters.map(\.letter) == ["ث", "ذ", "ظ", "ض"])
        for letter in guide.letters {
            #expect(course.guide(for: letter) == guide)
        }
        let ba = try #require(course.letters.first { $0.letter == "ب" })
        #expect(course.guide(for: ba) == nil)

        let images = guide.blocks.compactMap { if case .image(let image) = $0 { image } else { nil } }
        #expect(images.map(\.image) == ["interdental-sa-za", "interdental-zo", "dod-place", "dod-palate", "dod-air"])
        let titledTexts = guide.blocks.filter { if case .text(let text) = $0 { text.title != nil } else { false } }
        #expect(titledTexts.count == 2)
        for image in images {
            #expect(GuideImageLoader.url(image.image) != nil, "\(image.image).webp в Bundle")
        }
        let legend = try #require(images.first { !$0.legend.isEmpty }?.legend)
        #expect(legend.map(\.rgb) == [0xB17CD5, 0x23113C])
        #expect(legend.map(\.text) == ["Касание без давления", "Касание и давление"])
        #expect(images.last?.title == nil && images.last?.text == nil, "рисунок без подписи")
    }

    @Test("Руководство: битые рисунок, цвет легенды и неизвестная буква - ошибка", arguments: [
        ##"{"id":"g","letters":["ب"],"translations":{"ru":{"title":"Р"}},"blocks":[{"type":"image"}]}"##,
        ##"{"id":"g","letters":["ب"],"translations":{"ru":{"title":"Р"}},"blocks":[{"type":"image","image":"x","legend":[{"color":"violet","translations":{"ru":{"text":"т"}}}]}]}"##,
        ##"{"id":"g","letters":["ب"],"translations":{"ru":{"title":"Р"}},"blocks":[{"type":"image","image":"x","legend":[{"color":"#B17CD5","translations":{"ru":{"text":""}}}]}]}"##,
        ##"{"id":"g","letters":["ث"],"translations":{"ru":{"title":"Р"}},"blocks":[]}"##,
    ])
    func brokenGuide(guide: String) {
        #expect(throws: ReadingMappingError.self) {
            try Self.course(Self.minimalJSON(guides: "[\(guide)]"))
        }
    }

    @Test("Руководство: цвет легенды #RRGGBB, необязательная подпись, неизвестные блоки пропускаются")
    func minimalGuide() throws {
        let course = try Self.course(Self.minimalJSON(guides: ##"""
            [{"id":"g","letters":["ب"],"translations":{"ru":{"title":"Р"}},"blocks":[
              {"type":"text","translations":{"ru":{"title":" ","text":"Текст"}}},
              {"type":"video"},
              {"type":"image","image":"x","legend":[{"color":"#b17cd5","translations":{"ru":{"text":"т"}}}]}
            ]}]
            """##))
        let guide = try #require(course.guides.only)
        #expect(guide.blocks == [
            .text(ReadingBlock.Text(text: "Текст")),
            .image(ReadingBlock.Image(image: "x", legend: [ReadingLegendItem(rgb: 0xB17CD5, text: "т")])),
        ])
        #expect(try Self.course(Self.minimalJSON()).guides.isEmpty, "без guides - пусто")
    }

    @Test("«Форма буквы» без карточки в таблицах форм - ошибка, не вопрос с одним вариантом")
    func letterFormWithoutCard() {
        #expect(throws: ReadingMappingError.self) {
            try Self.course(Self.minimalJSON(quiz: #"[{"type":"letterForm","letter":"ب","form":"initial"}]"#))
        }
    }

    @Test("Нет курса в памяти - resourceMissing")
    func inMemoryMissing() async {
        await #expect(throws: ContentError.resourceMissing("reading.json")) {
            try await InMemoryContentRepository().readingCourse()
        }
    }

    @Test("Шаг 2 - 60 слов, каждое 6 раз, страница - 5 рядов, транскрипция у всех слов")
    func step2Words() async throws {
        let course = try await ReadingCourseFixture.load()
        let words = try #require(course.requiredStep(2).words)

        #expect(words.items.count == 60)
        #expect(words.repeat == 6)
        #expect(words.rowsPerPage == 5, "15 коротких слов по 3 в ряд")
        #expect(course.steps.compactMap(\.words).allSatisfy { $0.items.allSatisfy { $0.transliteration != nil } })
        #expect(words.items.first?.transliteration == "заъама")
    }

    @Test("Формы букв строятся из буквы - у несоединяемых и хамзы без лишних хвостов")
    func letterForms() async throws {
        let course = try await ReadingCourseFixture.load()

        #expect(LetterForm.allCases.map { try! course.letter("ب").form($0) } == ["ب", "بـ", "ـبـ", "ـب"])
        #expect(LetterForm.allCases.map { try! course.letter("ر").form($0) } == ["ر", "ر", "ـر", "ـر"])
        #expect(LetterForm.allCases.map { try! course.letter("ء").form($0) } == Array(repeating: "ء", count: 4))
    }

    @Test("Шаг 3 - формы группы 1 карточками, пояснение по ⓘ, вопросы по форме, 60 слов")
    func step3() async throws {
        let step = try await ReadingCourseFixture.load().requiredStep(3)

        let forms = try #require(step.formsBlocks.only)
        #expect(forms.groups.map { $0.map(\.letter).joined(separator: " ") } == ["ب ت ث ن ي", "ج ح خ"])
        #expect(forms.examples.count == 8, "слова-примеры у каждой буквы группы")
        #expect(forms.exampleCard.map(\.letter) == ["ب", "ن", "ج"])
        #expect(step.textBlocks.only?.info != nil)
        #expect(step.quiz.compactMap(\.letterInForm).count == 24, "запас - 8 букв в трёх формах")
        #expect(step.words?.items.count == 60)
    }

    @Test("Транскрипция слов-примеров формы - по местам, у каждого слова")
    func formExampleTransliterations() async throws {
        let course = try await ReadingCourseFixture.load()
        let forms = try #require(course.requiredStep(3).formsBlocks.only)
        let ba = try #require(forms.examples[course.letter("ب")])

        #expect(ba.word(.isolated) == nil)
        #expect([LetterForm.initial, .medial, .final].allSatisfy { ba.word($0) != nil && ba.transliteration($0) != nil })
    }

    @Test("Шаг 6 - формы группы 4, формулы мадда, проверка - формы и слова с маддом")
    func step6() async throws {
        let step = try await ReadingCourseFixture.load().requiredStep(6)

        let forms = try #require(step.formsBlocks.only)
        #expect(forms.groups.map { $0.map(\.letter).joined(separator: " ") } == ["د ذ ر ز", "و ا"])
        #expect(step.quiz.compactMap(\.letterInForm).count == 8, "د ذ ر ز - в начале и в конце")
        #expect(step.quiz.compactMap(\.word).count == 16, "слова с буквами мадда")
        let formulas = step.formulasBlocks
        try #require(formulas.count == 2)
        let (madd, small) = (formulas[0], formulas[1])
        let smallIndex = try #require(step.blocks.firstIndex(of: .formulas(small)))
        let formsIndex = try #require(step.blocks.firstIndex(of: .forms(forms)))
        #expect(smallIndex < formsIndex, "мадд - до форм: в примерах форм есть буквы мадда")
        #expect(madd.columns == 1, "формулы - по одной в строку")
        #expect(madd.items.map(\.result) == ["بَا", "بِي", "بُو"])
        #expect(madd.items.map(\.transliteration) == ["баа", "бии", "буу"])
        #expect(madd.items.first?.parts == ["بَ", "ا"])
        #expect(small.columns == 1)
        #expect(small.items.first?.parts == ["تَشَـٰبَهَ"], "маленький алиф - после фатхьи на соединительной черте")
        #expect(step.words?.repeat == 3)
    }

    @Test("Шаг 7 - сукун, формулы только в примере, закрытые слоги таблицей, мадд у вав и йа, 60 слов")
    func step7() async throws {
        let step = try await ReadingCourseFixture.load().requiredStep(7)

        #expect(step.markIds == ["sukun"])
        let formulas = step.formulasBlocks
        try #require(formulas.count == 2)
        let (pair, madd) = (formulas[0], formulas[1])
        #expect(pair.items.map(\.transliteration) == ["заз", "мин", "къуль"], "формула - только в первой карточке")
        #expect(madd.items.map(\.transliteration) == ["ъуу", "ъии"], "أُوْ и إِيْ - мадд, не закрытый слог")
        #expect(madd.items.map(\.result) == ["أُو", "إِي"], "после «=» буква мадда - без знака, как в шаге 6")
        let table = try #require(step.closedSyllablesBlocks.only)
        #expect(table.rows.count == 28, "все буквы, кроме алифа")
        #expect(table.all.count == 82, "26 букв × 3, у вав и йа - по 2")
        let rowOf = { (letter: String) in table.rows.first { $0.compactMap { $0 }.first?.letter.letter == letter }?.map { $0?.transliteration } }
        #expect(rowOf("و") == ["ъав", "ъив", nil])
        #expect(rowOf("ي") == ["ъай", nil, "ъуй"])
        #expect(table.all.last?.arabic == "أُأْ")
        #expect(table.all.first?.arabic == "أَبْ")
        #expect(table.all.first?.transliteration == "ъаб")
        #expect(table.sample == 6, "на уроке - 6 случайных, весь алфавит - по кнопке")
        let closed = step.quiz.compactMap(\.closedSyllable)
        #expect(closed.count == step.quiz.count, "проверка - закрытые слоги, как в «Весь алфавит»")
        #expect(closed.count == 79)
        #expect(closed.allSatisfy { $0.letter.closedVowels.contains($0.vowel.id) }, "мадд (أُوْ, إِيْ) - не закрытый слог")
        #expect(closed.contains { $0.letter.letter == "و" && $0.vowel.id == "kasra" }, "«ъив» - закрытый слог")
        let bi = try #require(closed.first { $0.letter.letter == "ب" && $0.vowel.id == "kasra" })
        #expect(bi.arabic == "إِبْ")
        #expect(bi.transliteration(vowel: "kasra") == "ъиб")
        let words = try #require(step.words)
        #expect(words.items.count == 60)
        #expect(words.repeat == 6)
        #expect(words.items.filter(\.isPhrase).count == 6)
    }

    @Test("Шаг 8 - танвин, знаки, равенства, слоги только с танвином, 60 слов")
    func step8() async throws {
        let step = try await ReadingCourseFixture.load().requiredStep(8)

        #expect(step.markIds == ["fathatan", "kasratan", "dammatan"])
        #expect(step.marksBlocks.only?.example.letter == "ب")
        let equal = try #require(step.formulasBlocks.only)
        #expect(equal.items.map(\.result) == ["بًا", "بٍ", "بٌ"])
        #expect(equal.items.map(\.transliteration) == ["бан", "бин", "бун"])
        let table = try #require(step.syllablesBlocks.only)
        #expect(table.marks.map(\.id) == ["fathatan", "kasratan", "dammatan"], "столбцы - только танвин")
        #expect(table.letters.count == 28)
        #expect(table.sample == 12)
        #expect(step.quiz.allSatisfy { $0.syllable.map { ReadingMark.tanwin.contains($0.mark.id) } == true })
        #expect(step.words?.items.count == 60)
        #expect(step.words?.repeat == 6)
    }

    @Test("Шаг 9 - шадда, равенства книги, вопросы со словом, 60 слов")
    func step9() async throws {
        let step = try await ReadingCourseFixture.load().requiredStep(9)

        #expect(step.markIds == ["shadda"])
        let equal = try #require(step.formulasBlocks.only)
        #expect(equal.items.count == 9)
        #expect([0, 6, 8].map { equal.items[$0].transliteration } == ["ъабба", "джиддан", "роббун"])
        #expect(equal.items.map(\.audio) == (1...9).map { "reading_s09_0\($0).m4a" })
        let words = step.quiz.compactMap(\.word)
        #expect(words.count == step.quiz.count)
        let first = try #require(words.first)
        #expect(first.arabic == "شَكُّ")
        #expect(first.options[first.answer] == "шакку")
        #expect(words.allSatisfy { $0.explanation != nil && $0.options.count == 3 })
        #expect(step.words?.items.count == 60)
        #expect(step.words?.items.first { $0.arabic == "مُحَمَّدٌ" }?.transliteration == "мухьаммадун")
    }

    @Test("Запись формулы - имя файла; `null` и нет поля - не записана", arguments: [
        (#"{"parts": ["بَ"], "result": "بَّ", "audio": "reading_s09_01.m4a"}"#, "reading_s09_01.m4a"),
        (#"{"parts": ["بَ"], "result": "بَّ", "audio": null}"#, nil),
        (#"{"parts": ["بَ"], "result": "بَّ"}"#, nil),
    ] as [(String, String?)])
    func formulaAudio(json: String, audio: String?) throws {
        let formula = try JSONDecoder().decode(ReadingFormulaDTO.self, from: Data(json.utf8))
        #expect(formula.audio == audio)
    }

    @Test("Шаг 10 - артикль, лунные и солнечные буквы справкой, вопросы со словом")
    func step10() async throws {
        let course = try await ReadingCourseFixture.load()
        let step = try course.requiredStep(10)

        #expect(step.title == "Определённый артикль")
        #expect(course.step(11)?.title == "Мадд перед артиклем")
        let read = step.formulasBlocks.flatMap(\.items)
        #expect(read.first { $0.result == "يَوْمِدِّينِ" }?.transliteration == "йавми-ддиини")
        #expect(read.first { $0.result == "ٱلْحَمْدُ" && $0.parts.isEmpty }?.transliteration == "ъальхьамду")
        #expect(step.ruleBlocks.isEmpty, "солнечные и лунные буквы - не на уроке")
        let extra = try #require(step.extra)
        #expect(extra.title == "Солнечные и лунные буквы")
        #expect(extra.ruleBlocks.map(\.letters.count) == [14, 14])
        #expect(!step.quiz.isEmpty && step.quiz.allSatisfy { $0.word != nil })
        #expect(step.words?.items.count == 60)
    }

    @Test("Шаг 11 - мадд перед артиклем, буква мадда не тянется, пары слов")
    func step11() async throws {
        let step = try await ReadingCourseFixture.load().requiredStep(11)

        #expect(step.formulasBlocks.only?.items.first?.transliteration == "фи-лькъубуури")
        let words = try #require(step.words)
        #expect(words.pairs, "пары - рядом")
        #expect(words.items.first { $0.arabic == "مَا" }?.transliteration == "маа")
        #expect(words.items.first { $0.arabic == "مَا ٱلْقَارِعَةُ" }?.transliteration == "ма-лькъоориӏату")
        #expect(step.quiz.allSatisfy { $0.word != nil })
    }

    @Test("Запас вопросов каждого шага с проверкой - не меньше захода, экзамен - 20 из 20")
    func quizPools() async throws {
        let course = try await ReadingCourseFixture.load()
        let withQuiz = course.steps.filter { !$0.quiz.isEmpty }

        for step in withQuiz {
            #expect(step.quiz.count >= step.quizCount, "шаг \(step.number): \(step.quiz.count) < \(step.quizCount)")
        }
        #expect(withQuiz.map(\.number) == Array(1...12), "проверка - у всех шагов, кроме итога")
        #expect(Set(withQuiz.filter { $0.number != 12 }.map(\.quizCount)) == [16])
        #expect(course.step(12)?.quizCount == 20)
    }

    // MARK: - Помощники

    private static func course(_ json: String) throws -> ReadingCourse {
        let dto = try JSONDecoder().decode(ReadingFileDTO.self, from: Data(json.utf8))
        return try ReadingCourse(dto: dto, languages: ContentLanguagePriority(languages: [.base]))
    }

    private static func minimalJSON(
        stepMarks: String = #"["fatha"]"#, blocks: String = "[]", quiz: String = "[]", guides: String? = nil
    ) -> String {
        """
        {
          "marks": [{ "id": "fatha", "sign": "َ", "translations": { "ru": { "name": "фатха" } } }],
          "letters": [{ "id": 1, "letter": "ب", "name": "بَاء", "translations": { "ru": { "name": "Ба" } } }],
          "steps": [{ "id": 1, "marks": \(stepMarks), "translations": { "ru": { "title": "Шаг" } }, "blocks": \(blocks), "quiz": \(quiz) }]\(guides.map { ",\n  \"guides\": \($0)" } ?? "")
        }
        """
    }
}

extension Array {
    /// Единственный элемент; нет или больше одного - `nil` (как `single()` в Kotlin).
    var only: Element? { count == 1 ? first : nil }
}
