import Foundation
import Observation

/// Порядок карточек сборников на главной хадисов. Меняется в настройках, сохраняется между запусками.
@Observable
final class HadithCollectionOrder {
    static let defaultOrder = HadithCollection.allCases

    private static let key = "hadith.collectionOrder"

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var collections: [HadithCollection] {
        didSet { defaults.set(collections.map(\.rawValue), forKey: Self.key) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let stored = defaults.stringArray(forKey: Self.key) ?? []
        collections = Self.sanitized(stored.compactMap(HadithCollection.init(rawValue:)))
    }

    var isDefault: Bool { collections == Self.defaultOrder }

    /// Для `List.onMove`.
    func move(fromOffsets source: IndexSet, toOffset destination: Int) {
        collections.move(fromOffsets: source, toOffset: destination)
    }

    func restoreDefault() {
        guard !isDefault else { return }
        collections = Self.defaultOrder
    }

    /// Без повторов; сборники, которых нет в сохранённом списке (например, новый сборник
    /// в обновлении приложения), — в конец в исходном порядке.
    private static func sanitized(_ stored: [HadithCollection]) -> [HadithCollection] {
        var seen = Set<HadithCollection>()
        let unique = stored.filter { seen.insert($0).inserted }
        return unique + defaultOrder.filter { !seen.contains($0) }
    }
}
