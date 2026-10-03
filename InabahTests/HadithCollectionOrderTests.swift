import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Порядок сборников хадисов")
struct HadithCollectionOrderTests {
    /// Отдельный suite на каждый тест — тесты идут параллельно и не делят UserDefaults.
    private let defaults: UserDefaults
    private static let key = "hadith.collectionOrder"

    init() throws {
        defaults = try #require(UserDefaults(suiteName: "HadithCollectionOrderTests.\(UUID().uuidString)"))
    }

    @Test("По умолчанию — исходный порядок")
    func defaultOrder() {
        let order = HadithCollectionOrder(defaults: defaults)

        #expect(order.collections == [.nawawi, .qudsi, .ajurri])
        #expect(order.isDefault)
    }

    @Test("Перемещение сохраняется между запусками")
    func movePersists() {
        HadithCollectionOrder(defaults: defaults).move(fromOffsets: [2], toOffset: 0)

        let restored = HadithCollectionOrder(defaults: defaults)
        #expect(restored.collections == [.ajurri, .nawawi, .qudsi])
        #expect(!restored.isDefault)
    }

    @Test("Действия VoiceOver «Выше» / «Ниже»; за краями списка — ничего")
    func moveByOffset() {
        let order = HadithCollectionOrder(defaults: defaults)

        #expect(!order.canMove(.nawawi, by: -1))
        order.move(.nawawi, by: -1)
        #expect(order.isDefault)

        order.move(.ajurri, by: -1)
        #expect(order.collections == [.nawawi, .ajurri, .qudsi])
        order.move(.nawawi, by: 1)
        #expect(order.collections == [.ajurri, .nawawi, .qudsi])
        #expect(!order.canMove(.qudsi, by: 1))
        #expect(HadithCollectionOrder(defaults: defaults).collections == [.ajurri, .nawawi, .qudsi])
    }

    @Test("Восстановление исходного порядка")
    func restoreDefault() {
        let order = HadithCollectionOrder(defaults: defaults)
        order.move(fromOffsets: [0], toOffset: 3)

        order.restoreDefault()

        #expect(order.isDefault)
        #expect(HadithCollectionOrder(defaults: defaults).isDefault)
    }

    @Test("Неизвестные значения и повторы отбрасываются, недостающий сборник — в конец")
    func sanitizesStoredValue() {
        defaults.set(["ajurri", "unknown", "ajurri", "nawawi"], forKey: Self.key)

        #expect(HadithCollectionOrder(defaults: defaults).collections == [.ajurri, .nawawi, .qudsi])
    }
}
