import Foundation

/// «Состав азкаров» (Android-first 2026-10-24): какие зикры раздела читать и в каком порядке.
///
/// Набор по умолчанию — все зикры без `optional` в порядке данных; он не хранится и следует за данными.
/// Другой набор хранится номерами через запятую — `azkar.composition.<раздел>`, а в `….removed` —
/// остальные номера раздела на момент сохранения: зикр без `optional`, появившийся в данных позже,
/// встанет в конец набора, а убранный пользователем не вернётся. Ключи и правила — как на Android.
final class AzkarComposition {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// Номера набора раздела по порядку.
    func numbers(for section: AzkarSection, catalog: [Zikr]) -> [Int] {
        Self.resolve(
            stored: Self.parse(defaults.string(forKey: Self.key(for: section))),
            removed: Self.parse(defaults.string(forKey: Self.removedKey(for: section))),
            catalog: catalog
        )
    }

    /// Сохранить набор. Пустой не сохраняется (последний зикр убрать нельзя), набор по умолчанию —
    /// удаляет ключи.
    func set(_ numbers: [Int], for section: AzkarSection, catalog: [Zikr]) {
        let known = Set(catalog.map(\.number))
        var seen = Set<Int>()
        let set = numbers.filter { known.contains($0) && seen.insert($0).inserted }
        guard !set.isEmpty else { return }
        if set == Self.defaultNumbers(in: catalog) {
            defaults.removeObject(forKey: Self.key(for: section))
            defaults.removeObject(forKey: Self.removedKey(for: section))
        } else {
            let removed = catalog.map(\.number).filter { !seen.contains($0) }
            defaults.set(Self.format(set), forKey: Self.key(for: section))
            defaults.set(Self.format(removed), forKey: Self.removedKey(for: section))
        }
    }

    nonisolated static func defaultNumbers(in catalog: [Zikr]) -> [Int] {
        catalog.filter { !$0.isOptional }.map(\.number)
    }

    /// Сохранённый порядок (неизвестные номера и повторы отброшены), в конце — зикры без `optional`,
    /// которых нет ни в наборе, ни в убранных. Ничего не сохранено или осталось пусто — по умолчанию.
    nonisolated static func resolve(stored: [Int], removed: [Int], catalog: [Zikr]) -> [Int] {
        let known = Set(catalog.map(\.number))
        var seen = Set<Int>()
        let kept = stored.filter { known.contains($0) && seen.insert($0).inserted }
        guard !kept.isEmpty else { return defaultNumbers(in: catalog) }
        let removedSet = Set(removed)
        let appeared = defaultNumbers(in: catalog).filter { !seen.contains($0) && !removedSet.contains($0) }
        return kept + appeared
    }

    private static func key(for section: AzkarSection) -> String {
        "azkar.composition.\(section.rawValue)"
    }

    private static func removedKey(for section: AzkarSection) -> String {
        key(for: section) + ".removed"
    }

    private static func parse(_ value: String?) -> [Int] {
        (value ?? "").split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
    }

    private static func format(_ numbers: [Int]) -> String {
        numbers.map(String.init).joined(separator: ",")
    }
}
