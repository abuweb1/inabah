import Foundation
import Testing
@testable import Inabah

/// «Если серия под угрозой» (решения пользователя 2026-10-11): правило — как Android `streakAtRisk`
/// (`ReminderDueTest`), планирование заранее — особенность iOS.
@Suite("Напоминание «серия под угрозой»")
struct ReminderStreakTests {
    private static let berlin: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
        return calendar
    }()

    private static let windows: [AzkarSection: AzkarWindow] = [
        .morning: AzkarWindow(start: DayTime(hour: 5, minute: 0), end: DayTime(hour: 12, minute: 0)),
        .evening: AzkarWindow(start: DayTime(hour: 17, minute: 0), end: DayTime(hour: 2, minute: 0)),
    ]

    private static let complete = AzkarDayRecord(completed: 16, total: 16)
    private static let full = AzkarDay(morning: complete, evening: complete)
    private static let october10 = AzkarCalendarDay(year: 2026, month: 10, day: 10)!

    private static func date(_ d: Int, _ h: Int) -> Date {
        berlin.date(from: DateComponents(year: 2026, month: 10, day: d, hour: h))!
    }

    /// Два полных дня перед `day`.
    private static func twoFullDaysBefore(_ day: AzkarCalendarDay) -> [AzkarCalendarDay: AzkarDay] {
        [day.adding(days: -1): full, day.adding(days: -2): full]
    }

    private static func atRisk(_ section: AzkarSection, at moment: Date, _ days: [AzkarCalendarDay: AzkarDay]) -> Int? {
        ReminderPlan.streakAtRisk(section: section, windows: windows, days: days, at: moment, calendar: berlin)
    }

    private static func plan(
        _ preferences: ReminderPreferences,
        now: Date,
        history: [AzkarCalendarDay: AzkarDay],
        days: Int = 3
    ) -> [PlannedReminder] {
        ReminderPlan.plan(preferences: preferences, windows: windows, now: now, calendar: berlin, days: days, history: history) { section, day in
            AzkarCalendarDay(key: day).flatMap { history[$0]?.record(of: section)?.isComplete } == true
        }
    }

    // MARK: Правило

    @Test("Утром — утренние не дочитаны")
    func morning() {
        let morning = Self.date(10, 10)
        var started = Self.twoFullDaysBefore(Self.october10)
        started[Self.october10] = AzkarDay(morning: AzkarDayRecord(completed: 5, total: 16))
        var done = Self.twoFullDaysBefore(Self.october10)
        done[Self.october10] = AzkarDay(morning: Self.complete)

        #expect(Self.atRisk(.morning, at: morning, Self.twoFullDaysBefore(Self.october10)) == 2)
        #expect(Self.atRisk(.morning, at: morning, started) == 2)
        #expect(Self.atRisk(.morning, at: morning, done) == nil, "дочитаны")
        #expect(Self.atRisk(.morning, at: morning, [:]) == nil, "серии нет")
        #expect(Self.atRisk(.morning, at: Self.date(10, 13), Self.twoFullDaysBefore(Self.october10)) == nil, "окно закрыто")
    }

    @Test("Вечером — только если утренние этого дня прочитаны")
    func evening() {
        var morningDone = Self.twoFullDaysBefore(Self.october10)
        morningDone[Self.october10] = AzkarDay(morning: Self.complete)
        var dayFull = morningDone
        dayFull[Self.october10] = Self.full
        // Вечерние 17:00–02:00: в полночь — ещё день 10 октября.
        let midnight = Self.date(11, 0)

        #expect(Self.atRisk(.evening, at: midnight, morningDone) == 2)
        #expect(Self.atRisk(.evening, at: midnight, Self.twoFullDaysBefore(Self.october10)) == nil, "утренние пропущены — серия уже прервана")
        #expect(Self.atRisk(.evening, at: midnight, dayFull) == nil, "день полный")
    }

    // MARK: План

    @Test("План: серия до вчера — утром в 10:00 и вечером в полночь; дальше серия прервётся — больше нет")
    func plannedMoments() {
        let planned = Self.plan(ReminderPreferences(azkarStreak: true), now: Self.date(10, 8), history: Self.twoFullDaysBefore(Self.october10))

        // Вечером при непрочитанных утренних серия уже прервана — вечернего нет; завтра серии нет.
        #expect(planned.map(\.kind) == [.morningStreak])
        #expect(planned.first?.at == Self.date(10, 10))
        #expect(planned.first?.streak == 2)
    }

    @Test("План: утренние сегодня прочитаны — вечером в полночь; день полный — завтра утром серия больше")
    func plannedAfterReading() {
        var morningDone = Self.twoFullDaysBefore(Self.october10)
        morningDone[Self.october10] = AzkarDay(morning: Self.complete)
        let evening = Self.plan(ReminderPreferences(azkarStreak: true), now: Self.date(10, 13), history: morningDone)

        #expect(evening.map(\.kind) == [.eveningStreak])
        #expect(evening.first?.at == Self.date(11, 0))
        #expect(evening.first?.streak == 2)

        var dayFull = morningDone
        dayFull[Self.october10] = Self.full
        let tomorrow = Self.plan(ReminderPreferences(azkarStreak: true), now: Self.date(10, 23), history: dayFull)

        #expect(tomorrow.map(\.kind) == [.morningStreak])
        #expect(tomorrow.first?.at == Self.date(11, 10))
        #expect(tomorrow.first?.streak == 3)
    }

    @Test("Без дублей: в момент серии «ещё не прочитаны» того же раздела не ставится; выключено — серии нет")
    func noDuplicates() {
        let history = Self.twoFullDaysBefore(Self.october10)
        let both = ReminderPreferences(azkarStart: [.morning], azkarUnfinished: true, azkarStreak: true)
        let planned = Self.plan(both, now: Self.date(10, 8), history: history, days: 1)
            .filter { $0.kind != .morningStart }

        #expect(planned.map(\.kind) == [.morningStreak])

        let withoutStreak = Self.plan(ReminderPreferences(azkarStart: [.morning], azkarUnfinished: true), now: Self.date(10, 8), history: history, days: 1)
        #expect(withoutStreak.contains { $0.kind == .morningUnfinished })
        #expect(!withoutStreak.contains { $0.kind == .morningStreak })
    }

    // MARK: Тексты и настройки

    @Test("Подстановка серии; «9999+» сверх предела")
    func streakText() {
        let template = ReminderText(title: "Серия {streak} под угрозой", body: "{text}")
        let filled = template.filled([.streak: "12 дней подряд", .text: "{streak}"])

        #expect(filled.title == "Серия 12 дней подряд под угрозой")
        #expect(filled.body == "{streak}", "внутри текста подстановки не заменяются")
        #expect(AzkarStreak.daysText(10_000).key == "azkar.streak.daysMax")
    }

    @Test("Нажатие на «серия под угрозой» открывает раздел")
    func tapOpensSection() {
        #expect(ReminderPayload.target(from: ReminderPayload.userInfo(kind: .eveningStreak)) == .azkar(.evening))
    }

    @Test("Настройка хранится под ключом Android и сохраняется только при изменении")
    @MainActor
    func setting() throws {
        let storage = try IsolatedDefaults("ReminderStreakTests")
        let settings = ReminderSettings(defaults: storage.defaults)
        #expect(!settings.preferences.azkarStreak)

        settings.setStreak(true)

        #expect(storage.defaults.bool(forKey: "reminders.azkar.streak"))
        #expect(ReminderSettings(defaults: storage.defaults).preferences.azkarStreak)
        #expect(settings.offerAnswered, "включение отвечает и на предложение на главной")
    }
}
