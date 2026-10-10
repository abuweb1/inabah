import Foundation
import Observation

/// «Избранное» — хадисы, отмеченные закладкой, из всех сборников; отдельно от отметок
/// «прочитан» / «выучен» (`HadithProgress`), сброс прогресса его не трогает.
///
/// Ключи — `h_fav_{сборник}_{номер}` со значением `"1"`, как на Android (`docs/android/03-domain-logic.md`);
/// снятая закладка удаляет ключ. Порядок и время добавления не хранятся: экран «Избранное»
/// показывает хадисы по сборникам в порядке главной и по номеру.
@Observable
final class HadithFavorites {
    @ObservationIgnored private let defaults: UserDefaults
    /// Закладки в памяти — `UserDefaults` читается один раз при создании.
    private(set) var numbers: [HadithCollection: Set<Int>] = [:]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        numbers = Self.load(from: defaults)
    }

    var isEmpty: Bool { numbers.values.allSatisfy(\.isEmpty) }

    func isFavorite(_ id: HadithID) -> Bool {
        numbers[id.collection]?.contains(id.number) ?? false
    }

    func toggle(_ id: HadithID) {
        if isFavorite(id) {
            numbers[id.collection]?.remove(id.number)
            defaults.removeObject(forKey: Self.key(id))
        } else {
            numbers[id.collection, default: []].insert(id.number)
            defaults.set(Self.storedFlag, forKey: Self.key(id))
        }
    }

    /// Убрать все закладки — и те, что в память не попали (неизвестный сборник, номер 0).
    func clear() {
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix(Self.keyPrefix) {
            guard key.wholeMatch(of: Self.keyPattern) != nil else { continue }
            defaults.removeObject(forKey: key)
        }
        if !numbers.isEmpty { numbers = [:] }
    }

    // MARK: - Хранение

    static func key(_ id: HadithID) -> String { "h_fav_\(id.collection.rawValue)_\(id.number)" }

    private static let storedFlag = "1"
    private static let keyPrefix = "h_fav_"
    private static let keyPattern = /h_fav_(\w+)_(\d+)/

    private static func load(from defaults: UserDefaults) -> [HadithCollection: Set<Int>] {
        var result: [HadithCollection: Set<Int>] = [:]
        for (key, value) in defaults.dictionaryRepresentation() where key.hasPrefix(keyPrefix) {
            guard value as? String == storedFlag,
                  let match = key.wholeMatch(of: keyPattern),
                  let collection = HadithCollection(rawValue: String(match.1)),
                  let number = Int(match.2), number >= 1 else { continue }
            result[collection, default: []].insert(number)
        }
        return result
    }
}
