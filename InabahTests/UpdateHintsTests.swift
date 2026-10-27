import Foundation
import Testing
@testable import Inabah

/// Подсказка «Вышло обновление» — один раз на каждую новую версию магазина (2026-10-27).
@MainActor
@Suite("Подсказка «Вышло обновление»")
struct UpdateHintsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("UpdateHintsTests")
    }

    @Test("Один раз на версию, переживает перезапуск; следующая версия — снова")
    func oncePerVersion() {
        let hints = UpdateHints(defaults: defaults)
        #expect(hints.shouldHint("1.3.0"))

        hints.markHinted("1.3.0")

        #expect(!hints.shouldHint("1.3.0"))
        #expect(!UpdateHints(defaults: defaults).shouldHint("1.3.0"), "после перезапуска")
        #expect(UpdateHints(defaults: defaults).shouldHint("1.4.0"), "о новой версии - снова")
    }
}
