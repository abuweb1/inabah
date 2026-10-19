import AVFoundation
import Observation
import os

/// Короткие записи «Учимся читать» (Android `ClipPlayer.kt`): одна запись за раз, без мини-плеера, экрана
/// блокировки и Now Playing. Кнопка звука на экране есть, только если файл записи есть в сборке
/// (`available`) - положили запись, кнопка появилась сама.
///
/// `AVAudioPlayer` - только в акторе `ClipPlaybackBackend` (его вызовы синхронно ждут аудиосервер), здесь -
/// состояние для экранов. С плеером азкаров - по очереди: запись начинается - азкары на паузу
/// (`onWillPlay`), азкары начинаются - запись замолкает (`AppEnvironment`).
@Observable
final class ClipPlayer {
    /// Имена файлов записей в сборке (`reading_23_name.m4a`, `morning_03.m4a`).
    let available: Set<String>
    /// Звучащая запись - от `play` до конца; конец, ошибка, прерывание, `stop()` - `nil`.
    private(set) var playing: String?
    /// Сколько раз звук забрали (звонок, наушники, азкары): цепочки после прерывания не продолжаются.
    private(set) var interruptions = 0
    /// Номер последнего `play`: экран останавливает только свою запись, а не ту, что началась после.
    private(set) var playID = 0

    /// Запись вот-вот начнётся - плеер азкаров встаёт на паузу.
    @ObservationIgnored var onWillPlay: (() -> Void)?

    @ObservationIgnored private let urls: [String: URL]
    @ObservationIgnored private let backend: any ClipBackend
    @ObservationIgnored private let session: (any AudioSessionHandling)?
    @ObservationIgnored private let commands: AsyncStream<Command>.Continuation
    @ObservationIgnored private var tasks: [Task<Void, Never>] = []
    @ObservationIgnored private var waiters: [Waiter] = []
    @ObservationIgnored private var nextWaiter = 0

    private enum Command {
        case play(URL, id: Int)
        case stop
    }

    private struct Waiter {
        let id: Int
        let file: String
        let interruptionsBefore: Int
        let continuation: CheckedContinuation<Bool, Never>
    }

    /// Записи - все `.m4a` в корне Bundle (группа `audio/` кладёт их туда плоско).
    convenience init(bundle: Bundle = .main, session: (any AudioSessionHandling)? = AudioSessionController()) {
        let urls = (bundle.urls(forResourcesWithExtension: "m4a", subdirectory: nil) ?? [])
            .reduce(into: [String: URL]()) { $0[$1.lastPathComponent] = $1 }
        self.init(files: urls, session: session) { ClipPlaybackBackend(events: $0) }
    }

    init(
        files: [String: URL],
        session: (any AudioSessionHandling)?,
        makeBackend: (AsyncStream<ClipBackendEvent>.Continuation) -> any ClipBackend
    ) {
        urls = files
        available = Set(files.keys)
        self.session = session
        let (events, eventInput) = AsyncStream.makeStream(of: ClipBackendEvent.self)
        backend = makeBackend(eventInput)
        let (commandStream, commandInput) = AsyncStream.makeStream(of: Command.self)
        commands = commandInput
        let backend = backend
        // Команды - по порядку: активация сессии и старт не обгоняют следующую остановку.
        tasks.append(Task { [weak self] in
            for await command in commandStream {
                switch command {
                case .play(let url, let id):
                    try? await self?.session?.activate()
                    guard let self, id == self.playID, self.playing != nil else { continue }
                    await backend.play(url, id: id)
                case .stop:
                    await backend.stop()
                }
            }
        })
        tasks.append(Task { [weak self] in
            for await event in events {
                self?.handle(event)
            }
        })
        session?.onEvent = { [weak self] in self?.handle($0) }
    }

    deinit {
        commands.finish()
        tasks.forEach { $0.cancel() }
    }

    /// Проиграть с начала (повтор той же записи - тоже с начала, не пауза). Файла нет - ничего.
    func play(_ file: String) {
        guard let url = urls[file] else { return }
        onWillPlay?()
        playID += 1
        setPlaying(file)
        commands.yield(.play(url, id: playID))
    }

    /// Проиграть и дождаться: `true` - доиграла сама и звук не забирали (цепочка может продолжаться);
    /// `false` - прервали, зазвучало другое или файла нет. Отменили задачу - запись замолкает (если звучит
    /// ещё она, а не начатая после).
    func playToEnd(_ file: String) async -> Bool {
        let before = interruptions
        play(file)
        guard playing == file else { return false }
        let playID = playID
        nextWaiter += 1
        let id = nextWaiter
        return await withTaskCancellationHandler {
            await withCheckedContinuation { continuation in
                waiters.append(Waiter(id: id, file: file, interruptionsBefore: before, continuation: continuation))
            }
        } onCancel: {
            Task { @MainActor [weak self] in
                guard let self else { return }
                resolveWaiter(id, result: false)
                if self.playID == playID { stop() }
            }
        }
    }

    func stop() {
        guard playing != nil else { return }
        playID += 1
        setPlaying(nil)
        commands.yield(.stop)
    }

    // MARK: - События

    private func handle(_ event: ClipBackendEvent) {
        switch event {
        case .finished(let id), .failed(let id):
            guard id == playID else { return }
            setPlaying(nil)
        }
    }

    private func handle(_ event: AudioSessionEvent) {
        switch event {
        case .interruptionBegan, .outputLost:
            guard playing != nil else { return }
            interruptions += 1
            stop()
        case .mediaServicesReset:
            stop()
        case .interruptionEnded:
            break
        }
    }

    /// Новое состояние; ожидающие `playToEnd` своей записи узнают, чем кончилось.
    private func setPlaying(_ file: String?) {
        if playing != file { playing = file }
        for waiter in waiters where waiter.file != file {
            resolveWaiter(waiter.id, result: file == nil && interruptions == waiter.interruptionsBefore)
        }
    }

    private func resolveWaiter(_ id: Int, result: Bool) {
        guard let index = waiters.firstIndex(where: { $0.id == id }) else { return }
        waiters.remove(at: index).continuation.resume(returning: result)
    }
}

/// События бэкенда: запись `id` доиграла или не открылась.
nonisolated enum ClipBackendEvent: Sendable {
    case finished(id: Int)
    case failed(id: Int)
}

/// Проигрывание записей вне главного актора - протокол, чтобы плеер проверялся без звука.
protocol ClipBackend: Actor {
    func play(_ url: URL, id: Int)
    func stop()
}

/// `AVAudioPlayer` на своей очереди (как `AudioPlaybackBackend`): его вызовы синхронно ждут аудиосервер.
actor ClipPlaybackBackend: ClipBackend {
    private nonisolated let queue = DispatchSerialQueue(label: "app.inabah.clips", qos: .userInitiated)
    nonisolated var unownedExecutor: UnownedSerialExecutor { queue.asUnownedSerialExecutor() }

    private let events: AsyncStream<ClipBackendEvent>.Continuation
    private var player: AVAudioPlayer?
    /// Делегат хранится здесь: `AVAudioPlayer.delegate` - слабая ссылка.
    private var delegate: ClipPlayerDelegate?
    private let logger = Logger(subsystem: "app.inabah", category: "clips")

    init(events: AsyncStream<ClipBackendEvent>.Continuation) {
        self.events = events
    }

    func play(_ url: URL, id: Int) {
        stop()
        do {
            let player = try AVAudioPlayer(contentsOf: url)
            let events = events
            let delegate = ClipPlayerDelegate { finished in
                events.yield(finished ? .finished(id: id) : .failed(id: id))
            }
            player.delegate = delegate
            self.player = player
            self.delegate = delegate
            if !player.play() {
                logger.error("Запись не запустилась: \(url.lastPathComponent, privacy: .public)")
                events.yield(.failed(id: id))
            }
        } catch {
            // Битый файл - тишина, пользователю ничего (как Android).
            logger.error("Запись не открылась: \(url.lastPathComponent, privacy: .public)")
            events.yield(.failed(id: id))
        }
    }

    func stop() {
        player?.stop()
        player = nil
        delegate = nil
    }
}

/// Делегат одной записи: AVFoundation вызывает его на своём потоке.
private nonisolated final class ClipPlayerDelegate: NSObject, AVAudioPlayerDelegate, Sendable {
    private let onEnd: @Sendable (_ finished: Bool) -> Void

    init(onEnd: @escaping @Sendable (_ finished: Bool) -> Void) {
        self.onEnd = onEnd
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        onEnd(flag)
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: (any Error)?) {
        onEnd(false)
    }
}
