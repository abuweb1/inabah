import Foundation

/// Время суток (часы и минуты) без даты — например, начало времени азкаров.
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

/// Время азкаров раздела: «с `start` до `end`». Конец раньше начала — на следующий день
/// (вечерние 17:00–02:00).
nonisolated struct AzkarWindow: Hashable, Sendable {
    let start: DayTime
    let end: DayTime

    /// Начало, совпадающее с концом, — окно без длины; такое не сохраняется.
    var isValid: Bool { start != end }
}

/// Отрезок времени, к которому относится прогресс раздела: само время азкаров или промежуток
/// до него. На каждой границе счётчики обнуляются.
///
/// Отрезок узнаётся по виду и дате (`day`), а не по мгновениям: после перелёта в другой часовой
/// пояс то же окно — то же самое, и прочитанное не теряется.
nonisolated struct AzkarPeriod: Codable, Hashable, Sendable {
    enum Kind: String, Codable, Sendable {
        /// Время азкаров: прогресс идёт в отметку на главной и в историю.
        case window
        /// Промежуток до следующего окна: счёт работает, но никуда не идёт.
        case gap
    }

    let kind: Kind
    /// Дата начала окна (`yyyy-MM-dd`): своего — у окна, следующего — у промежутка.
    /// Вечерние 17:00–02:00, прочитанные в 00:30, относятся к дате 17:00.
    let day: String
    /// Граница отрезка: конец окна или начало следующего.
    let validUntil: Date

    var isWindow: Bool { kind == .window }

    func isExpired(at now: Date) -> Bool {
        now >= validUntil
    }

    /// Тот же отрезок — счёт сохраняется, даже если граница сдвинулась (новое время в настройках,
    /// другой часовой пояс).
    func isSameSpan(as other: AzkarPeriod) -> Bool {
        kind == other.kind && day == other.day
    }
}

/// Расписание времени азкаров раздела: в каком отрезке момент и когда граница.
///
/// Считается через `Calendar`, а не сложением секунд: переход на летнее/зимнее время и смена
/// часового пояса учитываются (несуществующее время — например, 02:00 в ночь перевода часов —
/// сдвигается на ближайшее существующее).
nonisolated struct AzkarWindowSchedule: Sendable {
    let window: AzkarWindow
    let calendar: Calendar

    /// Отрезок, в который попадает `now`. Ровно в момент начала окна — уже окно,
    /// ровно в момент конца — уже промежуток.
    func period(at now: Date) -> AzkarPeriod {
        let start = lastOccurrence(of: window.start, notAfter: now)
        let end = nextOccurrence(of: window.end, after: start)
        if now < end {
            return AzkarPeriod(kind: .window, day: dayKey(of: start), validUntil: end)
        }
        let nextStart = nextOccurrence(of: window.start, after: now)
        return AzkarPeriod(kind: .gap, day: dayKey(of: nextStart), validUntil: nextStart)
    }

    /// Григорианская дата (`yyyy-MM-dd`) в часовом поясе расписания — ключ окна в истории
    /// и признак «того же отрезка». Не в календаре пользователя: с хиджрой или японским
    /// календарём в настройках iPhone ключ был бы «1448-04-25», а смена календаря посреди
    /// окна обнуляла бы счёт (аудит 2026-10-06, §5.1).
    func dayKey(of date: Date) -> String {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        let components = gregorian.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year ?? 0, components.month ?? 0, components.day ?? 0)
    }

    private func components(of time: DayTime) -> DateComponents {
        DateComponents(hour: time.hour, minute: time.minute, second: 0)
    }

    private func lastOccurrence(of time: DayTime, notAfter date: Date) -> Date {
        calendar.nextDate(
            after: date.addingTimeInterval(1),
            matching: components(of: time),
            matchingPolicy: .nextTime,
            direction: .backward
        ) ?? date
    }

    private func nextOccurrence(of time: DayTime, after date: Date) -> Date {
        calendar.nextDate(
            after: date,
            matching: components(of: time),
            matchingPolicy: .nextTime,
            direction: .forward
        ) ?? date.addingTimeInterval(24 * 60 * 60)
    }
}
