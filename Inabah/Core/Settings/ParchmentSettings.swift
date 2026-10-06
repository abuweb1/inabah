import Foundation
import Observation

/// Фон под арабским текстом, выбранный в настройках («Палитра» → «Фон арабского текста»).
/// Сохраняется между запусками; на тему влияет через `Theme.withParchment`.
@Observable
final class ParchmentSettings {
    private static let key = "appearance.parchment"

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var style: ParchmentStyle

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // Неизвестное или повреждённое значение — «Пергамент», как было.
        style = defaults.string(forKey: Self.key).flatMap(ParchmentStyle.init(rawValue:)) ?? .classic
    }

    /// Выбор фона. Повторный выбор того же ничего не делает: `@Observable` уведомляет и о записи
    /// того же значения — без проверки тема приложения пересчитывалась бы впустую.
    func select(_ newStyle: ParchmentStyle) {
        guard newStyle != style else { return }
        style = newStyle
        defaults.set(newStyle.rawValue, forKey: Self.key)
    }
}
