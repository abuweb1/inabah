/// «Послушать алфавит» (Android `AlphabetPlayback`): какая буква звучит и на паузе ли. Чистое значение -
/// урок хранит его в `@State`, звук ведёт сам по `key`. `nil` у урока - панели нет.
nonisolated struct AlphabetPlayback: Hashable, Sendable {
    /// После записи названия - пауза до следующей буквы (Android `ALPHABET_GAP_MILLIS`).
    static let gap = Duration.milliseconds(500)
    /// Буква без записи подсвечена столько (Android `LETTER_MILLIS`).
    static let silentLetter = Duration.seconds(2)

    /// Сколько букв в сетке (29).
    let count: Int
    /// Звучащая буква, 0-based.
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
