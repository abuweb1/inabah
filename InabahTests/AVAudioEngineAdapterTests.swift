import Foundation
import Testing
@testable import Inabah

/// Настоящий движок на записях из Bundle: загрузка вне главного актора, отмена устаревшей
/// загрузки и мгновенные ответы фасада.
@MainActor
@Suite("Движок AVAudioPlayer", .timeLimit(.minutes(1)))
struct AVAudioEngineAdapterTests {
    private let engine = AVAudioEngineAdapter()

    private static func url(_ name: String) throws -> URL {
        try #require(Bundle.main.url(forResource: name, withExtension: nil))
    }

    private func settle() async {
        for _ in 0..<10 { await Task.yield() }
    }

    @Test("Загрузка возвращает длительность, запись стоит в начале")
    func loadReturnsDuration() async throws {
        let duration = try await engine.load(url: Self.url("morning_01.mp3"))

        #expect(duration > 0)
        #expect(!engine.isPlaying)
        #expect(engine.currentTime == 0)
    }

    @Test("Стоп во время загрузки отменяет её")
    func stopCancelsLoad() async throws {
        let url = try Self.url("morning_01.mp3")
        let loading = Task { try await engine.load(url: url) }
        await settle()

        engine.stop()

        await #expect(throws: CancellationError.self) { try await loading.value }
    }

    @Test("Перемотка, пауза и стоп видны сразу, без ожидания плеера")
    func controlsAreImmediate() async throws {
        _ = try await engine.load(url: Self.url("morning_01.mp3"))

        engine.currentTime = 5
        #expect(engine.currentTime == 5)

        engine.play()
        #expect(engine.isPlaying)

        engine.pause()
        #expect(!engine.isPlaying)

        engine.stop()
        #expect(engine.currentTime == 0)
    }

    @Test("Подготовленная запись запускается сразу и с известной длительностью")
    func preloadedStartsImmediately() async throws {
        _ = try await engine.load(url: Self.url("morning_01.mp3"))
        let next = try Self.url("morning_02.mp3")
        await engine.preload(url: next)

        let duration = engine.startPreloaded(url: next, after: 0)

        #expect((duration ?? 0) > 0)
        #expect(engine.isPlaying)
        engine.stop()
    }

    @Test("Неподготовленную запись запустить нельзя")
    func notPreloaded() throws {
        #expect(engine.startPreloaded(url: try Self.url("morning_02.mp3"), after: 0) == nil)
    }
}
