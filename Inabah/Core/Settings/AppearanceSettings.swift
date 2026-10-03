import Foundation
import Observation

/// Палитра оформления, выбранная в настройках. Сохраняется между запусками.
@Observable
final class AppearanceSettings {
    private static let key = "appearance.themeStyle"

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var style: ThemeStyle

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // Неизвестное или повреждённое значение — палитра по умолчанию.
        style = defaults.string(forKey: Self.key).flatMap(ThemeStyle.init(rawValue:)) ?? .sections
    }

    /// Выбор палитры. Повторный выбор той же ничего не делает: `@Observable` уведомляет и о записи
    /// того же значения — без проверки вся тема приложения пересчитывалась бы впустую.
    func select(_ newStyle: ThemeStyle) {
        guard newStyle != style else { return }
        style = newStyle
        defaults.set(newStyle.rawValue, forKey: Self.key)
    }
}
