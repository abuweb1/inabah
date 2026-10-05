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

    /// Наступление времени обнуления, ближайшее к `date` (при равенстве расстояний — позднее).
    func boundary(nearest date: Date) -> Date {
        let previous = periodStart(at: date)
        let next = nextReset(after: date)
        return date.timeIntervalSince(previous) < next.timeIntervalSince(date) ? previous : next
    }
}

/// Период чтения раздела: прогресс действителен до `validUntil`, затем обнуляется.
///
/// Хранится момент окончания, а не начала периода: проверка «наступило ли обнуление» не зависит
/// от часового пояса и перевода часов (сравнение «начал периода» после перелёта давало другое
/// мгновение и стирало прогресс), а смена времени в настройках не может пропустить обнуление.
nonisolated struct AzkarPeriod: Codable, Hashable, Sendable {
    /// Граница, назначенная при начале периода, — от неё пересчитывается смена времени в настройках
    /// (прокрутка колеса времени не зависит от промежуточных значений).
    private(set) var scheduledReset: Date
    /// Фактическая граница периода.
    private(set) var validUntil: Date
    /// Часовой пояс, в котором назначена граница.
    private(set) var timeZone: String

    /// Новый период в момент `now` — до ближайшего обнуления.
    init(startingAt now: Date, schedule: AzkarResetSchedule) {
        let reset = schedule.nextReset(after: now)
        scheduledReset = reset
        validUntil = reset
        timeZone = schedule.calendar.timeZone.identifier
    }

    /// Период из сохранения старого формата, где хранилось его начало.
    init(legacyPeriodStart start: Date, schedule: AzkarResetSchedule) {
        self.init(startingAt: start, schedule: schedule)
    }

    func isExpired(at now: Date) -> Bool {
        now >= validUntil
    }

    /// Время обнуления изменили в настройках. Граница переносится на ближайшее к назначенной
    /// наступление нового времени, если оно ещё впереди (17:00 → 18:00 — одно обнуление в 18:00).
    /// Если новое время в этом периоде уже прошло — обнуление в прежнее время: прочитанное
    /// не стирается сразу и ближайшее обнуление не пропускается.
    mutating func reschedule(to schedule: AzkarResetSchedule, at now: Date) {
        let candidate = schedule.boundary(nearest: scheduledReset)
        if candidate > now {
            validUntil = candidate
        } else if scheduledReset > now {
            validUntil = scheduledReset
        }
    }

    /// Сменился часовой пояс: граница переносится на ближайшее наступление времени обнуления
    /// в новом поясе, если оно ещё впереди (Москва → Берлин: обнуление в 17:00 по Берлину, одно).
    mutating func relocate(to schedule: AzkarResetSchedule, at now: Date) {
        let zone = schedule.calendar.timeZone.identifier
        guard zone != timeZone else { return }
        timeZone = zone
        let candidate = schedule.boundary(nearest: validUntil)
        if candidate > now { validUntil = candidate }
        scheduledReset = validUntil
    }
}
