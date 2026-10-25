import Foundation
import Testing
@testable import Inabah

/// «Добавьте ещё азкаров»: с 14-го полного дня серии, повтор через 7, после обрыва — снова с 14 (2026-10-25).
/// Те же случаи, что `AzkarMotivationTest` на Android.
@MainActor
@Suite("Добавьте ещё азкаров")
struct AzkarMotivationTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("AzkarMotivationTests")
    }

    private let start = AzkarCalendarDay(year: 2026, month: 10, day: 1)!
    private let full = AzkarDay(morning: AzkarDayRecord(completed: 16, total: 16), evening: AzkarDayRecord(completed: 16, total: 16))
    private let morningOnly = AzkarDay(morning: AzkarDayRecord(completed: 16, total: 16), evening: AzkarDayRecord(completed: 3, total: 16))

    /// Полные дни подряд с `from`, `count` штук.
    private func series(from: AzkarCalendarDay, count: Int) -> [AzkarCalendarDay: AzkarDay] {
        Dictionary(uniqueKeysWithValues: (0..<count).map { (from.adding(days: $0), full) })
    }

    private func series(_ count: Int) -> [AzkarCalendarDay: AzkarDay] {
        series(from: start, count: count)
    }

    /// День `n` серии от `from` (1 — сам `from`).
    private func day(_ n: Int, from: AzkarCalendarDay? = nil) -> AzkarCalendarDay {
        (from ?? start).adding(days: n - 1)
    }

    private func due(_ days: [AzkarCalendarDay: AzkarDay], _ today: AzkarCalendarDay, last: MotivationOffer?, canAddMore: Bool = true) -> Int? {
        AzkarMotivation.due(days: days, today: today, last: last, canAddMore: canAddMore)?.streak
    }

    @Test("Предлагается с 14-го полного дня, не раньше")
    func fromDayFourteen() {
        #expect(due(series(13), day(13), last: nil) == nil)
        #expect(due(series(14), day(14), last: nil) == 14)
    }

    @Test("Выбраны все азкары — не предлагается")
    func allSelected() {
        #expect(due(series(30), day(30), last: nil, canAddMore: false) == nil)
    }

    @Test("Сегодня не дочитан полностью — не предлагается, даже если серия длинная")
    func todayUnfinished() {
        var days = series(20)
        days[day(21)] = morningOnly
        #expect(due(days, day(21), last: nil) == nil)
    }

    @Test("После закрытия — снова через 7 дней серии")
    func repeatsAfterSeven() {
        let last = MotivationOffer(seriesStart: start, streak: 14)
        for n in 14...20 {
            #expect(due(series(n), day(n), last: last) == nil, "день \(n)")
        }
        #expect(due(series(21), day(21), last: last) == 21)
        #expect(due(series(22), day(22), last: MotivationOffer(seriesStart: start, streak: 21)) == nil)
        #expect(due(series(28), day(28), last: MotivationOffer(seriesStart: start, streak: 21)) == 28)
    }

    @Test("Серия прервалась — новая серия снова с 14 дней")
    func brokenSeriesRestarts() {
        let last = MotivationOffer(seriesStart: start, streak: 14)
        // 16 дней, пропуск, новая серия.
        let restart = day(18)
        var days = series(16).merging(series(from: restart, count: 14)) { first, _ in first }
        #expect(due(days, day(14, from: restart), last: last) == 14)
        days[day(14, from: restart)] = nil
        #expect(due(days, day(13, from: restart), last: last) == nil)
    }

    @Test("Пропущенный 14-й день — на ближайшем полном, следующий через 7 от него")
    func missedFourteenth() {
        #expect(due(series(16), day(16), last: nil) == 16)
        #expect(due(series(22), day(22), last: MotivationOffer(seriesStart: start, streak: 16)) == nil)
        #expect(due(series(23), day(23), last: MotivationOffer(seriesStart: start, streak: 16)) == 23)
    }

    @Test("Закрытие запоминается и переживает перезапуск, испорченное — как не предлагали")
    func persistence() {
        let motivation = AzkarMotivation(defaults: defaults)
        #expect(motivation.last == nil)
        let offer = MotivationOffer(seriesStart: start, streak: 14)
        #expect(offer.day == day(14))

        motivation.offered(offer)

        let restarted = AzkarMotivation(defaults: defaults)
        #expect(restarted.last == offer)
        #expect(restarted.due(days: series(20), today: day(20), canAddMore: true) == nil)

        defaults.set("не дата", forKey: "azkar.motivation.seriesStart")
        #expect(AzkarMotivation(defaults: defaults).last == nil)
    }
}
