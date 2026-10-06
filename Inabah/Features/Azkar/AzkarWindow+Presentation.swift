import SwiftUI

extension DayTime {
    /// Время суток как дата дня `day` — для `DatePicker` и показа часов и минут.
    func date(in calendar: Calendar, on day: Date = .now) -> Date {
        calendar.date(bySettingHour: hour, minute: minute, second: 0, of: day) ?? day
    }

    init(_ date: Date, in calendar: Calendar) {
        let components = calendar.dateComponents([.hour, .minute], from: date)
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
