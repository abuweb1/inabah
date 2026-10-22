/// Что звучит в «Послушать алфавит»: буква урока («Ба») или слог таблицы («Ба · фатхьа») и его запись
/// (Android `AlphabetItem`).
nonisolated struct AlphabetItem: Hashable, Sendable {
    let title: String
    let audio: String?

    /// Буква сетки урока - её название.
    static func letter(_ letter: ReadingLetter) -> AlphabetItem {
        AlphabetItem(title: letter.name, audio: letter.nameAudio)
    }

    /// Слог таблицы «Весь алфавит».
    static func syllable(_ syllable: TableSyllable) -> AlphabetItem {
        AlphabetItem(title: "\(syllable.letter.name) · \(syllable.mark.name)", audio: syllable.audio)
    }
}

/// «Послушать алфавит» (Android `AlphabetPlayback`): какой элемент звучит и на паузе ли. Чистое значение -
/// экран хранит его в `@State`, звук ведёт `alphabetPlayer` по `key`. `nil` у экрана - панели нет.
nonisolated struct AlphabetPlayback: Hashable, Sendable {
    /// После записи - пауза до следующего элемента (Android `ITEM_GAP_MILLIS`).
    static let gap = Duration.milliseconds(500)
    /// Элемент без записи подсвечен столько (Android `SILENT_ITEM_MILLIS`).
    static let silentItem = Duration.seconds(2)

    /// Сколько элементов: букв в сетке (29) или слогов в таблице.
    let count: Int
    /// Звучащий элемент, 0-based.
    private(set) var index = 0
    private(set) var isPaused = false
    /// Растёт при каждом новом звучании той же буквы («Продолжить» - с начала её названия).
    private var run = 0

    /// Ключ звучания: сменился - прошлое замолкает, звучит буква `index` (на паузе - ничего).
    var key: Key { Key(index: index, isPaused: isPaused, run: run) }

    struct Key: Hashable, Sendable {
        let index: Int
        let isPaused: Bool
        let run: Int
    }

    var hasPrevious: Bool { index > 0 }
    var hasNext: Bool { index < count - 1 }

    /// С первой буквы; пустая сетка - `nil`.
    init?(count: Int) {
        guard count > 0 else { return nil }
        self.count = count
    }

    mutating func pause() {
        isPaused = true
    }

    /// Пауза ↔ «Продолжить» (та же буква сначала).
    mutating func togglePause() {
        if isPaused { resume() } else { pause() }
    }

    mutating func previous() {
        guard hasPrevious else { return }
        index -= 1
        resume()
    }

    mutating func next() {
        guard hasNext else { return }
        index += 1
        resume()
    }

    /// Буква отзвучала: следующая; была последней - `false`, алфавит кончился.
    mutating func advance() -> Bool {
        guard hasNext else { return false }
        next()
        return true
    }

    private mutating func resume() {
        isPaused = false
        run += 1
    }
}
