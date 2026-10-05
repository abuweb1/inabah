import Foundation
import Observation

/// Время ежедневного обнуления прогресса азкаров. Сохраняется между запусками.
///
/// По умолчанию утренние обнуляются в 17:00 (после них — время вечерних), вечерние — в 02:00
/// (после полуночи, чтобы прочитанное поздно вечером не пропадало сразу).
@Observable
final class AzkarResetSettings {
    static func defaultTime(for section: AzkarSection) -> DayTime {
        switch section {
        case .morning: DayTime(hour: 17, minute: 0)
        case .evening: DayTime(hour: 2, minute: 0)
        }
    }

    private static func key(for section: AzkarSection) -> String {
        "azkar.reset.\(section.rawValue)"
    }

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private var onChange: (() -> Void)?

    private var times: [AzkarSection: DayTime] = [:]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        for section in AzkarSection.allCases {
            let stored = defaults.object(forKey: Self.key(for: section)) as? Int
            times[section] = stored.map(DayTime.init(minutesSinceMidnight:)) ?? Self.defaultTime(for: section)
        }
    }

    /// Подписка на смену времени. Подписчик один — `AzkarStore`, который пересчитывает периоды;
    /// вторая подписка заменила бы первую молча, поэтому запрещена.
    func onResetTimeChange(_ action: @escaping () -> Void) {
        assert(onChange == nil, "У AzkarResetSettings уже есть подписчик")
        onChange = action
    }

    func resetTime(for section: AzkarSection) -> DayTime {
        times[section] ?? Self.defaultTime(for: section)
    }

    func setResetTime(_ time: DayTime, for section: AzkarSection) {
        guard time != resetTime(for: section) else { return }
        times[section] = time
        defaults.set(time.minutesSinceMidnight, forKey: Self.key(for: section))
        onChange?()
    }
}
