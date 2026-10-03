import Foundation
import Observation

/// Настройки режима «Прослушать все». Сохраняются между запусками.
@Observable
final class PlaylistSettings {
    static let pauseOptions: [Double] = [0, 1, 3, 5]
    static let rateOptions: [Float] = [0.75, 1, 1.25, 1.5]

    static let defaultRepeatsByCount = true
    static let defaultPause: Double = 1
    static let defaultRate: Float = 1

    private enum Key {
        static let repeatsByCount = "playlist.repeatsByCount"
        static let pause = "playlist.pauseBetween"
        static let rate = "playlist.rate"
    }

    @ObservationIgnored private let defaults: UserDefaults

    /// Каждый зикр звучит столько раз, сколько его положено читать; иначе — по одному разу.
    var repeatsByCount: Bool {
        didSet { defaults.set(repeatsByCount, forKey: Key.repeatsByCount) }
    }

    /// Пауза между записями, секунды (одно из `pauseOptions`).
    var pauseBetween: Double {
        didSet { defaults.set(pauseBetween, forKey: Key.pause) }
    }

    /// Скорость воспроизведения (одно из `rateOptions`).
    var rate: Float {
        didSet { defaults.set(rate, forKey: Key.rate) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        repeatsByCount = defaults.object(forKey: Key.repeatsByCount) as? Bool ?? Self.defaultRepeatsByCount
        let storedPause = defaults.object(forKey: Key.pause) as? Double
        pauseBetween = storedPause.flatMap { Self.pauseOptions.contains($0) ? $0 : nil } ?? Self.defaultPause
        let storedRate = (defaults.object(forKey: Key.rate) as? Double).map(Float.init)
        rate = storedRate.flatMap { Self.rateOptions.contains($0) ? $0 : nil } ?? Self.defaultRate
    }
}
