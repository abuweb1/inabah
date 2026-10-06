import SwiftUI

extension DayTime {
    /// Время суток как дата опорного дня — для `DatePicker` и показа часов и минут.
    ///
    /// Опорный день — 1 января 2001 (григорианский, пояс календаря): в этот день переводов
    /// часов нет ни в одном поясе. Со «сегодня» в день весеннего перевода 02:00 не существует —
    /// показ и колесо сдвигались на 03:00 (аудит 2026-10-06, §5.5).
    nonisolated func date(in calendar: Calendar) -> Date {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        let components = DateComponents(year: 2001, month: 1, day: 1, hour: hour, minute: minute)
        return gregorian.date(from: components) ?? .distantPast
    }

    nonisolated init(_ date: Date, in calendar: Calendar) {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        let components = gregorian.dateComponents([.hour, .minute], from: date)
        self.init(hour: components.hour ?? 0, minute: components.minute ?? 0)
    }

    /// «5:00» — часы без ведущего нуля, по формату локали.
    func text(in calendar: Calendar) -> Text {
        Text(date(in: calendar), format: .dateTime.hour(.defaultDigits(amPM: .abbreviated)).minute())
    }
}

extension AzkarWindow {
    /// «5:00–12:00» — на карточке раздела вне времени азкаров.
    func rangeText(in calendar: Calendar) -> Text {
        Text("azkar.window.range \(start.text(in: calendar)) \(end.text(in: calendar))")
    }
}

extension AzkarSection {
    /// «Время утренних азкаров — с 5:00 до 12:00» — на экране раздела вне времени азкаров.
    func windowNotice(_ window: AzkarWindow, in calendar: Calendar) -> Text {
        let start = window.start.text(in: calendar)
        let end = window.end.text(in: calendar)
        return switch self {
        case .morning: Text("azkar.window.notice.morning \(start) \(end)")
        case .evening: Text("azkar.window.notice.evening \(start) \(end)")
        }
    }
}
