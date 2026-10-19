import Foundation

// «Учимся читать» (раздел «Махрадж», data/reading.json, docs/06-data-structure.md) - уже на выбранном
// языке. Слоги, звук и формы буквы не хранятся: строятся из буквы и знака (как Android `Reading.kt`).

/// Курс целиком: знаки, 28 букв и хамза, шаги.
nonisolated struct ReadingCourse: Hashable, Sendable {
    let marks: [ReadingMark]
    let letters: [ReadingLetter]
    let steps: [ReadingStep]

    func step(_ number: Int) -> ReadingStep? { steps.first { $0.number == number } }

    /// Знаки, пройденные к шагу `stepNumber` включительно, в порядке `marks`.
    func learnedMarks(through stepNumber: Int) -> [ReadingMark] {
        let learned = Set(steps.filter { $0.number <= stepNumber }.flatMap(\.markIds))
        return marks.filter { learned.contains($0.id) }
    }

    /// Звук буквы - она же с сукуном; сукуна в данных нет - `nil`.
    var sukun: ReadingMark? { marks.first { $0.id == ReadingMark.sukun } }
}

/// Знак огласовки; `id` - слово латиницей (`fatha`), по нему ссылаются шаги и слоги.
nonisolated struct ReadingMark: Hashable, Sendable {
    static let sukun = "sukun"
    static let fathatan = "fathatan"
    /// Танвин: фатхьатан, касратан, домматан.
    static let tanwin: Set<String> = [fathatan, "kasratan", "dammatan"]

    let id: String
    /// Сам знак (U+064B…U+0652).
    let sign: String
    let name: String
    /// «над буквой» / «под буквой».
    let `where`: String
    /// Звук: «а», «ан», «без гласного».
    let sound: String
}

nonisolated struct ReadingLetter: Hashable, Sendable {
    static let tatweel = "ـ"
    private static let alif = "ا"
    private static let seatAlif = "أ"
    private static let nameAudioKey = "name"

    /// Номер в алфавите, 1…29 (хамза - последняя).
    let number: Int
    /// Буква без огласовки.
    let letter: String
    /// Арабское название с огласовками (`لَام`).
    let arabicName: String
    /// `false` - не соединяется со следующей (ا د ذ ر ز و ء).
    let joinsNext: Bool
    /// `false` - не соединяется и с предыдущей (хамза).
    let joinsPrevious: Bool
    /// Подставка слога по знакам: у алифа - хамза на алифе (`أ`, с касрой `إ`); нет знака - сама буква.
    let syllableBases: [String: String]
    /// «Льам».
    let name: String
    /// Транскрипция названия: «льаам».
    let nameTransliteration: String
    /// Транскрипция звука (буква с сукуном): «ль».
    let sound: String
    /// Транскрипция слогов по знакам: `fatha` → «льа».
    let syllables: [String: String]
    /// Записи: `sound`, `name`, `<знак>` → имя файла; записи нет - ключа нет.
    let audio: [String: String]
    /// Пояснение в листе буквы (алиф не бывает сам по себе); нет - `nil`.
    let note: String?

    /// Запись названия - она звучит и в «Послушать алфавит»; нет - `nil`.
    var nameAudio: String? { audio[Self.nameAudioKey] }

    private func base(_ mark: ReadingMark) -> String { syllableBases[mark.id] ?? letter }

    /// Форма буквы в слове, с татвилем на месте соседних букв: начало `بـ`, середина `ـبـ`, конец `ـب`.
    /// Не соединяется со следующей (ا د ذ ر ز و) - без левого хвоста; хамза ни с чем - всегда одна буква.
    func form(_ form: LetterForm) -> String {
        let before = joinsPrevious ? Self.tatweel : ""
        let after = joinsNext ? Self.tatweel : ""
        return switch form {
        case .isolated: letter
        case .initial: letter + after
        case .medial: before + letter + after
        case .final: before + letter
        }
    }

    /// Слог: буква (или её подставка) и знак (`لَ`, у алифа `إِ`). С танвином фатхьи - и алиф, который не
    /// читается (`بًا`); у алифа - без него (`أً`): там алиф уже есть.
    func syllable(_ mark: ReadingMark) -> String {
        let base = base(mark)
        let silentAlif = mark.id == ReadingMark.fathatan && base != Self.seatAlif ? Self.alif : ""
        return base + mark.sign + silentAlif
    }

    /// Транскрипция слога; с сукуном - звук буквы; нет в данных - `nil`.
    func transliteration(_ mark: ReadingMark) -> String? {
        mark.id == ReadingMark.sukun ? sound : syllables[mark.id]
    }

    /// Звук буквы на письме: с сукуном и татвилем перед ней (`ـلْ`, как в конце слова); без татвиля - у
    /// подставки (алиф → `أْ`) и у буквы, которая ни с чем не соединяется (хамза → `ءْ`).
    func soundArabic(sukun: ReadingMark) -> String {
        let base = base(sukun)
        return base == letter && joinsPrevious ? Self.tatweel + letter + sukun.sign : base + sukun.sign
    }
}

/// Форма буквы по месту в слове; `rawValue` - как в `reading.json`.
nonisolated enum LetterForm: String, CaseIterable, Hashable, Sendable {
    case isolated, initial, medial, final
}

/// Слова-примеры буквы: она в начале, в середине и в конце слова; `transliterations` - их транскрипция
/// (на карточке буквы - «подсмотреть»), нет - пусто.
nonisolated struct FormExamples: Hashable, Sendable {
    let initial: String
    let medial: String
    let final: String
    var transliterations: [LetterForm: String] = [:]

    func word(_ form: LetterForm) -> String? {
        switch form {
        case .initial: initial
        case .medial: medial
        case .final: final
        case .isolated: nil
        }
    }

    func transliteration(_ form: LetterForm) -> String? { transliterations[form] }
}

nonisolated struct ReadingStep: Hashable, Sendable {
    /// Вопросов в заходе, если у шага не указано иное.
    static let quizRound = 16

    /// 1, 2, 3… по порядку; ключ прогресса. У итога тоже есть, но не показывается.
    let number: Int
    /// Знаки, которые шаг вводит.
    let markIds: [String]
    /// Пример справа в списке шагов; `nil` - без примера.
    let example: String?
    let title: String
    let subtitle: String
    let blocks: [ReadingBlock]
    /// Запас вопросов проверки: в каждый заход берутся `quizCount` случайных (`ReadingLogic.quizRound`).
    let quiz: [ReadingQuestion]
    /// «Читаем слова»; `nil` - у шага слов нет.
    var words: ReadingWords?
    /// Итог курса («Читаем зикр»): последний, вместо номера - ✦, в «пройдено N из M» не считается.
    var isFinal = false
    /// Справка к уроку отдельным экраном (кнопка под уроком); нет - `nil`.
    var extra: ReadingExtra?
    /// Вопросов в одном заходе в проверку; запас меньше - все.
    var quizCount = quizRound

    /// Пустые шаги - «Урок готовится».
    var hasLesson: Bool { !blocks.isEmpty }

    /// Темы урока по порядку; номер темы - индекс здесь.
    var topics: [ReadingBlock.Topic] { blocks.compactMap { if case .topic(let topic) = $0 { topic } else { nil } } }

    /// Зикры итога по порядку; номер зикра - индекс здесь.
    var zikrs: [ReadingBlock.Zikr] { blocks.compactMap { if case .zikr(let zikr) = $0 { zikr } else { nil } } }

    /// Справка: `topic` - тема урока по номеру, `nil` - справка шага (`extra`).
    func extra(topic: Int?) -> ReadingExtra? {
        guard let topic else { return extra }
        return topics.indices.contains(topic) ? topics[topic].extra : nil
    }
}

/// Справка к уроку: `title` - на кнопке и в навбаре своего экрана, `blocks` - его содержимое.
nonisolated struct ReadingExtra: Hashable, Sendable {
    let title: String
    let blocks: [ReadingBlock]
}

/// «Читаем слова»: каждое слово прочитать `repeat` раз; `groupSize` - слов на странице из коротких слов
/// (15 = 5 рядов по 3). Страницы строит `ReadingLogic.wordPages` / `pairPages`.
nonisolated struct ReadingWords: Hashable, Sendable {
    /// Больше всего плиток в ряду - у коротких слов.
    static let maxColumns = 3

    let groupSize: Int
    let `repeat`: Int
    let items: [ReadingWord]
    /// Пары (шаг «Мадд перед артиклем»): слово и за ним словосочетание с ним - в одном ряду.
    var pairs = false

    /// Рядов на странице: `groupSize` коротких слов по `maxColumns` в ряд.
    var rowsPerPage: Int { Swift.max(1, (groupSize + Self.maxColumns - 1) / Self.maxColumns) }
}

nonisolated struct ReadingWord: Hashable, Sendable {
    let arabic: String
    let transliteration: String?

    /// Несколько слов через пробел («لَهُۥ بَنِينَ») - на экране строкой во всю ширину.
    var isPhrase: Bool { arabic.contains(" ") }
}

/// Место слитного чтения зикра: слова подряд, как читаются вместе, и почему (`note`).
nonisolated struct ReadingJoin: Hashable, Sendable {
    let arabic: String
    let transliteration: String
    let note: String
}

/// Формула блока `formulas`: части, итог и его транскрипция (`nil` - без подписи).
nonisolated struct ReadingFormula: Hashable, Sendable {
    let parts: [String]
    let result: String
    let transliteration: String?
}

/// Блок урока; неизвестные типы данных сюда не попадают.
nonisolated enum ReadingBlock: Hashable, Sendable {
    case text(Text)
    case forms(Forms)
    case marks(Marks)
    case letters(Letters)
    case syllables(Syllables)
    case formulas(Formulas)
    case topic(Topic)
    case zikr(Zikr)
    case rule(Rule)

    /// Короткий текст; `info` - полный, во всплывающем окне по ⓘ.
    struct Text: Hashable, Sendable {
        let text: String
        var info: String?
    }

    /// Таблица форм: карточки по группам похожего написания, ряд - буква, столбцы - 4 формы; `examples` -
    /// слова, где буква в начале, в середине и в конце; `exampleCard` - буквы третьей карточки «в словах».
    struct Forms: Hashable, Sendable {
        let groups: [[ReadingLetter]]
        var examples: [ReadingLetter: FormExamples] = [:]
        var exampleCard: [ReadingLetter] = []

        var letters: [ReadingLetter] { groups.flatMap { $0 } }
    }

    /// Знаки с названием и звуком, на примере `example`.
    struct Marks: Hashable, Sendable {
        let marks: [ReadingMark]
        let example: ReadingLetter
    }

    /// Сетка букв; нажатие - лист буквы.
    struct Letters: Hashable, Sendable {
        let letters: [ReadingLetter]
    }

    /// Таблица слогов: буквы × знаки, пройденные к шагу (у которых у всех букв есть транскрипция).
    struct Syllables: Hashable, Sendable {
        let letters: [ReadingLetter]
        let marks: [ReadingMark]
        /// На уроке - столько случайных слогов и «Посмотреть весь алфавит»; `nil` - вся таблица на уроке.
        var sample: Int?
    }

    /// Формулы на пергаменте: части через «+» и итог после «=», справа налево - «بَ + ا = بَا»; одна часть -
    /// равенство, без частей - слово строкой. `columns` - формул в ряду; `sample` - на уроке столько
    /// случайных и «Весь алфавит» отдельным экраном, `nil` - все на уроке.
    struct Formulas: Hashable, Sendable {
        let columns: Int
        let items: [ReadingFormula]
        var sample: Int?
    }

    /// Тема урока («Особые случаи»): карточка с кратким описанием `summary` и примером `example`,
    /// подробности - своим экраном (`extra`, как справка).
    struct Topic: Hashable, Sendable {
        let summary: String
        let example: String?
        let extra: ReadingExtra
    }

    /// Зикр итога «Читаем зикр»: зикр `zikr` из «Азкаров» и его запись `audio` (`nil` - без ▶); `words` - по
    /// порядку; `joins` - места слитного чтения; `summary` - что в нём встретится из курса;
    /// `transliteration` - строка целиком.
    struct Zikr: Hashable, Sendable {
        let zikr: ZikrID
        let audio: String?
        let arabic: String
        let words: [ReadingWord]
        let joins: [ReadingJoin]
        let summary: String
        let transliteration: String
    }

    /// Правило: арабское название и его транскрипция, пояснение, буквы правила.
    struct Rule: Hashable, Sendable {
        let arabic: String
        let transliteration: String
        let text: String
        let letters: [ReadingLetter]
    }
}

/// Вопрос проверки; варианты ответа строит `ReadingLogic.quizOptions`.
nonisolated enum ReadingQuestion: Hashable, Sendable {
    case syllable(Syllable)
    case closedSyllable(ClosedSyllable)
    case word(Word)
    case letterName(ReadingLetter)
    case letterInForm(LetterInForm)

    /// «Как читается этот слог?».
    struct Syllable: Hashable, Sendable {
        let letter: ReadingLetter
        let mark: ReadingMark
    }

    /// «Как читается этот слог?» - закрытый слог шага «Сукун»: алиф-хамза с `vowel` и `letter` с сукуном -
    /// `إِبْ`. `alif` - буква алиф курса (её подставки и слоги), `sukun` - знак.
    struct ClosedSyllable: Hashable, Sendable {
        /// Огласовки алифа в вариантах ответа, по порядку.
        static let vowels = ["fatha", "kasra", "damma"]

        let letter: ReadingLetter
        let vowel: ReadingMark
        let alif: ReadingLetter
        let sukun: ReadingMark

        var arabic: String { alif.syllable(vowel) + letter.letter + sukun.sign }

        /// Транскрипция слога с огласовкой `vowel`: «ъиб».
        func transliteration(vowel: String) -> String { (alif.syllables[vowel] ?? "") + letter.sound }
    }

    /// «Как читается это слово?»: варианты транскрипции из данных (в их порядке), `answer` - номер верного,
    /// `explanation` - почему так (нет - «Читается «…».»).
    struct Word: Hashable, Sendable {
        let arabic: String
        let options: [String]
        let answer: Int
        let explanation: String?
        /// Свой вопрос («Как читается на паузе?»); нет - «Как читается это слово?».
        var prompt: String?
    }

    /// «Какая это буква?» по одной форме; варианты - буквы её группы похожего написания (`group`).
    struct LetterInForm: Hashable, Sendable {
        let letter: ReadingLetter
        let form: LetterForm
        let group: [ReadingLetter]
    }
}
