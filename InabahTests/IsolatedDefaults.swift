import Foundation
import Testing

/// Отдельный набор `UserDefaults` на тест: тесты идут параллельно и не делят настройки.
/// Набор удаляется вместе с помощником (Swift Testing создаёт экземпляр сьюта на каждый тест) —
/// в симуляторе не копятся plist-файлы прошлых прогонов.
final class IsolatedDefaults {
    let defaults: UserDefaults
    private let suiteName: String

    init(_ name: String) throws {
        suiteName = "\(name).\(UUID().uuidString)"
        defaults = try #require(UserDefaults(suiteName: suiteName))
    }

    deinit {
        defaults.removePersistentDomain(forName: suiteName)
    }
}
