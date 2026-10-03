import Foundation
import Testing
@testable import Inabah

/// Бэкенд без звука: записывает команды, может задерживать загрузки, события отправляет тест.
private actor FakeAudioBackend: AudioBackend {
    private let events: AsyncStream<AudioBackendEvent>.Continuation
    private(set) var commands: [AudioBackendCommand] = []
    private var heldLoads: [CheckedContinuation<TimeInterval, any Error>] = []
    private var holdsLoads = false
    private static let duration: TimeInterval = 30

    init(events: AsyncStream<AudioBackendEvent>.Continuation) {
        self.events = events
    }

    func execute(_ command: AudioBackendCommand) {
        commands.append(command)
        switch command {
        case let .load(_, _, reply):
            if holdsLoads { heldLoads.append(reply) } else { reply.resume(returning: Self.duration) }
        case let .preload(_, reply):
            reply.resume(returning: Self.duration)
        default:
            break
        }
    }

    func holdLoads() { holdsLoads = true }

    func releaseLoads() {
        heldLoads.forEach { $0.resume(returning: Self.duration) }
        heldLoads.removeAll()
    }

    func emit(_ event: AudioBackendEvent) { events.yield(event) }

    /// Ревизия последней команды, меняющей позицию.
    var lastRevision: Int? {
        commands.reversed().lazy.compactMap { command -> Int? in
            switch command {
            case let .pause(revision), let .seek(_, revision), let .setRate(_, revision),
                 let .play(_, _, revision), let .startPreloaded(_, _, _, revision):
                revision
            default:
                nil
            }
        }.first
    }
}

/// Фасад движка с подменённым бэкендом: эпохи, ревизии позиции и отмена — без звука и гонок.
@MainActor
@Suite("Фасад аудиодвижка", .timeLimit(.minutes(1)))
struct AudioEngineFacadeTests {
    private let engine: AVAudioEngineAdapter
    private let backend: FakeAudioBackend
    private let url = URL(fileURLWithPath: "/morning_1.mp3")

    init() {
        var created: FakeAudioBackend?
        engine = AVAudioEngineAdapter { events in
            let backend = FakeAudioBackend(events: events)
            created = backend
            return backend
        }
        backend = created!
    }

    /// Дождаться, пока команды дойдут до бэкенда (очередь асинхронная).
    private func waitForCommands(_ count: Int) async throws {
        for _ in 0..<1_000 where await backend.commands.count < count {
            try await Task.sleep(for: .milliseconds(1))
        }
        try #require(await backend.commands.count >= count)
    }

    /// Отправить событие «доиграл» текущей эпохи и дождаться его обработки на главном акторе.
    private func finishAndWait(epoch: Int) async throws {
        var finished = 0
        engine.onFinish = { _ in finished += 1 }
        await backend.emit(.finished(epoch: epoch, successfully: true))
        for _ in 0..<1_000 where finished == 0 {
            try await Task.sleep(for: .milliseconds(1))
        }
        try #require(finished == 1)
    }

    @Test("Стоп во время загрузки отменяет её")
    func stopCancelsHeldLoad() async throws {
        await backend.holdLoads()
        let loading = Task { try await engine.load(url: url) }
        try await waitForCommands(1)

        engine.stop()
        await backend.releaseLoads()

        await #expect(throws: CancellationError.self) { try await loading.value }
    }

    @Test("Отчёт паузы, пришедший после перемотки, позицию не откатывает")
    func stalePositionReportIgnored() async throws {
        _ = try await engine.load(url: url)
        engine.play()
        engine.pause()
        engine.currentTime = 20
        try await waitForCommands(4)
        let seekRevision = try #require(await backend.lastRevision)

        // Отчёт паузы (предыдущая ревизия) приходит позже перемотки.
        await backend.emit(.position(revision: seekRevision - 1, 5, at: .now))
        try await finishAndWait(epoch: 1)

        #expect(engine.currentTime == 20)
    }

    @Test("Отчёт о позиции последней команды применяется")
    func currentPositionReportApplied() async throws {
        _ = try await engine.load(url: url)
        engine.pause()
        try await waitForCommands(2)
        let revision = try #require(await backend.lastRevision)

        await backend.emit(.position(revision: revision, 7, at: .now))
        try await finishAndWait(epoch: 1)

        #expect(engine.currentTime == 7)
    }

    @Test("Событие «доиграл» прошлой записи после стопа отбрасывается")
    func staleFinishAfterStopIgnored() async throws {
        _ = try await engine.load(url: url)
        engine.play()
        engine.stop()
        var finished = 0
        engine.onFinish = { _ in finished += 1 }

        await backend.emit(.finished(epoch: 1, successfully: true))
        try await finishAndWait(epoch: 2)

        #expect(finished == 0)
    }

    @Test("Заметная поправка часов сообщается (экран блокировки), мелкая — нет", arguments: [
        (12.0, 1),
        (0.1, 0),
    ])
    func timeCorrectionReported(reportedPosition: TimeInterval, expectedCorrections: Int) async throws {
        var corrections = 0
        engine.onTimeCorrection = { corrections += 1 }
        _ = try await engine.load(url: url)
        engine.pause()
        try await waitForCommands(2)
        let revision = try #require(await backend.lastRevision)

        await backend.emit(.position(revision: revision, reportedPosition, at: .now))
        try await finishAndWait(epoch: 1)

        #expect(corrections == expectedCorrections)
    }

    @Test("Загрузка записи, которая ещё готовится, отменяет её подготовку")
    func loadCancelsPreloadOfSameTrack() async throws {
        let preloading = Task { await engine.preload(url: url) }
        try await waitForCommands(1)

        _ = try await engine.load(url: url)
        await preloading.value

        #expect(engine.startPreloaded(url: url, after: 0) == nil)
    }

    @Test("Отложенный старт: часы идут от момента, который сообщил бэкенд")
    func delayedStartAnchorsToBackendInstant() async throws {
        _ = try await engine.load(url: url)
        engine.play(after: 3)
        try await waitForCommands(2)
        let revision = try #require(await backend.lastRevision)

        // Звук начался две секунды назад (бэкенд выполнил команду позже, чем рассчитывал фасад).
        await backend.emit(.position(revision: revision, 0, at: .now - .seconds(2)))
        try await finishAndWait(epoch: 1)

        #expect(abs(engine.currentTime - 2) < 0.2)
    }

    @Test("Сброс подготовленной записи: запустить её больше нельзя")
    func discardedPreloadCannotStart() async {
        let next = URL(fileURLWithPath: "/morning_2.mp3")
        await engine.preload(url: next)

        engine.discardPreloaded()

        #expect(engine.startPreloaded(url: next, after: 0) == nil)
    }
}
