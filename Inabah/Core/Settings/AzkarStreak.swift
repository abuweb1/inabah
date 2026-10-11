import Foundation

/// Серия дней азкаров (решения пользователя 2026-10-11, `docs/analysis/2026-10-11-azkar-streak-calendar.md`):
/// день — утренние и вечерние полностью, без «заморозки». Тот же расчёт на Android — `AzkarStreak.kt`.
nonisolated struct AzkarStreak: Hashable, Sendable {
    /// Дней подряд до сегодня включительно; сегодняшний неполный день серию не рвёт.
    let current: Int
    /// Самая длинная серия за всю историю.
    let best: Int
    /// Всего полных дней.
    let fullDays: Int

    static let empty = AzkarStreak(current: 0, best: 0, fullDays: 0)

    /// Самое большое число серии на экране и в уведомлении; дальше — «9999+» (решение пользователя 2026-10-11).
    static let displayMax = 9999

    /// Серия по истории `days` на день `today`. Дни позже `today` (перелёт на запад) учитываются
    /// в лучшей серии и в итоге, но не в текущей.
    init(days: [AzkarCalendarDay: AzkarDay], today: AzkarCalendarDay) {
        let full = Set(days.filter { $0.value.status == .full }.keys)
        var current = 0
        var day = full.contains(today) ? today : today.previous
        while full.contains(day) {
            current += 1
            day = day.previous
        }
        var best = 0
        var run = 0
        var previous: AzkarCalendarDay?
        for date in full.sorted() {
            run = previous?.adding(days: 1) == date ? run + 1 : 1
            best = max(best, run)
            previous = date
        }
        self.init(current: current, best: best, fullDays: full.count)
    }

    init(current: Int, best: Int, fullDays: Int) {
        self.current = current
        self.best = best
        self.fullDays = fullDays
    }

    /// Какой день азкаров «сегодня»: дата начала окна вечерних, если оно идёт, иначе следующего
    /// (`AzkarPeriod.day`). День заканчивается вместе с его вечерним окном: в 01:00 при вечерних
    /// 17:00–02:00 ещё вчерашний день, в 03:00 — уже новый, и вчерашний без вечерних становится пропуском.
    static func today(evening: AzkarWindowSchedule, at date: Date) -> AzkarCalendarDay? {
        AzkarCalendarDay(key: evening.period(at: date).day)
    }
}
