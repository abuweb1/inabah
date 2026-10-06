import Foundation
import Observation

/// Время азкаров: «с — до» для каждого раздела. Сохраняется между запусками.
///
/// По умолчанию утренние — 5:00–12:00, вечерние — 17:00–02:00 (решение пользователя 2026-10-06).
/// Вне этого времени счёт не идёт в отметку и историю, на границах счётчики обнуляются
/// (`AzkarStore`). Новое время `AzkarStore` применяет, когда пользователь уходит с экрана
/// настроек, — прокрутка колеса времени не стирает прочитанное.
@Observable
final class AzkarWindowSettings {
    static func defaultWindow(for section: AzkarSection) -> AzkarWindow {
        switch section {
        case .morning: AzkarWindow(start: DayTime(hour: 5, minute: 0), end: DayTime(hour: 12, minute: 0))
        case .evening: AzkarWindow(start: DayTime(hour: 17, minute: 0), end: DayTime(hour: 2, minute: 0))
        }
    }

    private enum Edge: String {
        case start, end
    }

    private static func key(for section: AzkarSection, _ edge: Edge) -> String {
        "azkar.window.\(section.rawValue).\(edge.rawValue)"
    }

    /// Время обнуления версии 1.0.0 — у него другой смысл, поэтому оно не переносится.
    private static func legacyResetKey(for section: AzkarSection) -> String {
        "azkar.reset.\(section.rawValue)"
    }

    @ObservationIgnored private let defaults: UserDefaults

    private var windows: [AzkarSection: AzkarWindow] = [:]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        for section in AzkarSection.allCases {
            defaults.removeObject(forKey: Self.legacyResetKey(for: section))
            let fallback = Self.defaultWindow(for: section)
            let start = Self.storedTime(in: defaults, key: Self.key(for: section, .start)) ?? fallback.start
            let end = Self.storedTime(in: defaults, key: Self.key(for: section, .end)) ?? fallback.end
            windows[section] = start == end ? fallback : AzkarWindow(start: start, end: end)
        }
    }

    private static func storedTime(in defaults: UserDefaults, key: String) -> DayTime? {
        (defaults.object(forKey: key) as? Int).map(DayTime.init(minutesSinceMidnight:))
    }

    func window(for section: AzkarSection) -> AzkarWindow {
        windows[section] ?? Self.defaultWindow(for: section)
    }

    /// Начало, совпадающее с концом, не сохраняется: у окна не было бы длины.
    func setStart(_ time: DayTime, for section: AzkarSection) {
        let current = window(for: section)
        update(AzkarWindow(start: time, end: current.end), for: section)
    }

    func setEnd(_ time: DayTime, for section: AzkarSection) {
        let current = window(for: section)
        update(AzkarWindow(start: current.start, end: time), for: section)
    }

    private func update(_ window: AzkarWindow, for section: AzkarSection) {
        guard window.start != window.end, window != self.window(for: section) else { return }
        windows[section] = window
        defaults.set(window.start.minutesSinceMidnight, forKey: Self.key(for: section, .start))
        defaults.set(window.end.minutesSinceMidnight, forKey: Self.key(for: section, .end))
    }
}
