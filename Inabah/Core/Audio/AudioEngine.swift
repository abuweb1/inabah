import AVFoundation
import os

/// Движок воспроизведения одного файла с подготовкой следующего. Логика плеера
/// (`AudioPlayerController`) работает только с этим протоколом — в тестах подставляется заглушка.
protocol AudioEngine: AnyObject {
    /// Загружает файл как текущий и возвращает его длительность. Тяжёлая часть (чтение
    /// и декодирование) — вне главного актора. Если за время загрузки запрошена другая запись,
    /// бросает `CancellationError`: устаревший результат не подменяет новую запись.
    func load(url: URL) async throws -> TimeInterval
    /// Заранее готовит следующую запись (пока текущая играет).
    func preload(url: URL) async
    /// Делает подготовленную запись текущей и запускает её через `delay` секунд (пауза между
    /// зикрами — часть аудиодорожки, звук не прерывается и фон не засыпает).
    /// Возвращает длительность или `nil`, если эта запись не была подготовлена.
    func startPreloaded(url: URL, after delay: TimeInterval) -> TimeInterval?
    /// Запускает текущую запись через `delay` секунд. `false` — система не дала начать.
    @discardableResult
    func play(after delay: TimeInterval) -> Bool
    func pause()
    func stop()
    var isPlaying: Bool { get }
    var currentTime: TimeInterval { get set }
    var rate: Float { get set }
    /// Текущая запись доиграла: `true` — нормально, `false` — с ошибкой. Вызывается на главном акторе.
    var onFinish: ((_ successfully: Bool) -> Void)? { get set }
    /// Ошибка декодирования во время воспроизведения. Вызывается на главном акторе.
    var onDecodeError: (() -> Void)? { get set }
}

extension AudioEngine {
    @discardableResult
    func play() -> Bool { play(after: 0) }
}

nonisolated enum AudioEngineError: Error, Equatable {
    /// Файл не открылся или не декодируется.
    case cannotLoad(String)
    /// Система не дала аудиосессию (например, идёт звонок).
    case sessionUnavailable
    /// Запись не удалось запустить.
    case playbackFailed
}

/// `AVAudioPlayer`: локальные файлы, скорость без изменения высоты голоса (`enableRate`).
final class AVAudioEngineAdapter: AudioEngine {
    var onFinish: ((_ successfully: Bool) -> Void)?
    var onDecodeError: (() -> Void)?

    var rate: Float = 1 {
        didSet { current?.player.rate = rate }
    }

    var currentTime: TimeInterval {
        get { current?.player.currentTime ?? 0 }
        set { current?.player.currentTime = newValue }
    }

    var isPlaying: Bool { current?.player.isPlaying ?? false }

    /// Плеер, его делегат и номер — делегат хранится здесь (`AVAudioPlayer.delegate` — слабая ссылка).
    private struct Slot {
        let id: Int
        let url: URL
        let player: AVAudioPlayer
        let delegate: PlayerDelegate
    }

    private var current: Slot?
    private var preloaded: Slot?
    private var lastID = 0
    /// Номер последнего запроса `load` — более ранние результаты отбрасываются.
    private var loadRequest = 0
    private var preloadRequest = 0
    private let logger = Logger(subsystem: "com.abumusaev.inabah", category: "audio")

    func load(url: URL) async throws -> TimeInterval {
        loadRequest += 1
        let request = loadRequest
        current?.player.stop()
        current = nil
        let player = try await Self.makePlayer(url: url)
        guard request == loadRequest, !Task.isCancelled else {
            player.stop()
            throw CancellationError()
        }
        let slot = makeSlot(url: url, player: player)
        current = slot
        return player.duration
    }

    func preload(url: URL) async {
        if preloaded?.url == url { return }
        preloadRequest += 1
        let request = preloadRequest
        preloaded = nil
        guard let player = try? await Self.makePlayer(url: url), request == preloadRequest else { return }
        preloaded = makeSlot(url: url, player: player)
    }

    func startPreloaded(url: URL, after delay: TimeInterval) -> TimeInterval? {
        guard let slot = preloaded, slot.url == url else { return nil }
        preloaded = nil
        // Отменяем незавершённую загрузку текущей записи — она больше не нужна.
        loadRequest += 1
        current?.player.stop()
        current = slot
        slot.player.rate = rate
        guard play(after: delay) else { return nil }
        return slot.player.duration
    }

    @discardableResult
    func play(after delay: TimeInterval) -> Bool {
        guard let player = current?.player else { return false }
        let started = delay > 0
            ? player.play(atTime: player.deviceCurrentTime + delay)
            : player.play()
        if !started { logger.error("AVAudioPlayer не запустился: \(self.current?.url.lastPathComponent ?? "-", privacy: .public)") }
        return started
    }

    func pause() { current?.player.pause() }

    func stop() {
        loadRequest += 1
        current?.player.stop()
        current?.player.currentTime = 0
        preloadRequest += 1
        preloaded = nil
    }

    private func makeSlot(url: URL, player: AVAudioPlayer) -> Slot {
        lastID += 1
        let id = lastID
        let delegate = PlayerDelegate { [weak self] event in
            self?.handle(event, from: id)
        }
        player.delegate = delegate
        player.rate = rate
        return Slot(id: id, url: url, player: player, delegate: delegate)
    }

    /// Событие учитывается, только если пришло от текущего плеера: сравнение по номеру,
    /// а не по адресу объекта (адрес освобождённого плеера может достаться новому).
    private func handle(_ event: PlayerDelegate.Event, from id: Int) {
        guard id == current?.id else { return }
        switch event {
        case .finished(let successfully): onFinish?(successfully)
        case .decodeError:
            logger.error("Ошибка декодирования: \(self.current?.url.lastPathComponent ?? "-", privacy: .public)")
            onDecodeError?()
        }
    }

    /// Открытие и декодирование файла — вне главного актора. Плеер создаётся здесь же
    /// и передаётся вызывающему (`sending`), больше нигде не используется.
    @concurrent
    private nonisolated static func makePlayer(url: URL) async throws -> sending AVAudioPlayer {
        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.enableRate = true
            player.prepareToPlay()
            return player
        } catch {
            throw AudioEngineError.cannotLoad(url.lastPathComponent)
        }
    }
}

/// Делегат одного плеера. AVFoundation вызывает его не на главном потоке —
/// событие с номером плеера (`Sendable`) переносится на главный актор.
private final class PlayerDelegate: NSObject, AVAudioPlayerDelegate {
    nonisolated enum Event: Sendable {
        case finished(successfully: Bool)
        case decodeError
    }

    private let onEvent: (Event) -> Void

    init(onEvent: @escaping (Event) -> Void) {
        self.onEvent = onEvent
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        send(.finished(successfully: flag))
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: (any Error)?) {
        send(.decodeError)
    }

    private nonisolated func send(_ event: Event) {
        Task { @MainActor [weak self] in
            self?.onEvent(event)
        }
    }
}
