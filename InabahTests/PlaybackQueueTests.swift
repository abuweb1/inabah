import Foundation
import Testing
@testable import Inabah

@Suite("Очередь воспроизведения")
struct PlaybackQueueTests {
    private static func items(_ repeats: [Int]) -> [AudioQueueItem] {
        repeats.enumerated().map { offset, count in
            AudioQueueItem(
                track: AudioTrack(
                    id: "t\(offset)",
                    url: URL(fileURLWithPath: "/t\(offset).mp3"),
                    title: "", subtitle: "", category: ""
                ),
                repeatCount: count
            )
        }
    }

    @Test("Повторы, затем следующая запись с первого повтора")
    func repetitionsThenNext() {
        var queue = PlaybackQueue(items: Self.items([2, 1]))

        let repeated = queue.advanceRepetition()
        #expect(repeated)
        #expect(queue.repetition == 2)
        let repeatedAgain = queue.advanceRepetition()
        #expect(!repeatedAgain)

        let movedNext = queue.moveNext()
        #expect(movedNext)
        #expect(queue.index == 1)
        #expect(queue.repetition == 1)
        let movedPastEnd = queue.moveNext()
        #expect(!movedPastEnd)
    }

    @Test("Назад — с первого повтора; на первой записи — нельзя")
    func previous() {
        var queue = PlaybackQueue(items: Self.items([1, 3]), index: 1)
        _ = queue.advanceRepetition()

        let movedBack = queue.movePrevious()
        #expect(movedBack)
        #expect(queue.repetition == 1)
        let movedBeforeStart = queue.movePrevious()
        #expect(!movedBeforeStart)
    }

    @Test("Начальный индекс ограничивается размером очереди", arguments: [(-5, 0), (1, 1), (99, 2)])
    func startIndexClamped(start: Int, expected: Int) {
        #expect(PlaybackQueue(items: Self.items([1, 1, 1]), index: start).index == expected)
    }

    @Test("Пустая очередь")
    func empty() {
        let queue = PlaybackQueue()
        #expect(queue.isEmpty)
        #expect(queue.current == nil)
        #expect(!queue.hasNext)
    }
}
