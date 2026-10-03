import Foundation
import Observation

/// Палитра оформления, выбранная в настройках. Сохраняется между запусками.
@Observable
final class AppearanceSettings {
    private static let key = "appearance.themeStyle"

    @ObservationIgnored private let defaults: UserDefaults

    var style: ThemeStyle {
        didSet { defaults.set(style.rawValue, forKey: Self.key) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // Неизвестное или повреждённое значение — палитра по умолчанию.
        style = defaults.string(forKey: Self.key).flatMap(ThemeStyle.init(rawValue:)) ?? .sections
    }
}
