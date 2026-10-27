import Foundation
import Testing
@testable import Inabah

/// «Состав азкаров»: какие зикры раздела читать и в каком порядке (как `AzkarCompositionTest` на Android).
@MainActor
@Suite("Состав азкаров")
struct AzkarCompositionTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("AzkarCompositionTests")
    }

    /// Раздел 1…5, 4 и 5 — не в наборе по умолчанию.
    private static let catalog = catalog(optional: [4, 5], count: 5)

    private static func catalog(optional: Set<Int>, count: Int) -> [Zikr] {
        (1...count).map { number in
            Zikr(
                id: ZikrID(section: .morning, number: number),
                arabic: "سُبْحَانَ اللَّهِ",
                repetitions: 1,
                translation: nil,
                isOptional: optional.contains(number)
            )
        }
    }

    @Test("По умолчанию — зикры без optional в порядке данных")
    func defaultSet() {
        let composition = AzkarComposition(defaults: defaults)

        #expect(composition.numbers(for: .morning, catalog: Self.catalog) == [1, 2, 3])
    }

    @Test("Сохранённый порядок восстанавливается, вечер — отдельно")
    func storedOrder() {
        AzkarComposition(defaults: defaults).set([3, 5, 1], for: .morning, catalog: Self.catalog)
        let composition = AzkarComposition(defaults: defaults)

        #expect(composition.numbers(for: .morning, catalog: Self.catalog) == [3, 5, 1])
        #expect(composition.numbers(for: .evening, catalog: Self.catalog) == [1, 2, 3])
    }

    @Test("Неизвестные номера и повторы отброшены")
    func unknownAndDuplicates() {
        #expect(AzkarComposition.resolve(stored: [2, 9, 2, 1], removed: [3, 4, 5], catalog: Self.catalog) == [2, 1])
    }

    @Test("Новый зикр без optional встаёт в конец, убранный не возвращается, новый optional не добавляется")
    func newAzkarInData() {
        let grown = Self.catalog(optional: [4, 5, 7], count: 7)

        // Сохранено при пяти зикрах: в наборе 3 и 1, убраны 2, 4, 5.
        #expect(AzkarComposition.resolve(stored: [3, 1], removed: [2, 4, 5], catalog: grown) == [3, 1, 6])
    }

    @Test("Пустой или полностью неизвестный набор — по умолчанию")
    func emptyFallsBack() {
        #expect(AzkarComposition.resolve(stored: [], removed: [1, 2], catalog: Self.catalog) == [1, 2, 3])
        #expect(AzkarComposition.resolve(stored: [42], removed: [], catalog: Self.catalog) == [1, 2, 3])
    }

    @Test("Набор по умолчанию не хранится — ключи удаляются")
    func defaultNotStored() {
        let composition = AzkarComposition(defaults: defaults)
        composition.set([2, 1], for: .morning, catalog: Self.catalog)
        #expect(defaults.string(forKey: "azkar.composition.morning") == "2,1")
        #expect(defaults.string(forKey: "azkar.composition.morning.removed") == "3,4,5")

        composition.set([1, 2, 3], for: .morning, catalog: Self.catalog)

        #expect(defaults.object(forKey: "azkar.composition.morning") == nil)
        #expect(defaults.object(forKey: "azkar.composition.morning.removed") == nil)
    }

    @Test("Пустой набор не сохраняется")
    func emptyNotStored() {
        let composition = AzkarComposition(defaults: defaults)
        composition.set([2], for: .morning, catalog: Self.catalog)

        composition.set([], for: .morning, catalog: Self.catalog)

        #expect(composition.numbers(for: .morning, catalog: Self.catalog) == [2])
    }
}
