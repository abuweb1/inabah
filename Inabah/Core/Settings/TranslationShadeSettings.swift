import Foundation
import Observation

/// «Фон перевода» («Палитра»): сдвиг фона карточек перевода от −1 (темнее) до +1 (светлее), 0 — как
/// задумано. Сохраняется между запусками; на тему влияет через `Theme.withTranslationShade`. Пишется,
/// когда палец отпустил ползунок, — не на каждом шаге.
@Observable
final class TranslationShadeSettings {
    private static let key = "appearance.translationShade"

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var value: Double

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        value = Self.clamped(defaults.double(forKey: Self.key))
    }

    /// Повтор того же значения ничего не делает: `@Observable` уведомляет и о записи того же значения.
    func set(_ newValue: Double) {
        let clamped = Self.clamped(newValue)
        guard clamped != value else { return }
        value = clamped
        defaults.set(clamped, forKey: Self.key)
    }

    /// Мусор (NaN) — «как задумано», вне диапазона — край.
    static func clamped(_ value: Double) -> Double {
        value.isNaN ? 0 : min(max(value, -1), 1)
    }
}
