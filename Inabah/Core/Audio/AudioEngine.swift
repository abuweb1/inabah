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
    /// Забывает подготовленную запись (например, после сброса медиасервисов её плеер недействителен).
    func discardPreloaded()
    /// Делает подготовленную запись текущей и запускает её через `delay` секунд (пауза между
    /// зикрами — часть аудиодорожки, звук не прерывается и фон не засыпает).
    /// Возвращает длительность или `nil`, если эта запись не была подготовлена.
    /// Если запустить не удалось — `onError(.preparedTrackFailed)`.
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
    /// Ошибка текущей записи: декодирование (`.cannotLoad`), отказ старта (`.playbackFailed`,
    /// `.preparedTrackFailed`). Вызывается на главном акторе.
    var onError: ((AudioEngineError) -> Void)? { get set }
    /// Позиция заметно поправлена по фактической позиции плеера — экран блокировки
    /// стоит обновить. Вызывается на главном акторе.
    var onTimeCorrection: (() -> Void)? { get set }
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
    /// Не запустилась заранее подготовленная запись — её стоит загрузить заново, а не пропускать.
    case preparedTrackFailed
}

/// `AVAudioPlayer` за фасадом на главном акторе.
///
/// Вызовы `AVAudioPlayer` синхронно ждут аудиосервер (перемотка и стоп — до 75 мс на iPhone,
/// см. `docs/analysis/2026-10-03-instruments-trace.md`), поэтому сам плеер живёт
/// в бэкенде (`AudioPlaybackBackend`), а фасад:
/// - отвечает мгновенно из «зеркала»: позиция — `PlaybackClock`, подготовленная запись — её длительность;
/// - ставит команды в одну очередь (`AsyncStream` + одна задача-исполнитель) — порядок
///   «перемотка → пауза → стоп» сохраняется;
/// - нумерует «эпохи» (новая запись, стоп) — события «доиграл»/ошибка прошлой эпохи отбрасываются;
/// - нумерует «ревизии» (каждая команда, меняющая позицию) — отчёт о позиции применяется,
///   только если после его команды позицию больше не меняли (пауза → сразу перемотка).
final class AVAudioEngineAdapter: AudioEngine {
    var onFinish: ((_ successfully: Bool) -> Void)?
    var onError: ((AudioEngineError) -> Void)?
    var onTimeCorrection: (() -> Void)?

    /// Поправка часов, о которой стоит сообщить (`onTimeCorrection`): мелкие подстройки
    /// экран блокировки догоняет сам.
    private static let reportedCorrection: TimeInterval = 0.5

    var rate: Float = 1 {
        didSet {
            let revision = nextRevision()
            clock.setRate(Double(rate), at: .now)
            send(.setRate(rate, revision: revision))
        }
    }

    var currentTime: TimeInterval {
        get { clock.time(at: .now) }
        set {
            let revision = nextRevision()
            clock.seek(to: newValue, at: .now)
            send(.seek(to: newValue, revision: revision))
        }
    }

    var isPlaying: Bool { clock.isRunning }

    private var clock = PlaybackClock()
    private var epoch = 0
    private var revision = 0
    /// Подготовленная запись и её длительность — `startPreloaded` отвечает без ожидания бэкенда.
    private var preloadedTrack: (url: URL, duration: TimeInterval)?
    private var preloadRequest = 0
    /// Запись, подготовка которой ещё идёт в бэкенде.
    private var preloadingURL: URL?
    private let commands: AsyncStream<AudioBackendCommand>.Continuation

    /// - Parameter makeBackend: исполнитель команд; тесты подставляют свой. События он
    ///   отправляет в переданный поток.
    init(makeBackend: (AsyncStream<AudioBackendEvent>.Continuation) -> any AudioBackend = { AudioPlaybackBackend(events: $0) }) {
        let (events, eventSink) = AsyncStream.makeStream(of: AudioBackendEvent.self)
        let (commandStream, commands) = AsyncStream.makeStream(of: AudioBackendCommand.self)
        let backend = makeBackend(eventSink)
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
        // Та же запись готовится заранее — бэкенд отбросит подготовленный экземпляр, фасад забывает о нём.
        if preloadingURL == url || preloadedTrack?.url == url {
            preloadRequest += 1
            preloadingURL = nil
            preloadedTrack = nil
        }
        epoch += 1
        _ = nextRevision()
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
        preloadingURL = url
        let duration = await withCheckedContinuation { reply in
            send(.preload(url, reply: reply))
        }
        guard request == preloadRequest else { return }
        preloadingURL = nil
        guard let duration else { return }
        preloadedTrack = (url, duration)
    }

    func discardPreloaded() {
        preloadRequest += 1
        preloadingURL = nil
        preloadedTrack = nil
        send(.discardPreloaded)
    }

    func startPreloaded(url: URL, after delay: TimeInterval) -> TimeInterval? {
        guard let track = preloadedTrack, track.url == url else { return nil }
        preloadedTrack = nil
        epoch += 1
        let revision = nextRevision()
        clock.reset(duration: track.duration)
        clock.start(after: delay, at: .now)
        send(.startPreloaded(url, after: delay, epoch: epoch, revision: revision))
        return track.duration
    }

    func play(after delay: TimeInterval) {
        let revision = nextRevision()
        clock.start(after: delay, at: .now)
        send(.play(after: delay, epoch: epoch, revision: revision))
    }

    func pause() {
        let revision = nextRevision()
        clock.pause(at: .now)
        send(.pause(revision: revision))
    }

    func stop() {
        epoch += 1
        _ = nextRevision()
        clock.stop()
        preloadRequest += 1
        preloadingURL = nil
        preloadedTrack = nil
        send(.stop(epoch: epoch))
    }

    private func nextRevision() -> Int {
        revision += 1
        return revision
    }

    private func send(_ command: AudioBackendCommand) {
        commands.yield(command)
    }

    private func handle(_ event: AudioBackendEvent) {
        switch event {
        case let .position(eventRevision, time, instant):
            guard eventRevision == revision else { return }
            let now = ContinuousClock.now
            let before = clock.time(at: now)
            clock.sync(position: time, at: instant)
            if abs(clock.time(at: now) - before) > Self.reportedCorrection {
                onTimeCorrection?()
            }
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
