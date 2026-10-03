import Foundation
import Observation

/// Настройки чтения, общие для азкаров и хадисов. Сохраняются между запусками.
@Observable
final class ReadingSettings {
    /// Верхняя граница — на 4 шага больше прототипа (32): по просьбе пользователя для крупного чтения.
    static let arabicFontSizeRange: ClosedRange<Double> = 14...40
    static let arabicFontSizeStep: Double = 2
    static let defaultArabicFontSize: Double = 21

    private enum Key {
        static let arabicFontSize = "reading.arabicFontSize"
    }

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var arabicFontSize: Double {
        didSet { defaults.set(arabicFontSize, forKey: Key.arabicFontSize) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let stored = defaults.object(forKey: Key.arabicFontSize) as? Double
        arabicFontSize = (stored ?? Self.defaultArabicFontSize).clamped(to: Self.arabicFontSizeRange)
    }

    var canIncreaseArabicFontSize: Bool { arabicFontSize < Self.arabicFontSizeRange.upperBound }
    var canDecreaseArabicFontSize: Bool { arabicFontSize > Self.arabicFontSizeRange.lowerBound }

    func increaseArabicFontSize() { changeArabicFontSize(by: Self.arabicFontSizeStep) }
    func decreaseArabicFontSize() { changeArabicFontSize(by: -Self.arabicFontSizeStep) }

    private func changeArabicFontSize(by delta: Double) {
        arabicFontSize = (arabicFontSize + delta).clamped(to: Self.arabicFontSizeRange)
    }
}
