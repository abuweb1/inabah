import Foundation
import Observation

/// Подсказка «Вышло обновление» у значка «Обновить» (решение пользователя 2026-10-27): один раз на каждую
/// новую версию магазина. Помнит версию, о которой уже подсказали (`update.hintVersion`); ставится при показе
/// или нажатии значка.
@Observable
final class UpdateHints {
    private static let key = "update.hintVersion"

    private(set) var hintedVersion: String?

    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults) {
        self.defaults = defaults
        hintedVersion = defaults.string(forKey: Self.key)
    }

    /// О версии `version` ещё не подсказывали.
    func shouldHint(_ version: String) -> Bool {
        hintedVersion != version
    }

    func markHinted(_ version: String) {
        guard hintedVersion != version else { return }
        hintedVersion = version
        defaults.set(version, forKey: Self.key)
    }
}
