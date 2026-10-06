import Foundation
import Observation

/// Плеер приложения: одиночные треки и плейлисты, один на всё приложение.
///
/// Одновременно звучит только одна запись: новый `play`/`playAll` заменяет текущую.
/// Плеер не знает про зикры — экраны передают ему `AudioTrack`.
/// `@MainActor` явно (а не только по умолчанию таргета): так класс гарантированно `Sendable`
/// и безопасно передаётся в обработчики пульта на экране блокировки.
///
/// Наблюдаемые свойства записываются только при реальном изменении (`assign`):
/// `@Observable` уведомляет и о записи того же значения, а от этих свойств зависит лента.
///
/// Видимость панели живёт здесь, а не в отдельной модели представления: `play` и «Прослушать все»
/// открывают плеер, а тикер позиции работает, только пока панель видна.
@MainActor
@Observable
final class AudioPlayerController {
    enum Mode: Equatable {
        /// Одна запись по кнопке ▶ на карточке. Ставится на паузу, когда приложение уходит в фон.
        case single
        /// «Прослушать все»: очередь с повторами, играет в фоне, пульт на экране блокировки.
        case playlist
    }

    /// Шаг перемотки, секунды. `nonisolated` — нужен и обработчикам пульта вне главного актора.
    nonisolated static let skipInterval: TimeInterval = 10
    /// Простой, после которого аудиосессия отпускается (музыка пользователя может продолжиться).
    private static let idleDeactivationDelay = Duration.seconds(2)

    private(set) var mode: Mode = .single
    /// Идентификатор запущенного плейлиста (например, `azkar.morning`); `nil` в одиночном режиме.
    private(set) var playlistID: String?
    private(set) var playback = PlaybackQueue()
    private(set) var isPlaying = false
    /// Запись (или весь плейлист) доиграла до конца.
    private(set) var isFinished = false
    /// Позиция для полосы прогресса. Обновляется, только пока панель видна (см. `updateTicker`).
    private(set) var currentTime: TimeInterval = 0
    private(set) var duration: TimeInterval = 0
    private(set) var isPanelVisible = false
    private(set) var error: AudioEngineError?

    @ObservationIgnored private let engine: any AudioEngine
    @ObservationIgnored private let session: (any AudioSessionHandling)?
    @ObservationIgnored private let nowPlaying: (any NowPlayingUpdating)?
    @ObservationIgnored private(set) var playbackRate: Float = 1
    @ObservationIgnored private var pauseBetweenItems: TimeInterval = 0
    @ObservationIgnored private var tickerTask: Task<Void, Never>?
    @ObservationIgnored private var loadTask: Task<Void, Never>?
    @ObservationIgnored private var loadGeneration = 0
    /// Поколение загрузки, которая ещё идёт или ждёт активации сессии. «Играть» в это время
    /// не стартует звук само — это сделает загрузка (иначе запись запускалась дважды и до
    /// активации, аудит 2026-10-06 §3.1). Новая загрузка, «стоп» или «закрыть» меняют
    /// `loadGeneration` — флаг сам становится недействительным.
    @ObservationIgnored private var startingGeneration: Int?
    private var isLoading: Bool { startingGeneration == loadGeneration }
    /// Старт после явной активации сессии (`startAfterActivation`).
    @ObservationIgnored private var startTask: Task<Void, Never>?
    @ObservationIgnored private var sessionTask: Task<Void, any Error>?
    @ObservationIgnored private var idleTask: Task<Void, Never>?
    /// Запись доиграла, пока плеер стоял на паузе (сигнал пришёл после нажатия «пауза»):
    /// следующий шаг выполняется при продолжении, а не сам собой.
    @ObservationIgnored private var pendingStep: PendingStep?
    @ObservationIgnored private var wasPlayingBeforeInterruption = false
    @ObservationIgnored private var isInBackground = false

    private enum PendingStep {
        case repeatCurrent
        case advance
    }

    init(
        engine: any AudioEngine = AVAudioEngineAdapter(),
        session: (any AudioSessionHandling)? = nil,
        nowPlaying: (any NowPlayingUpdating)? = nil
    ) {
        self.engine = engine
        self.session = session
        self.nowPlaying = nowPlaying
        engine.onFinish = { [weak self] successfully in self?.handleFinish(successfully: successfully) }
        engine.onError = { [weak self] error in self?.handleFailure(error) }
        // Часы подстроились по плееру — экран блокировки получает точное время.
        engine.onTimeCorrection = { [weak self] in self?.nowPlaying?.playbackDidChange() }
        session?.onEvent = { [weak self] event in self?.handle(event) }
        nowPlaying?.attach(to: self)
    }

    // MARK: - Состояние

    var currentItem: AudioQueueItem? { playback.current }
    var currentTrack: AudioTrack? { currentItem?.track }
    var hasTrack: Bool { currentItem != nil }
    var index: Int { playback.index }
    var queueCount: Int { playback.count }
    var repetition: Int { playback.repetition }
    var canGoPrevious: Bool { mode == .playlist && playback.hasPrevious }
    var canGoNext: Bool { mode == .playlist && playback.hasNext }
    /// Точная позиция прямо из движка (для экрана блокировки), без ожидания тикера.
    var engineCurrentTime: TimeInterval { engine.currentTime }

    /// Звучит ли (или ждёт паузы между записями) именно этот трек.
    func isActive(_ trackID: AudioTrack.ID) -> Bool {
        currentTrack?.id == trackID && !isFinished
    }

    /// Идёт (или стоит на паузе, но не доиграл) плейлист с этим идентификатором.
    func isPlaylistActive(_ id: String) -> Bool {
        mode == .playlist && playlistID == id && hasTrack && !isFinished
    }

    // MARK: - Запуск

    /// ▶ на карточке. Если этот трек уже загружен и не доиграл — только показать плеер,
    /// если доиграл — играть заново.
    func play(_ track: AudioTrack) {
        if mode == .single, currentTrack?.id == track.id, !isFinished, error == nil {
            showPanel()
            return
        }
        start(PlaybackQueue(items: [AudioQueueItem(track: track, repeatCount: 1)]),
              mode: .single, playlistID: nil, rate: 1, pause: 0)
    }

    /// «Прослушать все»: очередь с повторами, паузой между записями и скоростью.
    func playAll(
        _ items: [AudioQueueItem],
        id: String? = nil,
        rate: Float,
        pauseBetween: Duration,
        startAt startIndex: Int = 0
    ) {
        guard !items.isEmpty else { return }
        start(PlaybackQueue(items: items, index: startIndex),
              mode: .playlist, playlistID: id, rate: rate, pause: pauseBetween.seconds)
    }

    /// Скорость и пауза меняются на лету, пока плейлист играет.
    func updatePlaylist(rate: Float, pauseBetween: Duration) {
        guard mode == .playlist else { return }
        playbackRate = rate
        engine.rate = rate
        pauseBetweenItems = pauseBetween.seconds
        nowPlaying?.playbackDidChange()
    }

    private func start(_ queue: PlaybackQueue, mode: Mode, playlistID: String?, rate: Float, pause: TimeInterval) {
        assign(\.playback, queue)
        assign(\.mode, mode)
        assign(\.playlistID, playlistID)
        pauseBetweenItems = pause
        playbackRate = rate
        engine.rate = rate
        assign(\.isPanelVisible, true)
        loadAndPlayCurrent()
    }

    /// Загрузка файла и активация сессии идут вне главного актора: панель появляется сразу,
    /// звук стартует, когда файл готов. `isPlaying` — намерение пользователя: пауза во время
    /// загрузки отменяет старт. Устаревшая загрузка (другой ▶, «следующий», «закрыть»)
    /// отменяется и отбрасывается по номеру поколения.
    private func loadAndPlayCurrent(startAfter delay: TimeInterval = 0) {
        guard let item = currentItem else { return }
        assign(\.isFinished, false)
        assign(\.currentTime, 0)
        assign(\.duration, 0)
        assign(\.error, nil)
        pendingStep = nil
        cancelIdleDeactivation()
        loadTask?.cancel()
        loadGeneration += 1
        let generation = loadGeneration
        startingGeneration = generation
        setPlaying(true)
        loadTask = Task { [weak self] in
            await self?.performLoad(of: item, generation: generation, startAfter: delay)
        }
    }

    private func performLoad(of item: AudioQueueItem, generation: Int, startAfter delay: TimeInterval) async {
        defer {
            if startingGeneration == generation { startingGeneration = nil }
        }
        do {
            let loadedDuration = try await engine.load(url: item.track.url)
            guard generation == loadGeneration else { return }
            assign(\.duration, loadedDuration)
            // На паузе к концу загрузки сессию не активируем — музыка пользователя не
            // прерывается зря; активирует «играть» (`resume`).
            if isPlaying {
                try await enqueueSessionOperation { try await $0.activate() }.value
                guard generation == loadGeneration else { return }
                if isPlaying { engine.play(after: delay) }
            }
            nowPlaying?.trackDidChange()
            await preloadNextIfNeeded()
        } catch is CancellationError {
            return
        } catch {
            guard generation == loadGeneration else { return }
            handleFailure((error as? AudioEngineError) ?? .cannotLoad(item.track.url.lastPathComponent))
        }
    }

    /// Ждёт окончания текущей загрузки записи (для тестов и последовательных сценариев).
    func waitForLoading() async {
        await loadTask?.value
    }

    /// Следующая запись плейлиста готовится заранее — переход между зикрами без загрузки
    /// и без тишины в аудиотракте (иначе iOS может приостановить приложение в фоне).
    private func preloadNextIfNeeded() async {
        guard mode == .playlist, let next = playback.next else { return }
        await engine.preload(url: next.track.url)
    }

    /// Операции с аудиосессией — строго по очереди: задача ставится в цепочку сразу, в момент
    /// вызова (синхронно), поэтому деактивация после «закрыть» не обгонит активацию новой записи.
    @discardableResult
    private func enqueueSessionOperation(
        _ operation: @escaping (any AudioSessionHandling) async throws -> Void
    ) -> Task<Void, any Error> {
        let previous = sessionTask
        let session = session
        let task = Task<Void, any Error> {
            _ = await previous?.result
            guard let session else { return }
            try await operation(session)
        }
        sessionTask = task
        return task
    }

    // MARK: - Управление

    func togglePlayPause() {
        if isPlaying { pause() } else { resume() }
    }

    func pause() {
        guard isPlaying else { return }
        engine.pause()
        assign(\.currentTime, engine.currentTime)
        setPlaying(false)
    }

    func resume() {
        guard hasTrack, !isPlaying else { return }
        if isFinished || error != nil {
            restart()
            return
        }
        if let step = pendingStep {
            pendingStep = nil
            setPlaying(true)
            startAfterActivation { [weak self] in self?.perform(step) }
            return
        }
        // Запись ещё грузится — звук запустит сама загрузка, когда сессия станет активной.
        if isLoading {
            setPlaying(true)
            return
        }
        // Запись не загружена (загрузку прервали «стопом») — загрузить заново со стартом.
        guard duration > 0 else {
            loadAndPlayCurrent()
            return
        }
        setPlaying(true)
        startAfterActivation { [weak self] in self?.engine.play() }
    }

    /// Любое продолжение — только после явной активации сессии: после звонка, простоя или
    /// Siri неявная активация `AVAudioPlayer` ненадёжна, и сбой приходил как `.playbackFailed`
    /// (аудит 2026-10-06, §3.2). Пауза, пока сессия активируется, отменяет старт.
    private func startAfterActivation(_ action: @escaping () -> Void) {
        cancelIdleDeactivation()
        startTask?.cancel()
        // Без аудиосессии (превью, тесты) — сразу.
        guard session != nil else {
            action()
            return
        }
        let generation = loadGeneration
        let activation = enqueueSessionOperation { try await $0.activate() }
        startTask = Task { [weak self] in
            let result = await activation.result
            guard !Task.isCancelled, let self, generation == self.loadGeneration, self.isPlaying else { return }
            if case .failure = result {
                self.handleFailure(.sessionUnavailable)
                return
            }
            action()
        }
    }

    /// Остановить: позиция в начало записи (с первого повтора), плеер остаётся открытым.
    func stop() {
        guard hasTrack else { return }
        loadTask?.cancel()
        loadGeneration += 1
        engine.stop()
        var queue = playback
        queue.restartCurrent()
        assign(\.playback, queue)
        pendingStep = nil
        assign(\.currentTime, 0)
        assign(\.isFinished, false)
        setPlaying(false)
        scheduleIdleDeactivation()
    }

    /// Текущая запись с начала (в плейлисте — текущий зикр с первого повтора).
    func restart() {
        guard hasTrack else { return }
        var queue = playback
        queue.restartCurrent()
        assign(\.playback, queue)
        loadAndPlayCurrent()
    }

    /// Перемотка; цель в самом конце записи — то же, что запись доиграла.
    func seek(to time: TimeInterval) {
        guard hasTrack, duration > 0 else { return }
        let target = time.clamped(to: 0...duration)
        if target >= duration, isPlaying {
            handleFinish(successfully: true)
            return
        }
        engine.currentTime = target
        assign(\.currentTime, target)
        if isFinished, target < duration {
            assign(\.isFinished, false)
        }
        nowPlaying?.playbackDidChange()
    }

    func skip(by delta: TimeInterval) {
        seek(to: engine.currentTime + delta)
    }

    func next() {
        guard canGoNext else { return }
        var queue = playback
        _ = queue.moveNext()
        assign(\.playback, queue)
        loadAndPlayCurrent()
    }

    func previous() {
        guard canGoPrevious else { return }
        var queue = playback
        _ = queue.movePrevious()
        assign(\.playback, queue)
        loadAndPlayCurrent()
    }

    func showPanel() {
        guard hasTrack else { return }
        // Позиция не обновлялась, пока панель была скрыта, — сразу актуальная.
        assign(\.currentTime, engine.currentTime)
        assign(\.isPanelVisible, true)
        updateTicker()
    }

    /// Скрыть плеер — звук продолжает играть.
    func hidePanel() {
        assign(\.isPanelVisible, false)
        updateTicker()
    }

    /// Закрыть плеер: остановить и забыть очередь.
    func close() {
        loadTask?.cancel()
        loadTask = nil
        loadGeneration += 1
        engine.stop()
        setPlaying(false)
        assign(\.playback, PlaybackQueue())
        assign(\.playlistID, nil)
        assign(\.mode, .single)
        assign(\.currentTime, 0)
        assign(\.duration, 0)
        assign(\.isFinished, false)
        assign(\.isPanelVisible, false)
        assign(\.error, nil)
        pendingStep = nil
        cancelIdleDeactivation()
        enqueueSessionOperation { try await $0.deactivate() }
        nowPlaying?.trackDidChange()
    }

    /// Приложение ушло в фон: одиночная запись ставится на паузу, плейлист играет дальше.
    /// Позиция для полосы прогресса в фоне не нужна — тикер останавливается.
    func applicationDidEnterBackground() {
        isInBackground = true
        if mode == .single { pause() }
        updateTicker()
    }

    func applicationWillEnterForeground() {
        isInBackground = false
        assign(\.currentTime, engine.currentTime)
        updateTicker()
    }

    // MARK: - События

    private func handle(_ event: AudioSessionEvent) {
        switch event {
        case .interruptionBegan:
            wasPlayingBeforeInterruption = isPlaying
            pause()
        case .interruptionEnded(let shouldResume):
            // Возобновляем только плейлист — одиночная запись после звонка не должна заиграть сама.
            if shouldResume, wasPlayingBeforeInterruption, mode == .playlist { resume() }
            wasPlayingBeforeInterruption = false
        case .outputLost:
            pause()
        case .mediaServicesReset:
            // Плееры недействительны: подготовленная запись забывается, текущая загружается
            // заново с сохранением намерения.
            engine.discardPreloaded()
            guard hasTrack else { return }
            if isPlaying { loadAndPlayCurrent() } else { reloadPaused() }
        }
    }

    /// Загрузить текущую запись без старта (после «стоп» или сброса медиасервисов).
    private func reloadPaused() {
        guard let item = currentItem else { return }
        loadTask?.cancel()
        loadGeneration += 1
        let generation = loadGeneration
        loadTask = Task { [weak self] in
            guard let duration = try? await self?.engine.load(url: item.track.url),
                  let self, generation == self.loadGeneration else { return }
            self.assign(\.duration, duration)
        }
    }

    private func handleFinish(successfully: Bool) {
        guard hasTrack else { return }
        guard successfully else {
            handleFailure(.cannotLoad(currentTrack?.url.lastPathComponent ?? ""))
            return
        }
        assign(\.currentTime, duration)
        guard mode == .playlist else {
            finish()
            return
        }
        let step: PendingStep? = playback.hasMoreRepetitions ? .repeatCurrent : (playback.hasNext ? .advance : nil)
        guard let step else {
            finish()
            return
        }
        // Сигнал «доиграл» пришёл после паузы (или прерывания) — не играть самим.
        guard isPlaying else {
            pendingStep = step
            return
        }
        perform(step)
    }

    private func perform(_ step: PendingStep) {
        switch step {
        case .repeatCurrent:
            var queue = playback
            _ = queue.advanceRepetition()
            assign(\.playback, queue)
            engine.currentTime = 0
            assign(\.currentTime, 0)
            engine.play()
            nowPlaying?.playbackDidChange()
        case .advance:
            advanceToNext(after: pauseBetweenItems)
        }
    }

    /// Следующий зикр плейлиста: подготовленная запись запускается через паузу прямо в аудиотракте;
    /// если подготовить не успели — обычная загрузка со стартом через ту же паузу.
    private func advanceToNext(after delay: TimeInterval) {
        var queue = playback
        guard queue.moveNext(), let item = queue.current else {
            finish()
            return
        }
        assign(\.playback, queue)
        assign(\.currentTime, 0)
        if let loadedDuration = engine.startPreloaded(url: item.track.url, after: delay) {
            assign(\.duration, loadedDuration)
            assign(\.isFinished, false)
            assign(\.error, nil)
            setPlaying(true)
            nowPlaying?.trackDidChange()
            Task { [weak self] in await self?.preloadNextIfNeeded() }
        } else {
            loadAndPlayCurrent(startAfter: delay)
        }
    }

    /// Ошибка записи: показать её; в плейлисте битый файл пропускается — переход к следующей
    /// записи. Не запустилась заранее подготовленная запись — это не повод пропускать зикр:
    /// он загружается заново с той же паузой. Сбой старта (`.playbackFailed`) и недоступная
    /// сессия — дело среды (звонок, другое приложение), а не файла: плейлист встаёт на паузу
    /// на том же зикре, «играть» продолжит его (аудит 2026-10-06, §5.3).
    private func handleFailure(_ failure: AudioEngineError) {
        if failure == .preparedTrackFailed {
            loadAndPlayCurrent(startAfter: pauseBetweenItems)
            return
        }
        assign(\.error, failure)
        if mode == .playlist, playback.hasNext, case .cannotLoad = failure {
            advanceToNext(after: 0)
            return
        }
        engine.stop()
        setPlaying(false)
        scheduleIdleDeactivation()
    }

    private func finish() {
        engine.pause()
        assign(\.isFinished, true)
        setPlaying(false)
        scheduleIdleDeactivation()
    }

    // MARK: - Аудиосессия в простое

    /// Через пару секунд простоя (стоп, конец записи, ошибка) сессия отпускается —
    /// с `notifyOthers`, чтобы музыка или подкаст пользователя продолжились.
    private func scheduleIdleDeactivation() {
        cancelIdleDeactivation()
        idleTask = Task { [weak self] in
            try? await Task.sleep(for: Self.idleDeactivationDelay)
            guard !Task.isCancelled, let self, !self.isPlaying else { return }
            // Очередь сессии может выполнить деактивацию позже — если к тому моменту
            // пользователь снова нажал «играть», сессию не отпускаем.
            self.enqueueSessionOperation { [weak self] session in
                guard self?.isPlaying == false else { return }
                try await session.deactivate()
            }
        }
    }

    private func cancelIdleDeactivation() {
        idleTask?.cancel()
        idleTask = nil
    }

    // MARK: - Прогресс

    private func setPlaying(_ playing: Bool) {
        assign(\.isPlaying, playing)
        nowPlaying?.playbackDidChange()
        updateTicker()
    }

    /// Тикер позиции работает, только когда его результат кто-то видит:
    /// звук идёт, панель плеера открыта и приложение на экране.
    private func updateTicker() {
        let shouldRun = isPlaying && isPanelVisible && !isInBackground
        if shouldRun, tickerTask == nil {
            tickerTask = Task { [weak self] in
                while !Task.isCancelled {
                    self?.tick()
                    try? await Task.sleep(for: Motion.progressTick)
                }
            }
        } else if !shouldRun {
            tickerTask?.cancel()
            tickerTask = nil
        }
    }

    /// Позиция читается, только пока движок играет: после окончания записи и паузы позицию
    /// задаёт сам контроллер (`duration`, позиция паузы) — тикер её не перезаписывает.
    private func tick() {
        guard engine.isPlaying else { return }
        assign(\.currentTime, engine.currentTime)
    }

    /// Запись наблюдаемого свойства только при изменении значения — без лишних уведомлений.
    private func assign<Value: Equatable>(_ keyPath: ReferenceWritableKeyPath<AudioPlayerController, Value>, _ value: Value) {
        if self[keyPath: keyPath] != value {
            self[keyPath: keyPath] = value
        }
    }
}

private extension Duration {
    var seconds: TimeInterval {
        let (seconds, attoseconds) = components
        return TimeInterval(seconds) + TimeInterval(attoseconds) / 1e18
    }
}
