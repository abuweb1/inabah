import Foundation
import Testing
@testable import Inabah

/// Месяцы и сетка «Календаря азкаров» — как `monthCells` / `calendarMonths` на Android.
@Suite("Календарь азкаров")
struct AzkarCalendarTests {
    private func day(_ key: String) throws -> AzkarCalendarDay {
        try #require(AzkarCalendarDay(key: key))
    }

    @Test("День недели — как у григорианского календаря", arguments: [
        ("2026-10-01", 5), ("2026-10-11", 1), ("2027-02-01", 2), ("2000-02-29", 3), ("2028-03-04", 7),
    ])
    func weekday(key: String, expected: Int) throws {
        #expect(try day(key).weekday == expected)
    }

    @Test("Октябрь 2026 с понедельника: 3 пустых в начале, 35 ячеек")
    func octoberFromMonday() {
        let cells = AzkarCalendarMonth(year: 2026, month: 10).cells(firstWeekday: 2)

        #expect(cells.count == 35)
        #expect(cells.prefix(3).allSatisfy { $0 == nil })
        #expect(cells[3]?.key == "2026-10-01")
        #expect(cells.compactMap { $0 }.count == 31)
        #expect(cells[cells.count - 1] == nil)  // 31-е — суббота, воскресенье пустое
    }

    @Test("Октябрь 2026 с воскресенья: 4 пустых в начале")
    func octoberFromSunday() {
        let cells = AzkarCalendarMonth(year: 2026, month: 10).cells(firstWeekday: 1)

        #expect(cells.count % 7 == 0)
        #expect(cells.prefix(4).allSatisfy { $0 == nil })
        #expect(cells[4]?.key == "2026-10-01")
    }

    @Test("Февраль 2027 с понедельника — ровно 4 недели без пустых")
    func februaryExact() {
        let cells = AzkarCalendarMonth(year: 2027, month: 2).cells(firstWeekday: 2)

        #expect(cells.count == 28)
        #expect(cells.allSatisfy { $0 != nil })
    }

    @Test("Соседние месяцы — через границу года")
    func addingMonths() {
        let december = AzkarCalendarMonth(year: 2026, month: 12)

        #expect(december.adding(months: 1) == AzkarCalendarMonth(year: 2027, month: 1))
        #expect(AzkarCalendarMonth(year: 2027, month: 1).adding(months: -1) == december)
        #expect(december.adding(months: -12) == AzkarCalendarMonth(year: 2025, month: 12))
    }

    @Test("Месяцы: пустая история — только текущий")
    func monthsEmpty() throws {
        #expect(AzkarCalendarMonth.range(days: [], today: try day("2026-10-11")) == [AzkarCalendarMonth(year: 2026, month: 10)])
    }

    @Test("Месяцы: от месяца первой записи до текущего, через год")
    func monthsFromHistory() throws {
        let months = AzkarCalendarMonth.range(days: [try day("2025-11-20"), try day("2026-10-01")], today: try day("2026-10-11"))

        #expect(months.first == AzkarCalendarMonth(year: 2025, month: 11))
        #expect(months.last == AzkarCalendarMonth(year: 2026, month: 10))
        #expect(months.count == 12)
    }

    @Test("Месяцы: записи позже сегодня месяцев не добавляют")
    func monthsIgnoreFuture() throws {
        let months = AzkarCalendarMonth.range(days: [try day("2026-12-01")], today: try day("2026-10-11"))

        #expect(months == [AzkarCalendarMonth(year: 2026, month: 10)])
    }
}

/// Своё стекло кнопок — цвета от цвета раздела в OKLCH, как `glassColors` на Android.
@Suite("Стекло кнопок")
struct GlassColorsTests {
    @Test("OKLCH туда и обратно не меняет цвет", arguments: [
        SRGBColor(red: 1, green: 1, blue: 1),
        SRGBColor(red: 0x9B / 255, green: 0x6F / 255, blue: 0xCC / 255),
        SRGBColor(red: 0x5E / 255, green: 0xD6 / 255, blue: 0xA6 / 255),
    ])
    func roundTrip(color: SRGBColor) {
        let back = SRGBColor(oklch: color.oklch)

        #expect(abs(back.red - color.red) < 1e-6)
        #expect(abs(back.green - color.green) < 1e-6)
        #expect(abs(back.blue - color.blue) < 1e-6)
    }

    @Test("Набор сдвигов — по ближайшему оттенку раздела", arguments: [
        (SRGBColor(red: 0xB5 / 255, green: 0x9B / 255, blue: 0xEA / 255), GlassColors.Shifts.violet),  // tabAzkar
        (SRGBColor(red: 0x5E / 255, green: 0xD6 / 255, blue: 0xA6 / 255), GlassColors.Shifts.green),   // tabHadith
        (SRGBColor(red: 0xE8 / 255, green: 0xA8 / 255, blue: 0x5A / 255), GlassColors.Shifts.amber),   // tabMakharij
        (SRGBColor(red: 0x8F / 255, green: 0xB4 / 255, blue: 0xF0 / 255), GlassColors.Shifts.graphite), // tabSettings
    ])
    func nearestShifts(tint: SRGBColor, expected: GlassColors.Shifts) {
        #expect(GlassColors.Shifts.nearest(toHue: tint.oklch.h) == expected)
    }

    @Test("В покое — цвет раздела 20 % и 40 %; нажатие непрозрачное и светлее у значка")
    func restAndPressed() {
        let tint = SRGBColor(red: 0xB5 / 255, green: 0x9B / 255, blue: 0xEA / 255)
        let colors = GlassColors(tint: tint)

        #expect(colors.fill == tint.opacity(0.2))
        #expect(colors.rim == tint.opacity(0.4))
        #expect(colors.pressedFill.alpha == 1)
        #expect(colors.pressedIcon.oklch.l > tint.oklch.l)
        #expect(abs(colors.pressedIcon.oklch.l - min(tint.oklch.l + 0.258, 1)) < 0.02)
    }
}
