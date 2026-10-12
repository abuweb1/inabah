import Foundation
import Observation

/// Сколько зикров раздела выполнено за одно время азкаров.
nonisolated struct AzkarDayRecord: Codable, Hashable, Sendable {
    let completed: Int
    let total: Int

    var fraction: Double { total == 0 ? 0 : Double(completed) / Double(total) }

    /// Раздел прочитан полностью — половина дня в календаре; то же правило у «ещё не прочитаны».
    var isComplete: Bool { total > 0 && completed >= total }
}

/// История чтения азкаров: по дате начала окна и разделу — выполнено X из N.
///
/// Пишется только во время азкаров и сразу при каждом выполненном или сброшенном зикре —
/// закрытое системой приложение ничего не теряет. Основа календаря и серии дней (`AzkarStreak`).
/// Хранится в `UserDefaults` (две короткие записи в день — десятки килобайт за год).
@Observable
final class AzkarHistory {
    private static let key = "azkar.history"
    /// Нечитаемая история, отложенная для ручного восстановления (как на Android).
    private static let corruptKey = "azkar.history.corrupt"

    @ObservationIgnored private let defaults: UserDefaults

    /// Дата окна (`yyyy-MM-dd`) → раздел (`AzkarSection.rawValue`) → запись.
    @ObservationIgnored private var records: [String: [String: AzkarDayRecord]]

    /// Дни, за которые что-то прочитано, — для календаря и серии; обновляется при каждой записи и сбросе.
    private(set) var days: [AzkarCalendarDay: AzkarDay]

    /// Нечитаемое сохранение — пустая история, а прежние байты один раз откладываются под
    /// `azkar.history.corrupt`: первая новая запись не уничтожает их без следа (аудит 2026-10-06).
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        var records: [String: [String: AzkarDayRecord]] = [:]
        if let data = defaults.data(forKey: Self.key) {
            if let decoded = try? JSONDecoder().decode([String: [String: AzkarDayRecord]].self, from: data) {
                records = decoded
            } else if defaults.object(forKey: Self.corruptKey) == nil {
                defaults.set(data, forKey: Self.corruptKey)
            }
        }
        self.records = records
        days = Self.days(from: records)
    }

    var isEmpty: Bool { days.isEmpty }

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
        days = Self.days(from: records)
    }

    /// Сброс из настроек: календарь и серия начинаются заново, отложенная нечитаемая копия — тоже
    /// удаляется. Счётчики разделов не меняются — раздел, который читают сейчас, вернётся в историю
    /// со следующим выполненным или сброшенным зикром.
    func clear() {
        records = [:]
        defaults.removeObject(forKey: Self.key)
        defaults.removeObject(forKey: Self.corruptKey)
        if !days.isEmpty { days = [:] }
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(records) else { return }
        defaults.set(data, forKey: Self.key)
    }

    /// Типизированный вид: неразборчивые даты и неизвестные разделы пропускаются, но в хранилище остаются.
    private static func days(from records: [String: [String: AzkarDayRecord]]) -> [AzkarCalendarDay: AzkarDay] {
        var days: [AzkarCalendarDay: AzkarDay] = [:]
        for (key, sections) in records {
            guard let day = AzkarCalendarDay(key: key) else { continue }
            let value = AzkarDay(
                morning: sections[AzkarSection.morning.rawValue],
                evening: sections[AzkarSection.evening.rawValue]
            )
            if value.morning != nil || value.evening != nil {
                days[day] = value
            }
        }
        return days
    }
}
