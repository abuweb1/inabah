import Foundation
import Observation

/// Сколько зикров раздела выполнено за одно время азкаров.
nonisolated struct AzkarDayRecord: Codable, Hashable, Sendable {
    let completed: Int
    let total: Int

    var fraction: Double { total == 0 ? 0 : Double(completed) / Double(total) }
}

/// История чтения азкаров: по дате начала окна и разделу — выполнено X из N.
///
/// Пишется только во время азкаров и сразу при каждом выполненном или сброшенном зикре —
/// закрытое системой приложение ничего не теряет. Основа будущего календаря отметок.
/// Хранится в `UserDefaults` (две короткие записи в день — десятки килобайт за год).
@Observable
final class AzkarHistory {
    private static let key = "azkar.history"

    @ObservationIgnored private let defaults: UserDefaults

    /// Дата окна (`yyyy-MM-dd`) → раздел (`AzkarSection.rawValue`) → запись.
    private var records: [String: [String: AzkarDayRecord]]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        records = defaults.data(forKey: Self.key)
            .flatMap { try? JSONDecoder().decode([String: [String: AzkarDayRecord]].self, from: $0) } ?? [:]
    }

    func record(of section: AzkarSection, on day: String) -> AzkarDayRecord? {
        records[day]?[section.rawValue]
    }

    /// Прогресс раздела в окне `day`. Ничего не выполнено — записи нет (как и у дня,
    /// когда раздел не открывали). Пишет только при изменении.
    func record(_ progress: SectionProgress, of section: AzkarSection, on day: String) {
        let record = progress.completed > 0
            ? AzkarDayRecord(completed: progress.completed, total: progress.total)
            : nil
        guard records[day]?[section.rawValue] != record else { return }
        var dayRecords = records[day] ?? [:]
        dayRecords[section.rawValue] = record
        records[day] = dayRecords.isEmpty ? nil : dayRecords
        save()
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(records) else { return }
        defaults.set(data, forKey: Self.key)
    }
}
