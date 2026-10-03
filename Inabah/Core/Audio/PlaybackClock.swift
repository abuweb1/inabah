import Foundation

/// Позиция воспроизведения, вычисляемая без обращения к плееру: последняя известная позиция
/// плюс прошедшее время с учётом скорости и отложенного старта.
///
/// Нужна, потому что `AVAudioPlayer` живёт вне главного актора (его вызовы синхронно ждут
/// аудиосервер), а полосе прогресса и экрану блокировки позиция нужна мгновенно.
/// Расхождение не накапливается: после ответа плеера часы подстраиваются (`sync`) —
/// в том числе на точный момент отложенного старта.
nonisolated struct PlaybackClock: Equatable, Sendable {
    typealias Instant = ContinuousClock.Instant

    private(set) var isRunning = false
    private(set) var rate: Double = 1
    private(set) var duration: TimeInterval = 0
    /// Позиция в момент `anchor` (пока часы стоят — просто позиция).
    private var position: TimeInterval = 0
    /// Момент, с которого позиция растёт; при отложенном старте — в будущем.
    private var anchor: Instant?

    init() {}

    func time(at now: Instant) -> TimeInterval {
        guard isRunning, let anchor, now > anchor else { return position }
        let elapsed = (now - anchor).timeInterval * rate
        return duration > 0 ? min(position + elapsed, duration) : position + elapsed
    }

    /// Новая запись: позиция 0, часы стоят, скорость сохраняется.
    mutating func reset(duration: TimeInterval) {
        isRunning = false
        position = 0
        anchor = nil
        self.duration = duration
    }

    /// Стоп: в начало записи, длительность сохраняется.
    mutating func stop() {
        reset(duration: duration)
    }

    /// Старт с текущей позиции через `delay` секунд.
    mutating func start(after delay: TimeInterval = 0, at now: Instant) {
        position = time(at: now)
        anchor = now + .seconds(max(0, delay))
        isRunning = true
    }

    mutating func pause(at now: Instant) {
        position = time(at: now)
        anchor = nil
        isRunning = false
    }

    /// Перемотка. Отложенный старт сохраняется: перемотка в паузе между записями её не отменяет.
    mutating func seek(to time: TimeInterval, at now: Instant) {
        position = time
        if isRunning { anchor = max(anchor ?? now, now) }
    }

    mutating func setRate(_ newRate: Double, at now: Instant) {
        if isRunning {
            position = time(at: now)
            anchor = max(anchor ?? now, now)
        }
        rate = newRate
    }

    /// Подстройка по фактической позиции плеера: `actual` — позиция в момент `instant`.
    /// Для отложенного старта `instant` — будущий момент начала звука: часы начнут отсчёт
    /// тогда же, когда плеер. Устаревшие отчёты отсеивает фасад (по номеру команды).
    mutating func sync(position actual: TimeInterval, at instant: Instant) {
        position = actual
        if isRunning { anchor = instant }
    }
}

private extension Duration {
    nonisolated var timeInterval: TimeInterval {
        let (seconds, attoseconds) = components
        return TimeInterval(seconds) + TimeInterval(attoseconds) / 1e18
    }
}
