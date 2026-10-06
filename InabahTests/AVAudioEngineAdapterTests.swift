import Foundation
import Testing
@testable import Inabah

/// Класс-метка тестового Bundle — `Bundle(for:)` находит по нему ресурсы `InabahTests`.
private final class TestBundleToken {}

/// Настоящий движок на синтетических тонах из тестового Bundle (`Fixtures/test-tone-*.wav`,
/// 8 с, сгенерированы скриптом — в приложении своих записей пока нет): загрузка вне главного
/// актора, отмена устаревшей загрузки и мгновенные ответы фасада.
@MainActor
@Suite("Движок AVAudioPlayer", .timeLimit(.minutes(1)))
struct AVAudioEngineAdapterTests {
    private let engine = AVAudioEngineAdapter()

    private static func url(_ name: String) throws -> URL {
        try #require(Bundle(for: TestBundleToken.self).url(forResource: name, withExtension: nil))
    }

    @Test("Загрузка возвращает длительность, запись стоит в начале")
    func loadReturnsDuration() async throws {
        let duration = try await engine.load(url: Self.url("test-tone-1.wav"))

        #expect(duration > 0)
        #expect(!engine.isPlaying)
        #expect(engine.currentTime == 0)
    }

    @Test("Перемотка, пауза и стоп видны сразу, без ожидания плеера")
    func controlsAreImmediate() async throws {
        _ = try await engine.load(url: Self.url("test-tone-1.wav"))

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
        _ = try await engine.load(url: Self.url("test-tone-1.wav"))
        let next = try Self.url("test-tone-2.wav")
        await engine.preload(url: next)

        let duration = engine.startPreloaded(url: next, after: 0)

        #expect((duration ?? 0) > 0)
        #expect(engine.isPlaying)
        engine.stop()
    }

    @Test("Неподготовленную запись запустить нельзя")
    func notPreloaded() throws {
        #expect(engine.startPreloaded(url: try Self.url("test-tone-2.wav"), after: 0) == nil)
    }
}
