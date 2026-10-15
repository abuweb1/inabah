import Foundation

/// Строки барабана из `count` значений — Android `WheelRows` (`WheelPicker.kt`): по кругу (часы, минуты —
/// «…58, 59, 00, 01…») или от края до края (AM / PM). Индексы — строки списка; значение — `0..<count`.
nonisolated struct WheelRows: Hashable, Sendable {
    let count: Int
    let looping: Bool

    /// «Бесконечный» барабан: столько повторов, чтобы до края не долистать.
    static let loops = 200

    /// Строк в списке.
    var size: Int { looping ? count * Self.loops : count }

    /// Строка на полосе выбора для `value` — у круга середина списка.
    func index(of value: Int) -> Int {
        looping ? Self.loops / 2 * count + value : value
    }

    func value(at index: Int) -> Int {
        looping ? Self.wrapped(index, count) : min(max(index, 0), count - 1)
    }

    /// Строка после сдвига на `delta` значений: без круга — не дальше крайних.
    func shifted(_ index: Int, by delta: Int) -> Int {
        looping ? index + delta : min(max(index + delta, 0), count - 1)
    }

    /// Кратчайший сдвиг от `from` к `to`: по кругу 23 → 1 из 24 — вперёд на 2, а не назад на 22.
    func distance(from: Int, to: Int) -> Int {
        guard looping else { return to - from }
        let forward = Self.wrapped(to - from, count)
        return forward > count / 2 ? forward - count : forward
    }

    private static func wrapped(_ value: Int, _ count: Int) -> Int {
        ((value % count) + count) % count
    }
}
