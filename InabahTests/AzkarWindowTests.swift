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
        (date(2026, 10, 3, 4, 59), AzkarPeriod(kind: .gap, day: "2026-10-03", validUntil: date(2026, 10, 3, 5), startedOn: "2026-10-02")),
        (date(2026, 10, 3, 5), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 3, 12))),
        (date(2026, 10, 3, 11, 59), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 3, 12))),
        (date(2026, 10, 3, 12), AzkarPeriod(kind: .gap, day: "2026-10-04", validUntil: date(2026, 10, 4, 5), startedOn: "2026-10-03")),
        (date(2026, 10, 3, 23), AzkarPeriod(kind: .gap, day: "2026-10-04", validUntil: date(2026, 10, 4, 5), startedOn: "2026-10-03")),
    ])
    func morningPeriods(now: Date, expected: AzkarPeriod) {
        #expect(morning.period(at: now) == expected)
    }

    @Test("Вечерние 17:00–02:00: прочитанное после полуночи относится к дате начала", arguments: [
        (date(2026, 10, 3, 16, 59), AzkarPeriod(kind: .gap, day: "2026-10-03", validUntil: date(2026, 10, 3, 17), startedOn: "2026-10-02")),
        (date(2026, 10, 3, 21), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 4, 2))),
        (date(2026, 10, 4, 0, 30), AzkarPeriod(kind: .window, day: "2026-10-03", validUntil: date(2026, 10, 4, 2))),
        (date(2026, 10, 4, 2), AzkarPeriod(kind: .gap, day: "2026-10-04", validUntil: date(2026, 10, 4, 17), startedOn: "2026-10-03")),
    ])
    func eveningPeriods(now: Date, expected: AzkarPeriod) {
        #expect(evening.period(at: now) == expected)
    }

    @Test("Счёт продолжается до начала следующего окна: окно → промежуток после него, но не новое окно")
    func progressContinuesUntilNextStart() {
        let inWindow = morning.period(at: date(2026, 10, 3, 8))
        let afterEnd = morning.period(at: date(2026, 10, 3, 13))
        let nextWindow = morning.period(at: date(2026, 10, 4, 5))
        let nextGap = morning.period(at: date(2026, 10, 4, 13))

        #expect(afterEnd.continuesProgress(inWindow))
        #expect(!nextWindow.continuesProgress(afterEnd))
        #expect(!nextGap.continuesProgress(afterEnd), "пропущенное окно целиком")
    }

    @Test("Без даты начала (сохранение до 2026-10-20) - прежнее правило: тот же вид и дата")
    func legacyPeriodWithoutStart() throws {
        let legacyWindow = try JSONDecoder().decode(
            AzkarPeriod.self,
            from: Data(#"{"kind":"window","day":"2026-10-03","validUntil":0}"#.utf8)
        )
        #expect(legacyWindow.startedOn == nil)
        #expect(morning.period(at: date(2026, 10, 3, 8)).continuesProgress(legacyWindow))
        #expect(!morning.period(at: date(2026, 10, 3, 13)).continuesProgress(legacyWindow))

        let encoded = try JSONEncoder().encode(morning.period(at: date(2026, 10, 3, 13)))
        #expect(String(decoding: encoded, as: UTF8.self).contains(#""started":"2026-10-03""#))
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
        #expect(repeatedTwo.continuesProgress(afterEnd))
    }

    @Test("Москва → Берлин внутри окна — то же окно")
    func sameWindowAcrossTimeZones() {
        let inMoscow = AzkarWindowSchedule(window: window(5, 12), calendar: moscow).period(at: moscowDate(2026, 10, 3, 8))
        let inBerlin = morning.period(at: date(2026, 10, 3, 9))
        #expect(inBerlin.continuesProgress(inMoscow))
        #expect(inBerlin.validUntil == date(2026, 10, 3, 12))
    }

    @Test("Хиджра в настройках iPhone: ключ дня григорианский, границы те же", arguments: [
        Calendar.Identifier.islamicUmmAlQura, .japanese, .buddhist, .chinese,
    ])
    func dayKeyIgnoresUserCalendar(identifier: Calendar.Identifier) {
        var userCalendar = Calendar(identifier: identifier)
        userCalendar.timeZone = berlin.timeZone
        let schedule = AzkarWindowSchedule(window: window(17, 2), calendar: userCalendar)

        let period = schedule.period(at: date(2026, 10, 4, 0, 30))

        #expect(period == evening.period(at: date(2026, 10, 4, 0, 30)))
        #expect(period.day == "2026-10-03")
    }
}

@Suite("Время суток для показа и колеса")
struct DayTimeDateTests {
    @Test("Туда и обратно — те же часы и минуты, и в часы перевода часов", arguments: [
        DayTime(hour: 0, minute: 0), DayTime(hour: 2, minute: 0), DayTime(hour: 2, minute: 30),
        DayTime(hour: 12, minute: 0), DayTime(hour: 23, minute: 59),
    ])
    func roundTrip(time: DayTime) {
        let date = time.date(in: berlin)
        #expect(DayTime(date, in: berlin) == time)
    }

    @Test("Опорный день не зависит от календаря пользователя")
    func referenceDayIsGregorian() {
        var hijri = Calendar(identifier: .islamicUmmAlQura)
        hijri.timeZone = berlin.timeZone
        let time = DayTime(hour: 2, minute: 30)

        #expect(time.date(in: hijri) == time.date(in: berlin))
        #expect(DayTime(time.date(in: hijri), in: hijri) == time)
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

    @Test("По умолчанию утренние — 6:00–11:30, вечерние — 17:00–02:00")
    func defaultWindows() {
        let settings = AzkarWindowSettings(defaults: defaults)
        #expect(settings.window(for: .morning) == AzkarWindow(start: DayTime(hour: 6, minute: 0), end: DayTime(hour: 11, minute: 30)))
        #expect(settings.window(for: .evening) == window(17, 2))
    }

    @Test("Новое время сохраняется между запусками")
    func persists() {
        let settings = AzkarWindowSettings(defaults: defaults)
        let edited = AzkarWindow(start: DayTime(hour: 4, minute: 30), end: DayTime(hour: 11, minute: 0))
        settings.set(edited, for: .morning)

        #expect(AzkarWindowSettings(defaults: defaults).window(for: .morning) == edited)
        #expect(settings.window(for: .evening) == window(17, 2))
    }

    @Test("Начало, совпадающее с концом, не сохраняется")
    func rejectsEmptyWindow() {
        let settings = AzkarWindowSettings(defaults: defaults)
        settings.set(window(12, 12), for: .morning)

        #expect(!window(12, 12).isValid)
        #expect(settings.window(for: .morning) == AzkarWindowSettings.defaultWindow(for: .morning))
        #expect(AzkarWindowSettings(defaults: defaults).window(for: .morning) == AzkarWindowSettings.defaultWindow(for: .morning))
    }

    @Test("Повторное сохранение того же окна не уведомляет наблюдателей")
    func sameWindowDoesNotNotify() {
        let settings = AzkarWindowSettings(defaults: defaults)
        let notified = NotificationFlag()
        withObservationTracking {
            _ = settings.window(for: .morning)
        } onChange: {
            notified.set()
        }

        settings.set(AzkarWindowSettings.defaultWindow(for: .morning), for: .morning)

        #expect(!notified.value)
    }

    @Test("Время обнуления версии 1.0.0 не переносится и удаляется")
    func dropsLegacyResetTime() {
        defaults.set(16 * 60, forKey: "azkar.reset.morning")

        let settings = AzkarWindowSettings(defaults: defaults)

        #expect(settings.window(for: .morning) == AzkarWindowSettings.defaultWindow(for: .morning))
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
                 repetitions: 3, translation: nil)
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
            windowSettings: windowSettings ?? classicWindows(),
            now: { [clock] in clock.now },
            calendar: calendar
        )
        await store.load(section)
        return store
    }

    /// Механика проверяется на явном окне 5:00–12:00 (Android `classicMorningWindow`): числа не зависят
    /// от умолчаний. Окно сохраняется в настройки - его видят и сторы, созданные без `makeStore`.
    private func classicWindows() -> AzkarWindowSettings {
        let settings = AzkarWindowSettings(defaults: defaults)
        settings.set(window(5, 12), for: .morning)
        return settings
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

    @Test("Конец окна оставляет счёт и пометку оверлея до начала следующего, пока приложение открыто")
    func windowEndKeepsProgress() async {
        let store = await makeStore()
        complete(.morning, in: store)
        store.acknowledgeCompletion(of: .morning)

        clock.now = date(2026, 10, 3, 12)
        store.reconcile()

        #expect(!store.isInWindow(.morning))
        #expect(store.progress(of: .morning).isFinished)
        #expect(!store.shouldPresentCompletion(of: .morning))
        let reopened = await makeStore()
        #expect(reopened.sessions(in: .morning).map(\.count) == [3, 3])
    }

    @Test("После конца окна сохранённый счёт восстанавливается")
    func restoresAfterWindowBetweenLaunches() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 4, 4, 59)
        let restored = await makeStore()

        #expect(!restored.isInWindow(.morning))
        #expect(restored.sessions(in: .morning).map(\.count) == [1, 0])
    }

    @Test("Начало следующего окна обнуляет счёт и пометку оверлея - по таймеру и при запуске")
    func nextWindowStartResets() async {
        let store = await makeStore()
        complete(.morning, in: store)
        store.acknowledgeCompletion(of: .morning)
        clock.now = date(2026, 10, 3, 12)
        store.reconcile()

        clock.now = date(2026, 10, 4, 5)
        let launched = await makeStore()
        #expect(launched.isInWindow(.morning))
        #expect(launched.sessions(in: .morning).map(\.count) == [0, 0])

        store.reconcile()
        #expect(store.sessions(in: .morning).map(\.count) == [0, 0])
        complete(.morning, in: store)
        #expect(store.shouldPresentCompletion(of: .morning))
    }

    @Test("Пропущено окно целиком - счёт промежутка сбрасывается")
    func skippedWindowResets() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 4, 13)
        let restored = await makeStore()

        #expect(!restored.isInWindow(.morning))
        #expect(restored.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("Сохранение до 2026-10-20 (без started) - прежнее правило: окно того же дня продолжается, после конца - сброс")
    func legacySaveWithoutStart() async throws {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()
        let key = "azkar.progress.morning"
        let saved = try #require(defaults.data(forKey: key))
        var json = try #require(JSONSerialization.jsonObject(with: saved) as? [String: Any])
        var period = try #require(json["period"] as? [String: Any])
        #expect(period.removeValue(forKey: "started") != nil)
        json["period"] = period
        let legacy = try JSONSerialization.data(withJSONObject: json)

        defaults.set(legacy, forKey: key)
        clock.now = date(2026, 10, 3, 11)
        #expect(await makeStore().sessions(in: .morning).map(\.count) == [1, 0])

        defaults.set(legacy, forKey: key)
        clock.now = date(2026, 10, 3, 13)
        #expect(await makeStore().sessions(in: .morning).map(\.count) == [0, 0])
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
        #expect(!restored.isInWindow(.morning))
        #expect(restored.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 4, 5)
        restored.reconcile()
        #expect(restored.sessions(in: .morning)[0].count == 0)
    }

    @Test("Продлили окно (сохранённый черновик) — счёт остаётся до новой границы")
    func extendingWindowKeepsProgress() async {
        let settings = classicWindows()
        let store = await makeStore(windowSettings: settings)
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 3, 11)
        settings.set(window(5, 13), for: .morning)
        store.reconcile()
        #expect(store.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 3, 12, 30)
        store.reconcile()
        #expect(store.isInWindow(.morning))
        #expect(store.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 3, 13)
        store.reconcile()
        #expect(!store.isInWindow(.morning))
        #expect(store.sessions(in: .morning)[0].count == 1)
    }

    @Test("Конец окна перенесли на уже прошедшее время — окно закончилось, прочитанное остаётся")
    func shorteningWindowEndsIt() async {
        let settings = classicWindows()
        let store = await makeStore(windowSettings: settings)
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 3, 11)
        settings.set(window(5, 10), for: .morning)
        store.reconcile()

        #expect(!store.isInWindow(.morning))
        #expect(store.sessions(in: .morning)[0].count == 1)
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

    @Test("История: сброс карточки после конца окна запись дня не трогает")
    func historyKeptByCardResetAfterWindow() async {
        let store = await makeStore()
        complete(.morning, in: store)

        clock.now = date(2026, 10, 3, 13)
        store.reconcile()
        store.sessions(in: .morning)[0].reset()

        #expect(store.sessions(in: .morning).map(\.count) == [0, 3])
        #expect(AzkarHistory(defaults: defaults).record(of: .morning, on: "2026-10-03") == AzkarDayRecord(completed: 2, total: 2))
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

    // MARK: День серии

    @Test("День серии: до загрузки вечерних нет, дальше — день вечернего окна, новый — после его конца")
    func streakDayFollowsEveningWindow() async throws {
        let morningOnly = await makeStore(.morning)
        #expect(morningOnly.streakDay == nil)

        let store = await makeStore(.evening)
        let october3 = try #require(AzkarCalendarDay(key: "2026-10-03"))
        #expect(store.streakDay == october3)  // 08:00 — промежуток до вечерних 3-го

        clock.now = date(2026, 10, 3, 18)
        store.reconcile()
        #expect(store.streakDay == october3)

        clock.now = date(2026, 10, 4, 1)
        store.reconcile()
        #expect(store.streakDay == october3)  // вечерние 3-го ещё идут

        clock.now = date(2026, 10, 4, 3)
        store.reconcile()
        #expect(store.streakDay == october3.adding(days: 1))
    }
}
