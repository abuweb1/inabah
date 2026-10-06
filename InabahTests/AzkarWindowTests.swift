import Foundation
import Testing
@testable import Inabah

/// Календарь с фиксированным часовым поясом (с переходом на летнее время) — тесты не зависят от машины.
private let berlin: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
    return calendar
}()

/// Москва — на час впереди Берлина осенью (летнего времени нет).
private let moscow: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Europe/Moscow")!
    return calendar
}()

/// Дата по берлинскому времени.
private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
    berlin.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
}

/// Дата по московскому времени.
private func moscowDate(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
    moscow.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
}

private func window(_ startHour: Int, _ endHour: Int) -> AzkarWindow {
    AzkarWindow(start: DayTime(hour: startHour, minute: 0), end: DayTime(hour: endHour, minute: 0))
}

@Suite("Расписание времени азкаров")
struct AzkarWindowScheduleTests {
    private let morning = AzkarWindowSchedule(window: window(5, 12), calendar: berlin)
    private let evening = AzkarWindowSchedule(window: window(17, 2), calendar: berlin)

    @Test("Утренние 5:00–12:00: начало — уже окно, конец — уже промежуток", arguments: [
        (date(2026, 10, 3, 4, 59), AzkarPeriod(kind: .gap, day: "2026-10-03", validUntil: date(2026, 10, 3, 5))),
        (date(2026, 10, 3, 5), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 3, 12))),
        (date(2026, 10, 3, 11, 59), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 3, 12))),
        (date(2026, 10, 3, 12), AzkarPeriod(kind: .gap, day: "2026-10-04", validUntil: date(2026, 10, 4, 5))),
        (date(2026, 10, 3, 23), AzkarPeriod(kind: .gap, day: "2026-10-04", validUntil: date(2026, 10, 4, 5))),
    ])
    func morningPeriods(now: Date, expected: AzkarPeriod) {
        #expect(morning.period(at: now) == expected)
    }

    @Test("Вечерние 17:00–02:00: прочитанное после полуночи относится к дате начала", arguments: [
        (date(2026, 10, 3, 16, 59), AzkarPeriod(kind: .gap, day: "2026-10-03", validUntil: date(2026, 10, 3, 17))),
        (date(2026, 10, 3, 21), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 4, 2))),
        (date(2026, 10, 4, 0, 30), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 4, 2))),
        (date(2026, 10, 4, 2), AzkarPeriod(kind: .gap, day: "2026-10-04", validUntil: date(2026, 10, 4, 17))),
    ])
    func eveningPeriods(now: Date, expected: AzkarPeriod) {
        #expect(evening.period(at: now) == expected)
    }

    @Test("Ночь перевода часов на летнее время: 02:00 нет — окно до 03:00")
    func springForward() {
        // 29 марта 2026, Европа/Берлин: 02:00 → 03:00.
        let end = evening.period(at: date(2026, 3, 28, 21)).validUntil
        #expect(berlin.component(.hour, from: end) == 3)
        #expect(berlin.component(.day, from: end) == 29)
    }

    @Test("Осенний перевод часов: 02:00 повторяется, но окно заканчивается один раз")
    func fallBack() {
        // 25 октября 2026, Европа/Берлин: 03:00 летнего → 02:00 зимнего.
        let firstTwo = date(2026, 10, 25, 1).addingTimeInterval(60 * 60)
        #expect(evening.period(at: date(2026, 10, 24, 21)).validUntil == firstTwo)

        let afterEnd = evening.period(at: firstTwo)
        let repeatedTwo = evening.period(at: firstTwo.addingTimeInterval(60 * 60))
        #expect(afterEnd.kind == .gap)
        #expect(repeatedTwo.isSameSpan(as: afterEnd))
    }

    @Test("Москва → Берлин внутри окна — то же окно")
    func sameWindowAcrossTimeZones() {
        let inMoscow = AzkarWindowSchedule(window: window(5, 12), calendar: moscow).period(at: moscowDate(2026, 10, 3, 8))
        let inBerlin = morning.period(at: date(2026, 10, 3, 9))
        #expect(inBerlin.isSameSpan(as: inMoscow))
        #expect(inBerlin.validUntil == date(2026, 10, 3, 12))
    }
}

@MainActor
@Suite("Время азкаров в настройках")
struct AzkarWindowSettingsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("AzkarWindowSettingsTests")
    }

    @Test("По умолчанию утренние — 5:00–12:00, вечерние — 17:00–02:00")
    func defaultWindows() {
        let settings = AzkarWindowSettings(defaults: defaults)
        #expect(settings.window(for: .morning) == window(5, 12))
        #expect(settings.window(for: .evening) == window(17, 2))
    }

    @Test("Новое время сохраняется между запусками")
    func persists() {
        let settings = AzkarWindowSettings(defaults: defaults)
        settings.setStart(DayTime(hour: 4, minute: 30), for: .morning)
        settings.setEnd(DayTime(hour: 11, minute: 0), for: .morning)

        let restored = AzkarWindowSettings(defaults: defaults).window(for: .morning)
        #expect(restored == AzkarWindow(start: DayTime(hour: 4, minute: 30), end: DayTime(hour: 11, minute: 0)))
    }

    @Test("Начало, совпадающее с концом, не сохраняется")
    func rejectsEmptyWindow() {
        let settings = AzkarWindowSettings(defaults: defaults)
        settings.setStart(DayTime(hour: 12, minute: 0), for: .morning)

        #expect(settings.window(for: .morning) == window(5, 12))
        #expect(AzkarWindowSettings(defaults: defaults).window(for: .morning) == window(5, 12))
    }

    @Test("Время обнуления версии 1.0.0 не переносится и удаляется")
    func dropsLegacyResetTime() {
        defaults.set(16 * 60, forKey: "azkar.reset.morning")

        let settings = AzkarWindowSettings(defaults: defaults)

        #expect(settings.window(for: .morning) == window(5, 12))
        #expect(defaults.object(forKey: "azkar.reset.morning") == nil)
    }
}

@MainActor
@Suite("Прогресс азкаров во времени азкаров")
struct AzkarProgressPersistenceTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }
    /// Текущее время — меняется тестом.
    private final class Clock {
        var now: Date
        init(_ now: Date) { self.now = now }
    }
    private let clock = Clock(date(2026, 10, 3, 8))

    init() throws {
        storage = try IsolatedDefaults("AzkarProgressPersistenceTests")
    }

    private static let repository = InMemoryContentRepository(azkar: Dictionary(uniqueKeysWithValues: AzkarSection.allCases.map { section in
        (section, [1, 2].map {
            Zikr(id: ZikrID(section: section, number: $0), arabic: "سُبْحَانَ اللَّهِ",
                 repetitions: 3, audioFileName: "\(section.rawValue)_0\($0).mp3", translation: nil)
        })
    }))

    private func makeStore(
        _ section: AzkarSection = .morning,
        calendar: Calendar = berlin,
        windowSettings: AzkarWindowSettings? = nil
    ) async -> AzkarStore {
        let store = AzkarStore(
            repository: Self.repository,
            defaults: defaults,
            windowSettings: windowSettings,
            now: { [clock] in clock.now },
            calendar: calendar
        )
        await store.load(section)
        return store
    }

    private func complete(_ section: AzkarSection, in store: AzkarStore) {
        store.sessions(in: section).forEach { session in (0..<3).forEach { _ in session.increment() } }
    }

    // MARK: Отрезки

    @Test("Счёт сохраняется между запусками в пределах окна")
    func restoresWithinWindow() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()
        store.sessions(in: .morning)[1].increment()
        store.sessions(in: .morning)[1].increment()

        clock.now = date(2026, 10, 3, 11, 59)
        let restored = await makeStore()

        #expect(restored.isInWindow(.morning))
        #expect(restored.sessions(in: .morning).map(\.count) == [1, 2])
    }

    @Test("Конец окна — строгий сброс, пока приложение открыто: счёт и оверлей")
    func strictResetAtWindowEnd() async {
        let store = await makeStore()
        complete(.morning, in: store)
        store.acknowledgeCompletion(of: .morning)

        clock.now = date(2026, 10, 3, 12)
        store.reconcile()

        #expect(!store.isInWindow(.morning))
        #expect(store.progress(of: .morning).completed == 0)
        let reopened = await makeStore()
        #expect(reopened.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("После конца окна сохранённый счёт не восстанавливается")
    func discardsAfterWindowBetweenLaunches() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 3, 12, 1)
        let restored = await makeStore()

        #expect(restored.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("Счёт вне окна работает, сохраняется и обнуляется к началу окна")
    func gapCountsResetAtWindowStart() async {
        clock.now = date(2026, 10, 3, 13)
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()
        #expect(!store.isInWindow(.morning))

        clock.now = date(2026, 10, 4, 4, 59)
        let restored = await makeStore()
        #expect(restored.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 4, 5)
        restored.reconcile()
        #expect(restored.isInWindow(.morning))
        #expect(restored.sessions(in: .morning)[0].count == 0)
    }

    @Test("Оверлей завершения — только во время азкаров")
    func completionOnlyInWindow() async {
        clock.now = date(2026, 10, 3, 13)
        let outside = await makeStore()
        complete(.morning, in: outside)
        #expect(outside.progress(of: .morning).isFinished)
        #expect(!outside.shouldPresentCompletion(of: .morning))

        clock.now = date(2026, 10, 4, 8)
        outside.reconcile()
        complete(.morning, in: outside)
        #expect(outside.shouldPresentCompletion(of: .morning))
    }

    @Test("Перелёт Москва → Берлин между запусками: окно до 12:00 по Берлину")
    func timeZoneChangeBetweenLaunches() async {
        clock.now = moscowDate(2026, 10, 3, 8)
        let store = await makeStore(calendar: moscow)
        store.sessions(in: .morning)[0].increment()

        // 11:30 по Берлину = 12:30 МСК: московское окно закончилось, берлинское — ещё нет.
        clock.now = date(2026, 10, 3, 11, 30)
        let restored = await makeStore()
        #expect(restored.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 3, 12)
        restored.reconcile()
        #expect(restored.sessions(in: .morning)[0].count == 0)
    }

    @Test("Смена времени: важна только итоговая граница, промежуточные значения колеса ничего не стирают")
    func changingWindowIsPathIndependent() async {
        let settings = AzkarWindowSettings(defaults: defaults)
        let store = await makeStore(windowSettings: settings)
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 3, 11)
        for hour in [10, 9, 13] {
            settings.setEnd(DayTime(hour: hour, minute: 0), for: .morning)
        }
        store.reconcile()
        #expect(store.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 3, 12, 30)
        store.reconcile()
        #expect(store.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 3, 13)
        store.reconcile()
        #expect(store.sessions(in: .morning)[0].count == 0)
    }

    @Test("Конец окна перенесли на уже прошедшее время — окно закончилось")
    func shorteningWindowEndsIt() async {
        let settings = AzkarWindowSettings(defaults: defaults)
        let store = await makeStore(windowSettings: settings)
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 3, 11)
        settings.setEnd(DayTime(hour: 10, minute: 0), for: .morning)
        store.reconcile()

        #expect(!store.isInWindow(.morning))
        #expect(store.sessions(in: .morning)[0].count == 0)
    }

    @Test("Сохранение версии 1.0.0 не восстанавливается")
    func ignoresLegacyFormat() async {
        let reset = date(2026, 10, 3, 17).timeIntervalSinceReferenceDate
        let legacy = #"{"period":{"scheduledReset":\#(reset),"validUntil":\#(reset),"timeZone":"Europe/Berlin"},"counts":{"1":2},"completionShown":false}"#
        defaults.set(Data(legacy.utf8), forKey: "azkar.progress.morning")

        let store = await makeStore()

        #expect(store.sessions(in: .morning).map(\.count) == [0, 0])
    }

    // MARK: Ручной сброс и оверлей

    @Test("Ручной сброс из настроек обнуляет раздел и сохраняется")
    func manualReset() async {
        let store = await makeStore()
        complete(.morning, in: store)
        store.acknowledgeCompletion(of: .morning)

        store.resetProgress(of: .morning)

        #expect(store.sessions(in: .morning).allSatisfy { $0.count == 0 })
        #expect(!store.shouldPresentCompletion(of: .morning))
        let reopened = await makeStore()
        #expect(reopened.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("Ручной сброс не загруженного раздела забывает сохранённый прогресс")
    func manualResetOfUnloadedSection() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()

        let fresh = AzkarStore(repository: Self.repository, defaults: defaults, now: { [clock] in clock.now }, calendar: berlin)
        fresh.resetProgress(of: .morning)
        await fresh.load(.morning)

        #expect(fresh.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("Оверлей завершения после перезапуска не показывается снова")
    func completionAcknowledgementPersists() async {
        let store = await makeStore()
        complete(.morning, in: store)
        store.acknowledgeCompletion(of: .morning)

        let reopened = await makeStore()

        #expect(reopened.progress(of: .morning).isFinished)
        #expect(!reopened.shouldPresentCompletion(of: .morning))
    }

    @Test("Есть ли что сбрасывать — и у не загруженного раздела")
    func hasProgressOfUnloadedSection() async {
        let store = await makeStore()
        #expect(!store.hasProgress(of: .morning))
        store.sessions(in: .morning)[0].increment()
        #expect(store.hasProgress(of: .morning))

        let fresh = AzkarStore(repository: Self.repository, defaults: defaults, now: { [clock] in clock.now }, calendar: berlin)
        #expect(fresh.hasProgress(of: .morning))
        fresh.resetProgress(of: .morning)
        #expect(!fresh.hasProgress(of: .morning))
    }

    @Test("Сброс одной карточки сохраняется")
    func cardResetPersists() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()
        store.sessions(in: .morning)[0].reset()

        let reopened = await makeStore()

        #expect(reopened.sessions(in: .morning).map(\.count) == [0, 0])
    }

    // MARK: История

    @Test("История: выполнено X из N по дате окна, сохраняется после конца окна")
    func historyRecordsWindow() async {
        let store = await makeStore()
        (0..<3).forEach { _ in store.sessions(in: .morning)[0].increment() }

        #expect(AzkarHistory(defaults: defaults).record(of: .morning, on: "2026-10-03") == AzkarDayRecord(completed: 1, total: 2))

        clock.now = date(2026, 10, 3, 12)
        store.reconcile()
        #expect(AzkarHistory(defaults: defaults).record(of: .morning, on: "2026-10-03")?.fraction == 0.5)
    }

    @Test("История: вечерние после полуночи — запись за дату начала окна")
    func historyUsesWindowStartDay() async {
        clock.now = date(2026, 10, 4, 0, 30)
        let store = await makeStore(.evening)
        complete(.evening, in: store)

        let history = AzkarHistory(defaults: defaults)
        #expect(history.record(of: .evening, on: "2026-10-03") == AzkarDayRecord(completed: 2, total: 2))
        #expect(history.record(of: .evening, on: "2026-10-04") == nil)
    }

    @Test("История: счёт вне окна не записывается")
    func historyIgnoresGap() async {
        clock.now = date(2026, 10, 3, 13)
        let store = await makeStore()
        complete(.morning, in: store)

        let history = AzkarHistory(defaults: defaults)
        #expect(history.record(of: .morning, on: "2026-10-03") == nil)
        #expect(history.record(of: .morning, on: "2026-10-04") == nil)
    }

    @Test("История: ручной сброс в окне убирает запись дня")
    func historyClearedByManualReset() async {
        let store = await makeStore()
        complete(.morning, in: store)

        store.resetProgress(of: .morning)

        #expect(AzkarHistory(defaults: defaults).record(of: .morning, on: "2026-10-03") == nil)
    }
}
