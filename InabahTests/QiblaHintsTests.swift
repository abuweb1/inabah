import Foundation
import Testing
@testable import Inabah

/// Подсказки «Къиблы» — по одному разу, ключи как на Android (`OnceFlag`).
@MainActor
@Suite("Къибла: подсказки")
struct QiblaHintsTests {
    private static func defaults() -> UserDefaults {
        let suite = "qibla-hints-\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defaults.removePersistentDomain(forName: suite)
        return defaults
    }

    @Test("Новая установка — обе подсказки впереди; показанная запоминается")
    func markSeen() {
        let defaults = Self.defaults()
        let hints = QiblaHints(defaults: defaults)
        #expect(!hints.entrySeen)
        #expect(!hints.helpSeen)

        hints.markEntrySeen()
        #expect(hints.entrySeen)
        #expect(!hints.helpSeen, "подсказки независимы")

        let reopened = QiblaHints(defaults: defaults)
        #expect(reopened.entrySeen, "после перезапуска не показывается")
        #expect(!reopened.helpSeen)
    }

    @Test("Ключи совпадают с Android")
    func keys() {
        let defaults = Self.defaults()
        let hints = QiblaHints(defaults: defaults)
        hints.markEntrySeen()
        hints.markHelpSeen()
        #expect(defaults.bool(forKey: "azkar.qiblaHint.seen"))
        #expect(defaults.bool(forKey: "qibla.helpHint.seen"))
    }
}
