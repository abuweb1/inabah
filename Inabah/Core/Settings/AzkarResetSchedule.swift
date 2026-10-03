import Foundation

/// Время суток (часы и минуты) без даты — например, время обнуления прогресса.
nonisolated struct DayTime: Codable, Hashable, Sendable {
    let hour: Int
    let minute: Int

    init(hour: Int, minute: Int) {
        self.hour = hour.clamped(to: 0...23)
        self.minute = minute.clamped(to: 0...59)
    }

    init(minutesSinceMidnight minutes: Int) {
        let clamped = minutes.clamped(to: 0...(24 * 60 - 1))
        self.init(hour: clamped / 60, minute: clamped % 60)
    }

    var minutesSinceMidnight: Int { hour * 60 + minute }
}

/// Ежедневная граница периода чтения: прогресс раздела относится к периоду, который начался
/// в последнее наступление `time`, и обнуляется в следующее.
///
/// Считается через `Calendar`, а не сложением секунд: переход на летнее/зимнее время и смена
/// часового пояса учитываются (несуществующее время — например, 02:00 в ночь перевода часов —
/// сдвигается на ближайшее существующее).
nonisolated struct AzkarResetSchedule: Sendable {
    let time: DayTime
    let calendar: Calendar

    private var components: DateComponents {
        DateComponents(hour: time.hour, minute: time.minute, second: 0)
    }

    /// Начало периода, в который попадает `date`: последнее наступление времени обнуления
    /// не позже `date` (ровно в момент обнуления начинается новый период).
    func periodStart(at date: Date) -> Date {
        calendar.nextDate(
            after: date.addingTimeInterval(1),
            matching: components,
            matchingPolicy: .nextTime,
            direction: .backward
        ) ?? date
    }

    /// Ближайшее обнуление после `date`.
    func nextReset(after date: Date) -> Date {
        calendar.nextDate(
            after: date,
            matching: components,
            matchingPolicy: .nextTime,
            direction: .forward
        ) ?? date.addingTimeInterval(24 * 60 * 60)
    }
}
