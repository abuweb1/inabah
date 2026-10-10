import Foundation
import Testing
@testable import Inabah

/// Планирование напоминаний — как на Android (`ReminderPlannerTest`), плюс неделя вперёд (особенность iOS).
@Suite("Планирование напоминаний")
struct ReminderPlannerTests {
    private static let berlin: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
        return calendar
    }()

    private static let windows: [AzkarSection: AzkarWindow] = [
        .morning: AzkarWindow(start: DayTime(hour: 5, minute: 0), end: DayTime(hour: 12, minute: 0)),
        .evening: AzkarWindow(start: DayTime(hour: 17, minute: 0), end: DayTime(hour: 2, minute: 0)),
    ]

    private static func date(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        berlin.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }

    private static func plan(
        _ preferences: ReminderPreferences,
        now: Date,
        windows: [AzkarSection: AzkarWindow] = windows,
        days: Int = 1,
        completed: Set<String> = []
    ) -> [PlannedReminder] {
        ReminderPlan.plan(preferences: preferences, windows: windows, now: now, calendar: berlin, days: days) { section, day in
            completed.contains("\(section.rawValue) \(day)")
        }
    }

    @Test("В начале времени — ближайшее начало строго после «сейчас»")
    func startStrictlyAfterNow() {
        let atStart = Self.plan(ReminderPreferences(azkarStart: [.morning]), now: Self.date(2026, 10, 12, 5))

        #expect(atStart.map(\.at) == [Self.date(2026, 10, 13, 5)])
        #expect(atStart.first?.windowDay == "2026-10-13")
    }

    @Test("«Ещё не прочитаны» — за 2 ч до конца; вечерние — через полночь, день окна — день начала")
    func unfinishedBeforeEnd() {
        let preferences = ReminderPreferences(azkarStart: [.morning, .evening], azkarUnfinished: true)
        let planned = Self.plan(preferences, now: Self.date(2026, 10, 12, 6))

        let morning = planned.first { $0.kind == .morningUnfinished }
        let evening = planned.first { $0.kind == .eveningUnfinished }
        #expect(morning?.at == Self.date(2026, 10, 12, 10))
        #expect(morning?.windowDay == "2026-10-12")
        #expect(evening?.at == Self.date(2026, 10, 13, 0))
        #expect(evening?.windowDay == "2026-10-12")
    }

    @Test("Окно короче 2 ч — «ещё не прочитаны» в его середине")
    func shortWindowMiddle() {
        let windows: [AzkarSection: AzkarWindow] = [.morning: AzkarWindow(start: DayTime(hour: 6, minute: 0), end: DayTime(hour: 7, minute: 0))]
        let planned = Self.plan(ReminderPreferences(azkarStart: [.morning], azkarUnfinished: true), now: Self.date(2026, 10, 12, 5), windows: windows)

        #expect(planned.first { $0.kind == .morningUnfinished }?.at == Self.date(2026, 10, 12, 6, 30))
    }

    @Test("«Ещё не прочитаны» — только для разделов с напоминанием в начале и не на выполненные окна")
    func unfinishedRules() {
        let onlyEvening = Self.plan(ReminderPreferences(azkarStart: [.evening], azkarUnfinished: true), now: Self.date(2026, 10, 12, 6))
        #expect(!onlyEvening.contains { $0.kind == .morningUnfinished })

        let done = Self.plan(
            ReminderPreferences(azkarStart: [.morning], azkarUnfinished: true),
            now: Self.date(2026, 10, 12, 6),
            days: 2,
            completed: ["morning 2026-10-12"]
        )
        #expect(done.filter { $0.kind == .morningUnfinished }.map(\.windowDay) == ["2026-10-13"])
    }

    @Test("Неделя всех напоминаний — не больше 35 запросов (лимит iOS — 64)")
    func weekFitsLimit() {
        let all = ReminderPreferences(azkarStart: [.morning, .evening], azkarUnfinished: true, hadithOfDay: true)
        let planned = Self.plan(all, now: Self.date(2026, 10, 12, 6), days: ReminderPlan.horizonDays)

        #expect(planned.count == 35)
        #expect(Set(planned.map(\.identifier)).count == planned.count)
        #expect(planned.allSatisfy { $0.at > Self.date(2026, 10, 12, 6) })
    }

    @Test("Весенний перевод часов: несуществующее 02:30 → 03:00 (Т3)")
    func springForward() {
        var preferences = ReminderPreferences(hadithOfDay: true)
        preferences.hadithTime = DayTime(hour: 2, minute: 30)
        let planned = Self.plan(preferences, now: Self.date(2026, 3, 28, 12), days: 2)

        #expect(planned.map(\.at) == [Self.date(2026, 3, 29, 3), Self.date(2026, 3, 30, 2, 30)])
    }

    @Test("Осенний перевод часов: повторяющийся час — один раз (Т4)")
    func fallBack() {
        var preferences = ReminderPreferences(hadithOfDay: true)
        preferences.hadithTime = DayTime(hour: 2, minute: 30)
        let planned = Self.plan(preferences, now: Self.date(2026, 10, 24, 12), days: 3)

        #expect(planned.count == 3)
        #expect(Set(planned.map { Self.berlin.component(.day, from: $0.at) }) == [25, 26, 27])
    }

    @Test("Пустое окно (начало = конец) не планируется")
    func invalidWindow() {
        let windows: [AzkarSection: AzkarWindow] = [.morning: AzkarWindow(start: DayTime(hour: 6, minute: 0), end: DayTime(hour: 6, minute: 0))]
        #expect(Self.plan(ReminderPreferences(azkarStart: [.morning]), now: Self.date(2026, 10, 12, 5), windows: windows).isEmpty)
    }

    // MARK: - Показывать ли (Т1)

    @Test("Показывать ли напоминание азкаров", arguments: [
        (ReminderKind.morningUnfinished, 10, AzkarDayRecord(completed: 16, total: 16) as AzkarDayRecord?, false),
        (.morningUnfinished, 10, AzkarDayRecord(completed: 5, total: 16), true),
        (.morningUnfinished, 10, AzkarDayRecord(completed: 0, total: 0), true),
        (.morningUnfinished, 10, nil, true),
        (.morningStart, 12, nil, false),
    ])
    func due(kind: ReminderKind, hour: Int, record: AzkarDayRecord?, expected: Bool) {
        let window = Self.windows[.morning]!
        let due = ReminderPlan.isAzkarReminderDue(kind: kind, window: window, now: Self.date(2026, 10, 12, hour, 30), calendar: Self.berlin) { _ in record }
        #expect(due == expected)
    }

    @Test("Вечерние после полуночи — запись вчерашнего дня окна")
    func eveningAfterMidnight() {
        var asked: String?
        _ = ReminderPlan.isAzkarReminderDue(kind: .eveningUnfinished, window: Self.windows[.evening]!, now: Self.date(2026, 10, 13, 1), calendar: Self.berlin) {
            asked = $0
            return nil
        }
        #expect(asked == "2026-10-12")
    }

    // MARK: - Хадис дня

    private static func hadiths(_ collection: HadithCollection, _ count: Int) -> [Hadith] {
        (1...count).map { Hadith(id: HadithID(collection: collection, number: $0), arabic: "ا", translation: nil) }
    }

    private static let library: [HadithCollection: [Hadith]] = [.nawawi: hadiths(.nawawi, 3), .qudsi: hadiths(.qudsi, 2)]

    @Test("Хадис дня — первый непрочитанный по порядку главной")
    func firstUnread() {
        let read: Set<HadithID> = [HadithID(collection: .qudsi, number: 1)]
        let pick = ReminderPlan.pickHadithOfDay(order: [.qudsi, .nawawi], hadiths: Self.library, isRead: { read.contains($0) }, last: nil)
        #expect(pick?.id == HadithID(collection: .qudsi, number: 2))
    }

    @Test("Всё прочитано — по кругу после показанного; показанный не найден — первый")
    func cycle() {
        let last = HadithID(collection: .qudsi, number: 2)
        #expect(ReminderPlan.pickHadithOfDay(order: [.nawawi, .qudsi], hadiths: Self.library, isRead: { _ in true }, last: last)?.id
            == HadithID(collection: .nawawi, number: 1))
        #expect(ReminderPlan.pickHadithOfDay(order: [.nawawi, .qudsi], hadiths: Self.library, isRead: { _ in true }, last: HadithID(collection: .ajurri, number: 9))?.id
            == HadithID(collection: .nawawi, number: 1))
        #expect(ReminderPlan.pickHadithOfDay(order: [.ajurri], hadiths: [:], isRead: { _ in false }, last: nil) == nil)
    }

    @Test("Неделя хадисов — подряд непрочитанные, затем по кругу")
    func sequence() {
        let picks = ReminderPlan.pickHadiths(count: 6, order: [.nawawi, .qudsi], hadiths: Self.library, isRead: { $0.number == 2 }, last: nil)
        #expect(picks.map(\.id) == [
            HadithID(collection: .nawawi, number: 1),
            HadithID(collection: .nawawi, number: 3),
            HadithID(collection: .qudsi, number: 1),
            // Непрочитанные кончились — по кругу после последнего показанного (къудси № 1).
            HadithID(collection: .qudsi, number: 2),
            HadithID(collection: .nawawi, number: 1),
            HadithID(collection: .nawawi, number: 2),
        ])
    }

    // MARK: - Нажатие (Т7)

    @Test("Разбор нажатия: раздел, хадис; неизвестное и номер 0 — ничего")
    func payload() {
        #expect(ReminderPayload.target(from: ReminderPayload.userInfo(kind: .eveningUnfinished)) == .azkar(.evening))
        let id = HadithID(collection: .qudsi, number: 7)
        #expect(ReminderPayload.target(from: ReminderPayload.userInfo(kind: .hadithOfDay, hadith: id)) == .hadith(id))
        #expect(ReminderPayload.target(from: ["kind": "hadithOfDay", "collection": "bukhari", "number": "1"]) == nil)
        #expect(ReminderPayload.target(from: ["kind": "hadithOfDay", "collection": "nawawi", "number": "0"]) == nil)
        #expect(ReminderPayload.target(from: ["kind": "x"]) == nil)
        #expect(ReminderPayload.target(from: [:]) == nil)
    }
}
