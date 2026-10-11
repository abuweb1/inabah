import Foundation

/// Месяц календаря азкаров (григорианский, без пояса — как `AzkarCalendarDay`).
nonisolated struct AzkarCalendarMonth: Hashable, Comparable, Sendable {
    let year: Int
    let month: Int

    init(year: Int, month: Int) {
        self.year = year
        self.month = month
    }

    init(day: AzkarCalendarDay) {
        self.init(year: day.year, month: day.month)
    }

    /// Соседний месяц через границу года.
    func adding(months: Int) -> AzkarCalendarMonth {
        let index = year * 12 + (month - 1) + months
        return AzkarCalendarMonth(year: index.floorDiv(12), month: index.floorMod(12) + 1)
    }

    /// Все дни месяца по порядку.
    var days: [AzkarCalendarDay] {
        (1...31).compactMap { AzkarCalendarDay(year: year, month: month, day: $0) }
    }

    /// Ячейки сетки месяца с недели, начинающейся с `firstWeekday` (1 — воскресенье, 2 — понедельник,
    /// как `Calendar.firstWeekday`): `nil` — пустые места до 1-го числа и после последнего, до конца
    /// недели. Всегда кратно 7 (Android `monthCells`).
    func cells(firstWeekday: Int) -> [AzkarCalendarDay?] {
        let days = days
        guard let first = days.first else { return [] }
        let leading = (first.weekday - firstWeekday + 7) % 7
        let trailing = (7 - (leading + days.count) % 7) % 7
        return Array(repeating: nil, count: leading) + days.map(Optional.some) + Array(repeating: nil, count: trailing)
    }

    static func < (lhs: AzkarCalendarMonth, rhs: AzkarCalendarMonth) -> Bool {
        (lhs.year, lhs.month) < (rhs.year, rhs.month)
    }

    /// Месяцы календаря: от месяца первой записи истории (если она раньше) до месяца `today`
    /// (Android `calendarMonths`). Записи позже `today` месяцев не добавляют — в будущее не листается.
    static func range(days: some Collection<AzkarCalendarDay>, today: AzkarCalendarDay) -> [AzkarCalendarMonth] {
        let current = AzkarCalendarMonth(day: today)
        let first = days.min().map(AzkarCalendarMonth.init(day:)).flatMap { $0 < current ? $0 : nil } ?? current
        var months = [first]
        while let last = months.last, last < current {
            months.append(last.adding(months: 1))
        }
        return months
    }
}

nonisolated extension AzkarCalendarDay {
    /// День недели: 1 — воскресенье … 7 — суббота (как `Calendar.component(.weekday)`).
    var weekday: Int {
        // Формула Зеллера в варианте Сакамото: 0 — воскресенье.
        let offsets = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4]
        let y = month < 3 ? year - 1 : year
        let index = (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + day) % 7
        return index + 1
    }
}

private extension Int {
    nonisolated func floorDiv(_ divisor: Int) -> Int {
        Int((Double(self) / Double(divisor)).rounded(.down))
    }

    nonisolated func floorMod(_ divisor: Int) -> Int {
        ((self % divisor) + divisor) % divisor
    }
}
