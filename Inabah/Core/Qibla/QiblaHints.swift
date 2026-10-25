import Foundation
import Observation

/// Подсказки «Къиблы» — по одному разу (Android `OnceFlag`, те же ключи): у кнопки входа на главной «Азкаров»
/// и у ⓘ «Как повысить точность». Признак ставится при показе или нажатии кнопки.
@Observable
final class QiblaHints {
    enum Key {
        static let entry = "azkar.qiblaHint.seen"
        static let help = "qibla.helpHint.seen"
    }

    private(set) var entrySeen: Bool
    private(set) var helpSeen: Bool

    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults) {
        self.defaults = defaults
        entrySeen = defaults.bool(forKey: Key.entry)
        helpSeen = defaults.bool(forKey: Key.help)
    }

    func markEntrySeen() {
        guard !entrySeen else { return }
        entrySeen = true
        defaults.set(true, forKey: Key.entry)
    }

    func markHelpSeen() {
        guard !helpSeen else { return }
        helpSeen = true
        defaults.set(true, forKey: Key.help)
    }
}
