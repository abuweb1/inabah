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
    /// Статусы в памяти — `UserDefaults` читается один раз при создании, а не из каждого `body`.
    private var statuses: [HadithCollection: [Int: HadithStatus]] = [:]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        statuses = Self.load(from: defaults)
        applyRevisions()
    }

    /// Редакции сборников: при новой редакции хадисы под теми же номерами другие, и отметки
    /// сбрасываются один раз (1.1.1 — переработка къудси, docs/06-data-structure.md). На новой
    /// установке отметок нет — сброс ничего не делает, просто записывается редакция.
    static let revisions: [HadithCollection: Int] = [.qudsi: 2]

    static func revisionKey(_ collection: HadithCollection) -> String { "hadith.\(collection.rawValue).revision" }

    private func applyRevisions() {
        for (collection, revision) in Self.revisions where defaults.integer(forKey: Self.revisionKey(collection)) < revision {
            reset(collection)
            defaults.set(revision, forKey: Self.revisionKey(collection))
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

    /// Сброс всех отметок сборника («прочитан» и «выучен»), включая ключи, не попавшие в память
    /// (номер 0, значение не `"1"`); другие сборники и избранное (`h_fav_*`) не затрагиваются.
    func reset(_ collection: HadithCollection) {
        for key in defaults.dictionaryRepresentation().keys where key.hasPrefix(Self.keyPrefix) {
            guard let match = key.wholeMatch(of: Self.keyPattern), match.2 == collection.rawValue else { continue }
            defaults.removeObject(forKey: key)
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
    private static let keyPrefix = "h_"
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

    /// Один проход по `UserDefaults` (словарь включает системные домены — дорого строить его
    /// на каждый сборник); регулярное выражение — только для ключей с префиксом отметок.
    private static func load(from defaults: UserDefaults) -> [HadithCollection: [Int: HadithStatus]] {
        var result: [HadithCollection: [Int: HadithStatus]] = [:]
        for (key, value) in defaults.dictionaryRepresentation() where key.hasPrefix(keyPrefix) {
            guard value as? String == storedFlag,
                  let match = key.wholeMatch(of: keyPattern),
                  let collection = HadithCollection(rawValue: String(match.2)),
                  let number = Int(match.3), number >= 1 else { continue }
            // «Выучен» без «прочитан» (например, отмечено в старой версии) — тоже «выучен».
            if match.1 == "mem" {
                result[collection, default: [:]][number] = .memorized
            } else if result[collection]?[number] == nil {
                result[collection, default: [:]][number] = .read
            }
        }
        return result
    }
}
