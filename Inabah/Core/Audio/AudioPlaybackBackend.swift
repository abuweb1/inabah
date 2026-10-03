import AVFoundation
import os

/// Команда бэкенду.
///
/// - «Эпоха» — номер записи, которую фасад сделал текущей (новая запись, стоп): по ней
///   отбрасываются устаревшие события «доиграл» / ошибка.
/// - «Ревизия» — номер команды, менявшей позицию: отчёт о позиции применяется, только если
///   после этой команды позицию на главном акторе больше не меняли.
nonisolated enum AudioBackendCommand: Sendable {
    case load(URL, epoch: Int, reply: CheckedContinuation<TimeInterval, any Error>)
    case preload(URL, reply: CheckedContinuation<TimeInterval?, Never>)
    case discardPreloaded
    case startPreloaded(URL, after: TimeInterval, epoch: Int, revision: Int)
    case play(after: TimeInterval, epoch: Int, revision: Int)
    case pause(revision: Int)
    case stop(epoch: Int)
    case seek(to: TimeInterval, revision: Int)
    case setRate(Float, revision: Int)
}

/// Что сообщает бэкенд: события плеера и фактическая позиция для подстройки часов.
nonisolated enum AudioBackendEvent: Sendable {
    case finished(epoch: Int, successfully: Bool)
    case failed(epoch: Int, AudioEngineError)
    /// Позиция `position` в момент `at`. Для отложенного старта `at` — момент начала звука
    /// (может быть в будущем): часы начинают отсчёт ровно тогда же, когда плеер.
    case position(revision: Int, TimeInterval, at: ContinuousClock.Instant)
}

/// Исполнитель команд плеера. Протокол — чтобы тесты фасада подставляли детерминированный бэкенд.
protocol AudioBackend: Actor {
    func execute(_ command: AudioBackendCommand)
}

/// Единственный владелец `AVAudioPlayer`. Вызовы плеера (`play`, `stop`, `currentTime = …`,
/// создание и освобождение) синхронно ждут ответа аудиосервера — десятки миллисекунд,
/// поэтому они выполняются здесь, вне главного актора. Команды приходят строго по очереди
/// (см. `AVAudioEngineAdapter`) и выполняются целиком, без точек приостановки.
///
/// У актора свой последовательный исполнитель: блокирующие вызовы плеера занимают его поток,
/// а не потоки общего пула (их столько же, сколько ядер, — на них идут и другие задачи).
actor AudioPlaybackBackend: AudioBackend {
    private nonisolated let queue = DispatchSerialQueue(label: "com.abumusaev.inabah.audio", qos: .userInitiated)

    nonisolated var unownedExecutor: UnownedSerialExecutor {
        queue.asUnownedSerialExecutor()
    }

    /// Событие делегата конкретного плеера.
    private nonisolated struct PlayerEvent: Sendable {
        let id: Int
        let event: PlayerDelegate.Event
    }

    /// Плеер, его делегат и номер — делегат хранится здесь (`AVAudioPlayer.delegate` — слабая ссылка).
    private struct Slot {
        var id: Int
        let url: URL
        let player: AVAudioPlayer
        var delegate: PlayerDelegate
        var epoch: Int
    }

    private var current: Slot?
    private var preloaded: Slot?
    private var lastID = 0
    private var rate: Float = 1
    private let events: AsyncStream<AudioBackendEvent>.Continuation
    /// События делегатов — одной очередью: «ошибка декодирования» и «доиграл» обрабатываются
    /// в том порядке, в котором их прислал плеер.
    private let playerEvents: AsyncStream<PlayerEvent>.Continuation
    private let logger = Logger(subsystem: "com.abumusaev.inabah", category: "audio")

    init(events: AsyncStream<AudioBackendEvent>.Continuation) {
        self.events = events
        let (stream, playerEvents) = AsyncStream.makeStream(of: PlayerEvent.self)
        self.playerEvents = playerEvents
        Task { [weak self] in
            for await event in stream {
                await self?.deliver(event.event, from: event.id)
            }
        }
    }

    deinit {
        playerEvents.finish()
        events.finish()
    }

    func execute(_ command: AudioBackendCommand) {
        switch command {
        case let .load(url, epoch, reply):
            reply.resume(with: Result(catching: { try load(url: url, epoch: epoch) }))
        case let .preload(url, reply):
            reply.resume(returning: preload(url: url))
        case .discardPreloaded:
            preloaded = nil
        case let .startPreloaded(url, delay, epoch, revision):
            startPreloaded(url: url, after: delay, epoch: epoch, revision: revision)
        case let .play(delay, epoch, revision):
            play(after: delay, epoch: epoch, revision: revision)
        case let .pause(revision):
            // Доигравший плеер уже сбросил позицию в 0 — его «паузу» не сообщаем,
            // иначе часы на главном акторе откатились бы с конца записи в начало.
            guard let player = current?.player, player.isPlaying else { return }
            player.pause()
            report(revision)
        case let .stop(epoch):
            stop(epoch: epoch)
        case let .seek(time, revision):
            current?.player.currentTime = time
            report(revision)
        case let .setRate(newRate, revision):
            rate = newRate
            current?.player.rate = newRate
            if current?.player.isPlaying == true { report(revision) }
        }
    }

    // MARK: - Команды

    private func load(url: URL, epoch: Int) throws -> TimeInterval {
        current?.player.stop()
        current = nil
        // Подготовка этой же записи (её `preload` пришёл раньше) больше не нужна — лишний плеер.
        if preloaded?.url == url { preloaded = nil }
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

    private func startPreloaded(url: URL, after delay: TimeInterval, epoch: Int, revision: Int) {
        guard var slot = preloaded, slot.url == url else {
            // Подготовленной записи нет (например, сброшена после сброса медиасервисов) —
            // контроллер загрузит её заново, а не пропустит.
            events.yield(.failed(epoch: epoch, .preparedTrackFailed))
            return
        }
        preloaded = nil
        current?.player.stop()
        slot.epoch = epoch
        slot.player.rate = rate
        current = slot
        play(after: delay, epoch: epoch, revision: revision, failure: .preparedTrackFailed)
    }

    private func play(
        after delay: TimeInterval,
        epoch: Int,
        revision: Int,
        failure: AudioEngineError = .playbackFailed
    ) {
        guard let player = current?.player else {
            events.yield(.failed(epoch: epoch, failure))
            return
        }
        let startDelay = max(0, delay)
        // Момент старта считаем здесь, рядом с `deviceCurrentTime`: часы фасада начнут отсчёт
        // тогда же, когда звук, даже если команда простояла в очереди.
        let startInstant = ContinuousClock.now + .seconds(startDelay)
        let started = startDelay > 0
            ? player.play(atTime: player.deviceCurrentTime + startDelay)
            : player.play()
        guard started else {
            logger.error("AVAudioPlayer не запустился: \(self.current?.url.lastPathComponent ?? "-", privacy: .public)")
            events.yield(.failed(epoch: epoch, failure))
            return
        }
        events.yield(.position(revision: revision, player.currentTime, at: startInstant))
    }

    /// Стоп: в начало записи. Слот получает новый номер и делегата — «доиграл», отправленный
    /// старым делегатом до стопа, не будет принят за конец уже остановленной записи.
    private func stop(epoch: Int) {
        preloaded = nil
        guard var slot = current else { return }
        slot.player.stop()
        slot.player.currentTime = 0
        lastID += 1
        slot.id = lastID
        slot.delegate = makeDelegate(id: lastID)
        slot.player.delegate = slot.delegate
        slot.epoch = epoch
        current = slot
    }

    private func report(_ revision: Int) {
        guard let current else { return }
        events.yield(.position(revision: revision, current.player.currentTime, at: .now))
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
        let delegate = makeDelegate(id: lastID)
        player.delegate = delegate
        player.rate = rate
        return Slot(id: lastID, url: url, player: player, delegate: delegate, epoch: epoch)
    }

    private func makeDelegate(id: Int) -> PlayerDelegate {
        let playerEvents = playerEvents
        return PlayerDelegate { event in
            playerEvents.yield(PlayerEvent(id: id, event: event))
        }
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
