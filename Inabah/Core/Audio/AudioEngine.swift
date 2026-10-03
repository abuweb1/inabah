import Foundation

/// Движок воспроизведения одного файла с подготовкой следующего. Логика плеера
/// (`AudioPlayerController`) работает только с этим протоколом — в тестах подставляется заглушка.
///
/// Все методы, кроме загрузки, мгновенные: тяжёлая работа выполняется вне главного актора,
/// а её неудачи приходят асинхронно через `onError`.
protocol AudioEngine: AnyObject {
    /// Загружает файл как текущий и возвращает его длительность. Если за время загрузки
    /// запрошена другая запись (или стоп), бросает `CancellationError`: устаревший результат
    /// не подменяет новую запись.
    func load(url: URL) async throws -> TimeInterval
    /// Заранее готовит следующую запись (пока текущая играет).
    func preload(url: URL) async
    /// Делает подготовленную запись текущей и запускает её через `delay` секунд (пауза между
    /// зикрами — часть аудиодорожки, звук не прерывается и фон не засыпает).
    /// Возвращает длительность или `nil`, если эта запись не была подготовлена.
    func startPreloaded(url: URL, after delay: TimeInterval) -> TimeInterval?
    /// Запускает текущую запись через `delay` секунд. Если система не дала начать — `onError(.playbackFailed)`.
    func play(after delay: TimeInterval)
    func pause()
    func stop()
    var isPlaying: Bool { get }
    var currentTime: TimeInterval { get set }
    var rate: Float { get set }
    /// Текущая запись доиграла: `true` — нормально, `false` — с ошибкой. Вызывается на главном акторе.
    var onFinish: ((_ successfully: Bool) -> Void)? { get set }
    /// Ошибка текущей записи: декодирование (`.cannotLoad`) или отказ старта (`.playbackFailed`).
    /// Вызывается на главном акторе.
    var onError: ((AudioEngineError) -> Void)? { get set }
}

extension AudioEngine {
    func play() { play(after: 0) }
}

nonisolated enum AudioEngineError: Error, Equatable {
    /// Файл не открылся или не декодируется.
    case cannotLoad(String)
    /// Система не дала аудиосессию (например, идёт звонок).
    case sessionUnavailable
    /// Запись не удалось запустить.
    case playbackFailed
}

/// `AVAudioPlayer` за фасадом на главном акторе.
///
/// Вызовы `AVAudioPlayer` синхронно ждут аудиосервер (перемотка и стоп — до 75 мс на iPhone,
/// см. `docs/analysis/2026-10-03-instruments-trace.md`), поэтому сам плеер живёт
/// в `AudioPlaybackBackend`, а фасад:
/// - отвечает мгновенно из «зеркала»: позиция — `PlaybackClock`, подготовленная запись — её длительность;
/// - ставит команды в одну очередь (`AsyncStream` + одна задача-исполнитель) — порядок
///   «перемотка → пауза → стоп» сохраняется;
/// - нумерует «эпохи» (новая запись, стоп): события и ответы прошлой эпохи отбрасываются.
final class AVAudioEngineAdapter: AudioEngine {
    var onFinish: ((_ successfully: Bool) -> Void)?
    var onError: ((AudioEngineError) -> Void)?

    var rate: Float = 1 {
        didSet {
            clock.setRate(Double(rate), at: .now)
            send(.setRate(rate))
        }
    }

    var currentTime: TimeInterval {
        get { clock.time(at: .now) }
        set {
            clock.seek(to: newValue, at: .now)
            send(.seek(to: newValue))
        }
    }

    var isPlaying: Bool { clock.isRunning }

    private var clock = PlaybackClock()
    private var epoch = 0
    /// Подготовленная запись и её длительность — `startPreloaded` отвечает без ожидания бэкенда.
    private var preloadedTrack: (url: URL, duration: TimeInterval)?
    private var preloadRequest = 0
    private let commands: AsyncStream<AudioBackendCommand>.Continuation

    init() {
        let (events, eventSink) = AsyncStream.makeStream(of: AudioBackendEvent.self)
        let (commandStream, commands) = AsyncStream.makeStream(of: AudioBackendCommand.self)
        let backend = AudioPlaybackBackend(events: eventSink)
        self.commands = commands
        // Исполнитель команд: строго по одной, на бэкенде. Живёт, пока жив фасад (`deinit` закрывает очередь).
        Task { @concurrent in
            for await command in commandStream {
                await backend.execute(command)
            }
        }
        Task { [weak self] in
            for await event in events {
                self?.handle(event)
            }
        }
    }

    deinit {
        commands.finish()
    }

    func load(url: URL) async throws -> TimeInterval {
        epoch += 1
        let requestEpoch = epoch
        clock.reset(duration: 0)
        let duration = try await withCheckedThrowingContinuation { reply in
            send(.load(url, epoch: requestEpoch, reply: reply))
        }
        guard requestEpoch == epoch, !Task.isCancelled else { throw CancellationError() }
        clock.reset(duration: duration)
        return duration
    }

    func preload(url: URL) async {
        if preloadedTrack?.url == url { return }
        preloadRequest += 1
        let request = preloadRequest
        preloadedTrack = nil
        let duration = await withCheckedContinuation { reply in
            send(.preload(url, reply: reply))
        }
        guard let duration, request == preloadRequest else { return }
        preloadedTrack = (url, duration)
    }

    func startPreloaded(url: URL, after delay: TimeInterval) -> TimeInterval? {
        guard let track = preloadedTrack, track.url == url else { return nil }
        preloadedTrack = nil
        epoch += 1
        clock.reset(duration: track.duration)
        clock.start(after: delay, at: .now)
        send(.startPreloaded(url, after: delay, epoch: epoch))
        return track.duration
    }

    func play(after delay: TimeInterval) {
        clock.start(after: delay, at: .now)
        send(.play(after: delay, epoch: epoch))
    }

    func pause() {
        clock.pause(at: .now)
        send(.pause)
    }

    func stop() {
        epoch += 1
        clock.stop()
        preloadRequest += 1
        preloadedTrack = nil
        send(.stop(epoch: epoch))
    }

    private func send(_ command: AudioBackendCommand) {
        commands.yield(command)
    }

    private func handle(_ event: AudioBackendEvent) {
        switch event {
        case let .position(eventEpoch, time, instant):
            guard eventEpoch == epoch else { return }
            clock.sync(position: time, at: instant)
        case let .finished(eventEpoch, successfully):
            guard eventEpoch == epoch else { return }
            clock.pause(at: .now)
            onFinish?(successfully)
        case let .failed(eventEpoch, error):
            guard eventEpoch == epoch else { return }
            clock.pause(at: .now)
            onError?(error)
        }
    }
}
