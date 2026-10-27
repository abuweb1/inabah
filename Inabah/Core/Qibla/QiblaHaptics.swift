import Foundation

/// Решает, давать ли виброотклик: один на каждый новый вход в совпадение (аудит Android 2026-10-24). Счёт входов
/// растёт в `QiblaTracker`; у новой подписки (↻, возврат на экран) — свой номер и счёт с нуля, поэтому сравнение —
/// только внутри одного номера: старое значение после ↻ отклика не даёт, первый вход новой подписки — даёт.
nonisolated struct AlignmentHaptics {
    private var generation: Int?
    private var heard = 0

    mutating func shouldVibrate(_ pointer: QiblaPointer) -> Bool {
        if pointer.generation != generation {
            generation = pointer.generation
            heard = 0
        }
        let vibrate = pointer.alignedEntries > heard
        heard = pointer.alignedEntries
        return vibrate
    }
}

/// Градусы поворота для VoiceOver («поверните на 40°»).
nonisolated enum QiblaSpokenTurn {
    /// Шаг: точнее датчик не знает, и озвучка не меняется на каждый градус.
    static let step = 5
    private static let holdFraction = 0.75

    /// Шагом `step`, не меньше одного шага (не «на 0°»); прежнее значение `previous` держится, пока отклонение
    /// не уйдёт от него на три четверти шага (у 12,5° озвучка не прыгает 10°/15°).
    static func degrees(distance: Double, previous: Int?) -> Int {
        if let previous, abs(distance - Double(previous)) < Double(step) * holdFraction { return previous }
        return max(Int((distance / Double(step)).rounded()) * step, step)
    }
}
