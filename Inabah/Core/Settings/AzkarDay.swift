import Foundation

/// День азкаров — григорианская дата начала окна (`yyyy-MM-dd`), как ключ в истории
/// (`AzkarWindowSchedule.dayKey`). Без часового пояса: это номер дня, а не мгновение.
nonisolated struct AzkarCalendarDay: Hashable, Comparable, Sendable {
    let year: Int
    let month: Int
    let day: Int

    /// Строго `yyyy-MM-dd` и существующий день (`2026-02-30` — нет).
    init?(key: String) {
        let parts = key.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2,
              parts.allSatisfy({ $0.allSatisfy(\.isASCII) && $0.allSatisfy(\.isNumber) }),
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2])
        else { return nil }
        self.init(year: year, month: month, day: day)
    }

    /// Дата должна существовать в григорианском календаре.
    init?(year: Int, month: Int, day: Int) {
        let components = DateComponents(year: year, month: month, day: day)
        guard components.isValidDate(in: Self.calendar) else { return nil }
        self.year = year
        self.month = month
        self.day = day
    }

    var key: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    /// Соседний день через границы месяца и года.
    func adding(days: Int) -> AzkarCalendarDay {
        let start = Self.calendar.date(from: DateComponents(year: year, month: month, day: day)) ?? .distantPast
        let date = Self.calendar.date(byAdding: .day, value: days, to: start) ?? start
        let components = Self.calendar.dateComponents([.year, .month, .day], from: date)
        return AzkarCalendarDay(year: components.year ?? year, month: components.month ?? month, day: components.day ?? day)
            ?? self
    }

    var previous: AzkarCalendarDay { adding(days: -1) }

    static func < (lhs: AzkarCalendarDay, rhs: AzkarCalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    /// Григорианский календарь в UTC — только для арифметики дат: день от пояса не зависит.
    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .gmt
        return calendar
    }()
}

/// Отметка дня в календаре: оба раздела полностью — день, один — половина.
nonisolated enum AzkarDayStatus: Sendable {
    case full
    case morningOnly
    case eveningOnly
}

/// Утренние и вечерние одного дня (даты начала окна) — для календаря и серии.
nonisolated struct AzkarDay: Hashable, Sendable {
    var morning: AzkarDayRecord?
    var evening: AzkarDayRecord?

    func record(of section: AzkarSection) -> AzkarDayRecord? {
        switch section {
        case .morning: morning
        case .evening: evening
        }
    }

    /// Ни один раздел не прочитан полностью — `nil`: начатый раздел половиной не считается.
    var status: AzkarDayStatus? {
        switch (morning?.isComplete == true, evening?.isComplete == true) {
        case (true, true): .full
        case (true, false): .morningOnly
        case (false, true): .eveningOnly
        case (false, false): nil
        }
    }
}
