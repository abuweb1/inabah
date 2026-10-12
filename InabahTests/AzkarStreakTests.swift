import Foundation
import Testing
@testable import Inabah

/// Серия дней азкаров: день — оба раздела полностью, сегодняшний неполный день серию не рвёт
/// (решения пользователя 2026-10-11). Те же случаи, что `AzkarStreakTest` на Android.
@Suite("Серия дней азкаров")
struct AzkarStreakTests {
    private let today = AzkarCalendarDay(year: 2026, month: 10, day: 11)!
    private let full = AzkarDay(morning: AzkarDayRecord(completed: 16, total: 16), evening: AzkarDayRecord(completed: 16, total: 16))
    private let morningOnly = AzkarDay(morning: AzkarDayRecord(completed: 16, total: 16), evening: AzkarDayRecord(completed: 3, total: 16))

    private func daysAgo(_ offsets: Int..., day: AzkarDay? = nil) -> [AzkarCalendarDay: AzkarDay] {
        Dictionary(uniqueKeysWithValues: offsets.map { (today.adding(days: -$0), day ?? full) })
    }

    @Test("Отметка дня — по разделам, прочитанным полностью")
    func dayStatus() {
        #expect(full.status == .full)
        #expect(morningOnly.status == .morningOnly)
        #expect(AzkarDay(evening: AzkarDayRecord(completed: 16, total: 16)).status == .eveningOnly)
        #expect(AzkarDay(morning: AzkarDayRecord(completed: 15, total: 16), evening: AzkarDayRecord(completed: 1, total: 16)).status == nil)
        // Пустой раздел (контент не загрузился) полным не считается.
        #expect(AzkarDay(morning: AzkarDayRecord(completed: 0, total: 0), evening: AzkarDayRecord(completed: 0, total: 0)).status == nil)
    }

    @Test("Пустая история — нули")
    func empty() {
        #expect(AzkarStreak(days: [:], today: today) == .empty)
    }

    @Test("Сегодня полный — считается в серии")
    func todayFull() {
        #expect(AzkarStreak(days: daysAgo(0, 1, 2), today: today) == AzkarStreak(current: 3, best: 3, fullDays: 3))
    }

    @Test("Сегодня ещё не дочитан — серия до вчера не рвётся")
    func todayUnfinished() {
        var days = daysAgo(1, 2)
        days[today] = morningOnly
        #expect(AzkarStreak(days: days, today: today) == AzkarStreak(current: 2, best: 2, fullDays: 2))
    }

    @Test("Пропуск вчера — текущая серия с нуля, лучшая и итог остаются")
    func missedYesterday() {
        var days = daysAgo(2, 3, 4, 6)
        days[today.previous] = morningOnly
        #expect(AzkarStreak(days: days, today: today) == AzkarStreak(current: 0, best: 3, fullDays: 4))
    }

    @Test("Полдня не продолжает серию")
    func halfDayBreaks() {
        var days = daysAgo(0, 2)
        days[today.previous] = morningOnly
        #expect(AzkarStreak(days: days, today: today) == AzkarStreak(current: 1, best: 1, fullDays: 2))
    }

    @Test("Дни после сегодня — в лучшей серии и итоге, но не в текущей")
    func futureDays() {
        let days = [today.adding(days: 1): full, today: full]
        #expect(AzkarStreak(days: days, today: today) == AzkarStreak(current: 1, best: 2, fullDays: 2))
    }

    @Test("Серия через границу месяца и года")
    func acrossYear() throws {
        let newYear = try #require(AzkarCalendarDay(key: "2027-01-01"))
        let days = Dictionary(uniqueKeysWithValues: (0..<3).map { (newYear.adding(days: -$0), full) })
        #expect(AzkarStreak(days: days, today: newYear) == AzkarStreak(current: 3, best: 3, fullDays: 3))
    }

    @Test("День заканчивается вместе с вечерним окном")
    func dayEndsWithEveningWindow() {
        var berlin = Calendar(identifier: .gregorian)
        berlin.timeZone = TimeZone(identifier: "Europe/Berlin")!
        let evening = AzkarWindowSchedule(
            window: AzkarWindow(start: DayTime(hour: 17, minute: 0), end: DayTime(hour: 2, minute: 0)),
            calendar: berlin
        )
        func at(_ day: Int, _ hour: Int) -> Date {
            berlin.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour))!
        }

        #expect(AzkarStreak.today(evening: evening, at: at(11, 8)) == today)   // утро
        #expect(AzkarStreak.today(evening: evening, at: at(11, 18)) == today)  // вечернее окно
        #expect(AzkarStreak.today(evening: evening, at: at(12, 1)) == today)   // вечерние ещё идут
        #expect(AzkarStreak.today(evening: evening, at: at(12, 3)) == today.adding(days: 1))  // окно закрылось
    }
}

@Suite("День азкаров — дата")
struct AzkarCalendarDayTests {
    @Test("Разбирается только существующий день в виде yyyy-MM-dd", arguments: [
        "2026-02-30", "2026-13-01", "2026-1-01", "2026-01-1", "26-01-01", "2026/01/01", "", "abcd-ef-gh", "２０２６-01-01",
    ])
    func rejectsInvalidKeys(key: String) {
        #expect(AzkarCalendarDay(key: key) == nil)
    }

    @Test("Ключ — обратно в тот же текст")
    func roundTrip() throws {
        #expect(try #require(AzkarCalendarDay(key: "2028-02-29")).key == "2028-02-29")
    }

    @Test("Соседние дни — через границу месяца, года и 29 февраля", arguments: [
        ("2026-10-31", 1, "2026-11-01"),
        ("2026-12-31", 1, "2027-01-01"),
        ("2027-01-01", -1, "2026-12-31"),
        ("2028-02-28", 1, "2028-02-29"),
        ("2027-03-01", -1, "2027-02-28"),
    ])
    func adding(start: String, days: Int, expected: String) throws {
        #expect(try #require(AzkarCalendarDay(key: start)).adding(days: days).key == expected)
    }

    @Test("Сравнение — по году, месяцу и числу")
    func ordering() throws {
        let days = try ["2026-10-11", "2025-12-31", "2026-09-30", "2026-10-02"].map { try #require(AzkarCalendarDay(key: $0)) }
        #expect(days.sorted().map(\.key) == ["2025-12-31", "2026-09-30", "2026-10-02", "2026-10-11"])
    }
}
