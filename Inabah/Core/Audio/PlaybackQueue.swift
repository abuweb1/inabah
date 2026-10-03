import Foundation

/// Очередь воспроизведения: записи, текущая позиция и номер повтора.
/// Чистое значение без побочных эффектов — переходы проверяются тестами отдельно от плеера.
nonisolated struct PlaybackQueue: Equatable, Sendable {
    private(set) var items: [AudioQueueItem] = []
    private(set) var index = 0
    /// Номер текущего повтора записи, с 1.
    private(set) var repetition = 1

    init(items: [AudioQueueItem] = [], index: Int = 0) {
        self.items = items
        self.index = items.isEmpty ? 0 : index.clamped(to: 0...(items.count - 1))
    }

    var current: AudioQueueItem? { items.indices.contains(index) ? items[index] : nil }
    var next: AudioQueueItem? { items.indices.contains(index + 1) ? items[index + 1] : nil }
    var count: Int { items.count }
    var isEmpty: Bool { items.isEmpty }
    var hasNext: Bool { index + 1 < items.count }
    var hasPrevious: Bool { index > 0 }
    /// У текущей записи остались повторы.
    var hasMoreRepetitions: Bool { repetition < (current?.repeatCount ?? 1) }

    /// Следующий повтор текущей записи. `false` — повторы закончились.
    mutating func advanceRepetition() -> Bool {
        guard hasMoreRepetitions else { return false }
        repetition += 1
        return true
    }

    /// К следующей записи с первого повтора. `false` — это была последняя.
    mutating func moveNext() -> Bool {
        guard hasNext else { return false }
        index += 1
        repetition = 1
        return true
    }

    /// К предыдущей записи с первого повтора. `false` — это была первая.
    mutating func movePrevious() -> Bool {
        guard hasPrevious else { return false }
        index -= 1
        repetition = 1
        return true
    }

    /// Текущая запись — с первого повтора.
    mutating func restartCurrent() {
        repetition = 1
    }
}
