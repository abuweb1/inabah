import Foundation
import Testing
@testable import Inabah

/// Плеер записей «Учимся читать» на подделке движка (как Android `ReadingAudioTest`, часть плеера).
@MainActor
@Suite("Записи курса: ClipPlayer")
struct ClipPlayerTests {
    private static let files = [
        "reading_02_name.m4a": URL(fileURLWithPath: "/clips/reading_02_name.m4a"),
        "reading_02_sound.m4a": URL(fileURLWithPath: "/clips/reading_02_sound.m4a"),
    ]
    private static let name = "reading_02_name.m4a"
    private static let sound = "reading_02_sound.m4a"

    private let session = FakeClipSession()
    private let backend: FakeClipBackend
    private let player: ClipPlayer

    init() {
        var made: FakeClipBackend?
        player = ClipPlayer(files: Self.files, session: session) { events in
            let backend = FakeClipBackend(events: events)
            made = backend
            return backend
        }
        backend = made!
    }

    @Test("Записи в сборке - available; нет файла - тишина")
    func missingFile() async {
        #expect(player.available == Set(Self.files.keys))

        player.play("reading_09_name.m4a")
        let finished = await player.playToEnd("reading_09_name.m4a")

        #expect(player.playing == nil)
        #expect(!finished)
        await settle()
        #expect(await backend.plays.isEmpty)
    }

    @Test("Повтор той же записи - снова с начала, не пауза")
    func replayFromStart() async {
        player.play(Self.name)
        await waitForPlays(1)
        player.play(Self.name)
        await waitForPlays(2)

        let plays = await backend.plays
        #expect(plays.map(\.url.lastPathComponent) == [Self.name, Self.name])
        #expect(plays[0].id != plays[1].id)
        #expect(player.playing == Self.name)
        #expect(session.activations == 2)
    }

    @Test("Запись начинается - плеер азкаров на паузу (onWillPlay)")
    func willPlay() {
        var calls = 0
        player.onWillPlay = { calls += 1 }

        player.play(Self.name)
        player.play("нет такого")

        #expect(calls == 1)
    }

    @Test("playToEnd: доиграла сама - true, playing = nil")
    func playToEndFinished() async {
        let task = Task { await player.playToEnd(Self.name) }
        await waitForPlays(1)
        await backend.finishLast()

        #expect(await task.value)
        #expect(player.playing == nil)
    }

    @Test("playToEnd: зазвучало другое - false")
    func playToEndReplaced() async {
        let task = Task { await player.playToEnd(Self.name) }
        await waitForPlays(1)
        player.play(Self.sound)

        #expect(await task.value == false)
        #expect(player.playing == Self.sound)
    }

    @Test("playToEnd: прерывание сессии - false, запись остановлена")
    func playToEndInterrupted() async {
        let task = Task { await player.playToEnd(Self.name) }
        await waitForPlays(1)
        session.onEvent?(.interruptionBegan)

        #expect(await task.value == false)
        #expect(player.playing == nil)
        #expect(player.interruptions == 1)
        await settle()
        #expect(await backend.stops >= 1)
    }

    @Test("Отменили ожидание - своя запись замолкает, начатая после - нет")
    func cancelStopsOwn() async {
        let own = Task { await player.playToEnd(Self.name) }
        await waitForPlays(1)
        own.cancel()
        #expect(await own.value == false)
        await settle()
        #expect(player.playing == nil)

        let replaced = Task { await player.playToEnd(Self.name) }
        await waitForPlays(2)
        player.play(Self.sound)
        replaced.cancel()
        _ = await replaced.value
        await settle()
        #expect(player.playing == Self.sound)
    }

    @Test("Конец старой записи не сбрасывает новую")
    func staleFinish() async {
        player.play(Self.name)
        await waitForPlays(1)
        player.play(Self.sound)
        await waitForPlays(2)

        await backend.finish(index: 0)
        await settle()

        #expect(player.playing == Self.sound)
    }

    @Test("stop() - тишина, playing = nil")
    func stop() async {
        player.play(Self.name)
        await waitForPlays(1)
        player.stop()
        await settle()

        #expect(player.playing == nil)
        #expect(await backend.stops >= 1)
    }

    // MARK: - Ожидание очереди команд

    private func waitForPlays(_ count: Int) async {
        for _ in 0..<1_000 where await backend.plays.count < count {
            await Task.yield()
        }
    }

    private func settle() async {
        for _ in 0..<50 { await Task.yield() }
    }
}

/// Движок без звука: запоминает старты и остановки, доигрывает по команде теста.
private actor FakeClipBackend: ClipBackend {
    struct Play {
        let url: URL
        let id: Int
    }

    private let events: AsyncStream<ClipBackendEvent>.Continuation
    private(set) var plays: [Play] = []
    private(set) var stops = 0

    init(events: AsyncStream<ClipBackendEvent>.Continuation) {
        self.events = events
    }

    func play(_ url: URL, id: Int) {
        plays.append(Play(url: url, id: id))
    }

    func stop() {
        stops += 1
    }

    func finishLast() {
        if let last = plays.last { events.yield(.finished(id: last.id)) }
    }

    func finish(index: Int) {
        events.yield(.finished(id: plays[index].id))
    }
}

private final class FakeClipSession: AudioSessionHandling {
    var onEvent: ((AudioSessionEvent) -> Void)?
    private(set) var activations = 0

    func activate() async throws {
        activations += 1
    }

    func deactivate() async throws {}
}
