import Foundation
import Observation

nonisolated enum HadithStatus: Hashable, Sendable {
    case none
    case read
    case memorized

    var isRead: Bool { self != .none }
    var isMemorized: Bool { self == .memorized }
}

/// Прогресс сборника: сколько хадисов прочитано и выучено (выученные входят в прочитанные).
nonisolated struct HadithCollectionProgress: Hashable, Sendable {
    let read: Int
    let memorized: Int
    let total: Int

    var readFraction: Double { total == 0 ? 0 : Double(read) / Double(total) }
}

/// Отметки «прочитан» / «выучен», сохраняются между запусками.
///
/// Ключи — как в прототипе (`h_read_{сборник}_{номер}`, `h_mem_…`, значение `"1"`), но номер — `id`
/// хадиса (1-based), а не индекс. Правило: «выучен» всегда означает и «прочитан» —
/// отметка «выучен» ставит «прочитан», снятие «прочитан» снимает и «выучен».
@Observable
final class HadithProgress {
    @ObservationIgnored private let defaults: UserDefaults
    /// Статусы в памяти — `UserDefaults` читается один раз на сборник, а не из каждого `body`.
    private var statuses: [HadithCollection: [Int: HadithStatus]] = [:]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        for collection in HadithCollection.allCases {
            statuses[collection] = Self.load(collection, from: defaults)
        }
    }

    func status(of id: HadithID) -> HadithStatus {
        statuses[id.collection]?[id.number] ?? .none
    }

    func toggleRead(_ id: HadithID) {
        set(status(of: id).isRead ? .none : .read, for: id)
    }

    func toggleMemorized(_ id: HadithID) {
        set(status(of: id).isMemorized ? .read : .memorized, for: id)
    }

    /// Сброс всех отметок сборника («прочитан» и «выучен»); другие сборники не затрагиваются.
    func reset(_ collection: HadithCollection) {
        guard let numbers = statuses[collection]?.keys, !numbers.isEmpty else { return }
        for number in numbers {
            let id = HadithID(collection: collection, number: number)
            defaults.removeObject(forKey: Self.readKey(id))
            defaults.removeObject(forKey: Self.memorizedKey(id))
        }
        statuses[collection] = [:]
    }

    func progress(in collection: HadithCollection, total: Int) -> HadithCollectionProgress {
        let values = statuses[collection, default: [:]].filter { (1...max(total, 1)).contains($0.key) }.values
        return HadithCollectionProgress(
            read: values.count(where: \.isRead),
            memorized: values.count(where: \.isMemorized),
            total: total
        )
    }

    // MARK: - Хранение

    static func readKey(_ id: HadithID) -> String { "h_read_\(id.collection.rawValue)_\(id.number)" }
    static func memorizedKey(_ id: HadithID) -> String { "h_mem_\(id.collection.rawValue)_\(id.number)" }

    private static let storedFlag = "1"
    private static let keyPattern = /h_(read|mem)_(\w+)_(\d+)/

    private func set(_ status: HadithStatus, for id: HadithID) {
        guard status != self.status(of: id) else { return }
        statuses[id.collection, default: [:]][id.number] = status == .none ? nil : status
        store(status.isRead, key: Self.readKey(id))
        store(status.isMemorized, key: Self.memorizedKey(id))
    }

    private func store(_ flag: Bool, key: String) {
        if flag {
            defaults.set(Self.storedFlag, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    private static func load(_ collection: HadithCollection, from defaults: UserDefaults) -> [Int: HadithStatus] {
        var result: [Int: HadithStatus] = [:]
        for (key, value) in defaults.dictionaryRepresentation() {
            guard value as? String == storedFlag,
                  let match = key.wholeMatch(of: keyPattern),
                  match.2 == collection.rawValue,
                  let number = Int(match.3), number >= 1 else { continue }
            // «Выучен» без «прочитан» (например, отмечено в старой версии) — тоже «выучен».
            if match.1 == "mem" {
                result[number] = .memorized
            } else if result[number] == nil {
                result[number] = .read
            }
        }
        return result
    }
}
