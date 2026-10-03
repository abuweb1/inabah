import AVFoundation
import os

/// Команда бэкенду. «Эпоха» — номер, который фасад (`AVAudioEngineAdapter`) присваивает
/// записи, когда делает её текущей; по нему фасад отбрасывает устаревшие события.
nonisolated enum AudioBackendCommand: Sendable {
    case load(URL, epoch: Int, reply: CheckedContinuation<TimeInterval, any Error>)
    case preload(URL, reply: CheckedContinuation<TimeInterval?, Never>)
    case startPreloaded(URL, after: TimeInterval, epoch: Int)
    case play(after: TimeInterval, epoch: Int)
    case pause
    case stop(epoch: Int)
    case seek(to: TimeInterval)
    case setRate(Float)
}

/// Что сообщает бэкенд: события плеера и фактическая позиция для подстройки часов.
nonisolated enum AudioBackendEvent: Sendable {
    case finished(epoch: Int, successfully: Bool)
    case failed(epoch: Int, AudioEngineError)
    case position(epoch: Int, TimeInterval, at: ContinuousClock.Instant)
}

/// Единственный владелец `AVAudioPlayer`. Вызовы плеера (`play`, `stop`, `currentTime = …`,
/// создание и освобождение) синхронно ждут ответа аудиосервера — десятки миллисекунд,
/// поэтому они выполняются здесь, вне главного актора. Команды приходят строго по очереди
/// (см. `AVAudioEngineAdapter`) и выполняются целиком, без точек приостановки.
actor AudioPlaybackBackend {
    /// Плеер, его делегат и номер — делегат хранится здесь (`AVAudioPlayer.delegate` — слабая ссылка).
    private struct Slot {
        let id: Int
        let url: URL
        let player: AVAudioPlayer
        let delegate: PlayerDelegate
        var epoch: Int
    }

    private var current: Slot?
    private var preloaded: Slot?
    private var lastID = 0
    private var rate: Float = 1
    private let events: AsyncStream<AudioBackendEvent>.Continuation
    private let logger = Logger(subsystem: "com.abumusaev.inabah", category: "audio")

    init(events: AsyncStream<AudioBackendEvent>.Continuation) {
        self.events = events
    }

    deinit {
        events.finish()
    }

    func execute(_ command: AudioBackendCommand) {
        switch command {
        case let .load(url, epoch, reply):
            reply.resume(with: Result(catching: { try load(url: url, epoch: epoch) }))
        case let .preload(url, reply):
            reply.resume(returning: preload(url: url))
        case let .startPreloaded(url, delay, epoch):
            startPreloaded(url: url, after: delay, epoch: epoch)
        case let .play(delay, epoch):
            play(after: delay, epoch: epoch)
        case .pause:
            current?.player.pause()
            reportPosition()
        case let .stop(epoch):
            current?.player.stop()
            current?.player.currentTime = 0
            current?.epoch = epoch
            preloaded = nil
        case let .seek(time):
            current?.player.currentTime = time
            if current?.player.isPlaying == true { reportPosition() }
        case let .setRate(newRate):
            rate = newRate
            current?.player.rate = newRate
        }
    }

    // MARK: - Команды

    private func load(url: URL, epoch: Int) throws -> TimeInterval {
        current?.player.stop()
        current = nil
        let player = try makePlayer(url: url)
        current = makeSlot(url: url, player: player, epoch: epoch)
        return player.duration
    }

    private func preload(url: URL) -> TimeInterval? {
        if let preloaded, preloaded.url == url { return preloaded.player.duration }
        preloaded = nil
        guard let player = try? makePlayer(url: url) else { return nil }
        preloaded = makeSlot(url: url, player: player, epoch: 0)
        return player.duration
    }

    private func startPreloaded(url: URL, after delay: TimeInterval, epoch: Int) {
        guard var slot = preloaded, slot.url == url else {
            events.yield(.failed(epoch: epoch, .playbackFailed))
            return
        }
        preloaded = nil
        current?.player.stop()
        slot.epoch = epoch
        slot.player.rate = rate
        current = slot
        play(after: delay, epoch: epoch)
    }

    private func play(after delay: TimeInterval, epoch: Int) {
        guard let player = current?.player else {
            events.yield(.failed(epoch: epoch, .playbackFailed))
            return
        }
        let started = delay > 0
            ? player.play(atTime: player.deviceCurrentTime + delay)
            : player.play()
        guard started else {
            logger.error("AVAudioPlayer не запустился: \(self.current?.url.lastPathComponent ?? "-", privacy: .public)")
            events.yield(.failed(epoch: epoch, .playbackFailed))
            return
        }
        if delay == 0 { reportPosition() }
    }

    private func reportPosition() {
        guard let current else { return }
        events.yield(.position(epoch: current.epoch, current.player.currentTime, at: .now))
    }

    // MARK: - События плеера

    /// Событие учитывается, только если пришло от текущего плеера: сравнение по номеру,
    /// а не по адресу объекта (адрес освобождённого плеера может достаться новому).
    private func deliver(_ event: PlayerDelegate.Event, from id: Int) {
        guard let current, current.id == id else { return }
        switch event {
        case .finished(let successfully):
            events.yield(.finished(epoch: current.epoch, successfully: successfully))
        case .decodeError:
            logger.error("Ошибка декодирования: \(current.url.lastPathComponent, privacy: .public)")
            events.yield(.failed(epoch: current.epoch, .cannotLoad(current.url.lastPathComponent)))
        }
    }

    // MARK: - Плееры

    private func makePlayer(url: URL) throws -> AVAudioPlayer {
        do {
            let player = try AVAudioPlayer(contentsOf: url)
            player.enableRate = true
            player.prepareToPlay()
            return player
        } catch {
            throw AudioEngineError.cannotLoad(url.lastPathComponent)
        }
    }

    private func makeSlot(url: URL, player: AVAudioPlayer, epoch: Int) -> Slot {
        lastID += 1
        let id = lastID
        let delegate = PlayerDelegate { [weak self] event in
            Task { await self?.deliver(event, from: id) }
        }
        player.delegate = delegate
        player.rate = rate
        return Slot(id: id, url: url, player: player, delegate: delegate, epoch: epoch)
    }
}

/// Делегат одного плеера. AVFoundation вызывает его на своём потоке — событие (`Sendable`)
/// передаётся бэкенду.
private nonisolated final class PlayerDelegate: NSObject, AVAudioPlayerDelegate, Sendable {
    enum Event: Sendable {
        case finished(successfully: Bool)
        case decodeError
    }

    private let onEvent: @Sendable (Event) -> Void

    init(onEvent: @escaping @Sendable (Event) -> Void) {
        self.onEvent = onEvent
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        onEvent(.finished(successfully: flag))
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: (any Error)?) {
        onEvent(.decodeError)
    }
}
