import Foundation
import Testing
@testable import Inabah

/// История азкаров для календаря и серии: дни по дате окна, сброс, нечитаемое сохранение.
/// Те же случаи, что `AzkarHistoryTest` на Android.
@Suite("История азкаров")
@MainActor
struct AzkarHistoryTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    private let october11 = AzkarCalendarDay(year: 2026, month: 10, day: 11)!

    init() throws {
        storage = try IsolatedDefaults("AzkarHistoryTests")
    }

    @Test("Утренние и вечерние за один день независимы")
    func sectionsIndependent() {
        let history = AzkarHistory(defaults: defaults)
        history.record(SectionProgress(completed: 16, total: 16), of: .morning, on: "2026-10-11")
        history.record(SectionProgress(completed: 3, total: 16), of: .evening, on: "2026-10-11")
        history.record(SectionProgress(completed: 0, total: 16), of: .evening, on: "2026-10-11")

        let day = AzkarHistory(defaults: defaults).days[october11]
        #expect(day == AzkarDay(morning: AzkarDayRecord(completed: 16, total: 16)))
    }

    @Test("Дни — разделы по дате, обновляются при записи; неразборчивые даты пропускаются, но хранятся")
    func daysFollowRecords() throws {
        defaults.set(Data(#"{"not-a-day":{"morning":{"completed":1,"total":2}}}"#.utf8), forKey: "azkar.history")
        let history = AzkarHistory(defaults: defaults)
        #expect(history.isEmpty)

        history.record(SectionProgress(completed: 2, total: 16), of: .evening, on: "2026-10-11")

        #expect(history.days == [october11: AzkarDay(evening: AzkarDayRecord(completed: 2, total: 16))])
        #expect(AzkarHistory(defaults: defaults).record(of: .morning, on: "not-a-day") == AzkarDayRecord(completed: 1, total: 2))
    }

    @Test("Сброс — пусто и после перезапуска, отложенная копия тоже удалена")
    func clear() {
        defaults.set(Data("{broken".utf8), forKey: "azkar.history")
        let history = AzkarHistory(defaults: defaults)
        history.record(SectionProgress(completed: 16, total: 16), of: .morning, on: "2026-10-11")

        history.clear()

        #expect(history.isEmpty)
        #expect(AzkarHistory(defaults: defaults).isEmpty)
        #expect(defaults.object(forKey: "azkar.history.corrupt") == nil)
    }

    @Test("Нечитаемая история — копия под .corrupt, первая новая запись её не уничтожает")
    func corruptIsSetAside() {
        let broken = Data("{broken".utf8)
        defaults.set(broken, forKey: "azkar.history")

        let history = AzkarHistory(defaults: defaults)
        #expect(history.isEmpty)
        history.record(SectionProgress(completed: 1, total: 16), of: .morning, on: "2026-10-11")

        #expect(defaults.data(forKey: "azkar.history.corrupt") == broken)
        #expect(AzkarHistory(defaults: defaults).days[october11] == AzkarDay(morning: AzkarDayRecord(completed: 1, total: 16)))
    }

    @Test("Отложенная копия не перезаписывается при следующих сбоях")
    func corruptKeptOnce() {
        let first = Data("{first".utf8)
        defaults.set(first, forKey: "azkar.history")
        _ = AzkarHistory(defaults: defaults)

        defaults.set(Data("{second".utf8), forKey: "azkar.history")
        _ = AzkarHistory(defaults: defaults)

        #expect(defaults.data(forKey: "azkar.history.corrupt") == first)
    }
}
