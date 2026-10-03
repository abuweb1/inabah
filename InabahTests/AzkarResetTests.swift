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

@Suite("Расписание обнуления")
struct AzkarResetScheduleTests {
    private let morning = AzkarResetSchedule(time: DayTime(hour: 17, minute: 0), calendar: berlin)
    private let evening = AzkarResetSchedule(time: DayTime(hour: 2, minute: 0), calendar: berlin)

    @Test("Утренние: до 17:00 — период с вчерашних 17:00, после — с сегодняшних", arguments: [
        (date(2026, 10, 3, 16, 59), date(2026, 10, 2, 17)),
        (date(2026, 10, 3, 17, 0), date(2026, 10, 3, 17)),
        (date(2026, 10, 3, 23, 30), date(2026, 10, 3, 17)),
        (date(2026, 10, 4, 6, 0), date(2026, 10, 3, 17)),
    ])
    func morningPeriod(now: Date, expectedStart: Date) {
        #expect(morning.periodStart(at: now) == expectedStart)
    }

    @Test("Вечерние: прочитанное до 02:00 ночи относится к вчерашнему вечеру", arguments: [
        (date(2026, 10, 3, 21, 0), date(2026, 10, 3, 2)),
        (date(2026, 10, 4, 1, 59), date(2026, 10, 3, 2)),
        (date(2026, 10, 4, 2, 0), date(2026, 10, 4, 2)),
    ])
    func eveningPeriod(now: Date, expectedStart: Date) {
        #expect(evening.periodStart(at: now) == expectedStart)
    }

    @Test("Следующее обнуление")
    func nextReset() {
        #expect(morning.nextReset(after: date(2026, 10, 3, 10)) == date(2026, 10, 3, 17))
        #expect(morning.nextReset(after: date(2026, 10, 3, 17)) == date(2026, 10, 4, 17))
        #expect(evening.nextReset(after: date(2026, 10, 3, 21)) == date(2026, 10, 4, 2))
    }

    @Test("Ночь перевода часов на летнее время: 02:00 нет — обнуление в 03:00")
    func springForward() {
        // 29 марта 2026, Европа/Берлин: 02:00 → 03:00.
        let reset = evening.nextReset(after: date(2026, 3, 28, 21))
        #expect(berlin.component(.hour, from: reset) == 3)
        #expect(berlin.component(.day, from: reset) == 29)
    }

    @Test("Ближайшее наступление времени: при равенстве расстояний — позднее")
    func nearestBoundary() {
        let five = AzkarResetSchedule(time: DayTime(hour: 5, minute: 0), calendar: berlin)
        #expect(morning.boundary(nearest: date(2026, 10, 3, 17)) == date(2026, 10, 3, 17))
        #expect(morning.boundary(nearest: date(2026, 10, 3, 6)) == date(2026, 10, 3, 17))
        #expect(morning.boundary(nearest: date(2026, 10, 3, 4)) == date(2026, 10, 2, 17))
        #expect(five.boundary(nearest: date(2026, 10, 3, 17)) == date(2026, 10, 4, 5))
    }
}

@Suite("Период чтения раздела")
struct AzkarPeriodTests {
    private static func morning(_ hour: Int, _ minute: Int = 0, in calendar: Calendar = berlin) -> AzkarResetSchedule {
        AzkarResetSchedule(time: DayTime(hour: hour, minute: minute), calendar: calendar)
    }

    @Test("Новый период длится до ближайшего обнуления")
    func startsUntilNextReset() {
        let period = AzkarPeriod(startingAt: date(2026, 10, 3, 8), schedule: Self.morning(17))
        #expect(period.validUntil == date(2026, 10, 3, 17))
        #expect(!period.isExpired(at: date(2026, 10, 3, 16, 59)))
        #expect(period.isExpired(at: date(2026, 10, 3, 17)))
    }

    /// Прочитано в 08:00, время обнуления (было 17:00) меняют в 16:45.
    @Test("Смена времени: ближайшее обнуление не пропускается и не происходит дважды", arguments: [
        // Новое время сегодня уже прошло — обнуление в прежние 17:00, а не завтра в 16:30.
        (DayTime(hour: 16, minute: 30), date(2026, 10, 3, 17)),
        // Ещё впереди — обнуление в новое время.
        (DayTime(hour: 16, minute: 50), date(2026, 10, 3, 16, 50)),
        // Позже прежнего — одно обнуление в 18:00, без промежуточного в 17:00.
        (DayTime(hour: 18, minute: 0), date(2026, 10, 3, 18)),
        // Утреннее время — уже прошло сегодня: обнуление в прежние 17:00.
        (DayTime(hour: 7, minute: 0), date(2026, 10, 3, 17)),
    ])
    func reschedule(newTime: DayTime, expected: Date) {
        var period = AzkarPeriod(startingAt: date(2026, 10, 3, 8), schedule: Self.morning(17))
        period.reschedule(to: Self.morning(newTime.hour, newTime.minute), at: date(2026, 10, 3, 16, 45))
        #expect(period.validUntil == expected)
    }

    @Test("Прокрутка колеса времени: результат не зависит от промежуточных значений")
    func rescheduleIsPathIndependent() {
        let now = date(2026, 10, 3, 16, 45)
        var period = AzkarPeriod(startingAt: date(2026, 10, 3, 8), schedule: Self.morning(17))
        for minute in [55, 50, 46, 40, 30] {
            period.reschedule(to: Self.morning(16, minute), at: now)
        }
        #expect(period.validUntil == date(2026, 10, 3, 17))
        #expect(!period.isExpired(at: now))
    }

    @Test("Москва → Берлин: обнуление в 17:00 по Берлину, прочитанное не теряется")
    func relocateWest() {
        // Прочитано в 08:00 по Москве: граница — 17:00 МСК (16:00 по Берлину).
        var period = AzkarPeriod(startingAt: moscowDate(2026, 10, 3, 8), schedule: Self.morning(17, in: moscow))
        period.relocate(to: Self.morning(17), at: date(2026, 10, 3, 16, 30))
        #expect(period.validUntil == date(2026, 10, 3, 17))
        #expect(period.scheduledReset == date(2026, 10, 3, 17))
        #expect(period.timeZone == "Europe/Berlin")
    }

    @Test("Берлин → Москва: 17:00 по Москве уже прошло — обнуление в прежнюю границу")
    func relocateEast() {
        // Граница — 17:00 по Берлину = 18:00 МСК; в Москве уже 17:30.
        var period = AzkarPeriod(startingAt: date(2026, 10, 3, 8), schedule: Self.morning(17))
        period.relocate(to: Self.morning(17, in: moscow), at: moscowDate(2026, 10, 3, 17, 30))
        #expect(period.validUntil == moscowDate(2026, 10, 3, 18))
    }

    @Test("Тот же часовой пояс — граница не меняется")
    func relocateSameZone() {
        var period = AzkarPeriod(startingAt: date(2026, 10, 3, 8), schedule: Self.morning(17))
        let before = period
        period.relocate(to: Self.morning(18), at: date(2026, 10, 3, 9))
        #expect(period == before)
    }

    @Test("Осенний перевод часов: обнуление в 02:00 — ровно одно")
    func fallBack() {
        // 25 октября 2026, Европа/Берлин: 03:00 летнего → 02:00 зимнего, час 02:00–03:00 повторяется.
        let evening = Self.morning(2)
        let firstTwo = date(2026, 10, 25, 1).addingTimeInterval(60 * 60)
        let fromEvening = AzkarPeriod(startingAt: date(2026, 10, 24, 21), schedule: evening)
        #expect(fromEvening.validUntil == firstTwo)

        // Новый период, начатый в момент обнуления, длится до следующих суток, а не до повтора 02:00.
        let afterReset = AzkarPeriod(startingAt: firstTwo, schedule: evening)
        #expect(afterReset.validUntil == date(2026, 10, 26, 2))
    }
}

@MainActor
@Suite("Время обнуления в настройках")
struct AzkarResetSettingsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("AzkarResetSettingsTests")
    }

    @Test("По умолчанию утренние — 17:00, вечерние — 02:00")
    func defaultTimes() {
        let settings = AzkarResetSettings(defaults: defaults)
        #expect(settings.resetTime(for: .morning) == DayTime(hour: 17, minute: 0))
        #expect(settings.resetTime(for: .evening) == DayTime(hour: 2, minute: 0))
    }

    @Test("Новое время сохраняется между запусками")
    func persists() {
        AzkarResetSettings(defaults: defaults).setResetTime(DayTime(hour: 16, minute: 30), for: .morning)

        #expect(AzkarResetSettings(defaults: defaults).resetTime(for: .morning) == DayTime(hour: 16, minute: 30))
    }
}

@MainActor
@Suite("Сохранение и обнуление прогресса азкаров")
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

    private static let repository = InMemoryContentRepository(azkar: [
        .morning: [1, 2].map {
            Zikr(id: ZikrID(section: .morning, number: $0), arabic: "سُبْحَانَ اللَّهِ",
                 repetitions: 3, audioFileName: "morning_0\($0).mp3", translation: nil)
        },
    ])

    private func makeStore(calendar: Calendar = berlin) async -> AzkarStore {
        let store = AzkarStore(
            repository: Self.repository,
            defaults: defaults,
            now: { [clock] in clock.now },
            calendar: calendar
        )
        await store.load(.morning)
        return store
    }

    @Test("Счёт сохраняется между запусками в пределах периода")
    func restoresWithinPeriod() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()
        store.sessions(in: .morning)[1].increment()
        store.sessions(in: .morning)[1].increment()

        clock.now = date(2026, 10, 3, 16, 59)
        let restored = await makeStore()

        #expect(restored.sessions(in: .morning).map(\.count) == [1, 2])
    }

    @Test("После времени обнуления сохранённый счёт не восстанавливается")
    func discardsAfterReset() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 3, 17, 1)
        let restored = await makeStore()

        #expect(restored.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("Наступил новый период, пока приложение открыто, — счёт и оверлей завершения сбрасываются")
    func refreshResetsOpenSessions() async {
        let store = await makeStore()
        store.sessions(in: .morning).forEach { session in (0..<3).forEach { _ in session.increment() } }
        store.acknowledgeCompletion(of: .morning)
        #expect(store.progress(of: .morning).isFinished)

        clock.now = date(2026, 10, 3, 17, 0)
        store.refreshPeriods()

        #expect(store.progress(of: .morning).completed == 0)
        #expect(store.sessions(in: .morning).allSatisfy { $0.count == 0 })
        let reopened = await makeStore()
        #expect(reopened.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("Ручной сброс из настроек обнуляет раздел и сохраняется")
    func manualReset() async {
        let store = await makeStore()
        store.sessions(in: .morning).forEach { session in (0..<3).forEach { _ in session.increment() } }
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

    @Test("В том же периоде пересчёт ничего не сбрасывает")
    func refreshWithinPeriodKeepsProgress() async {
        let store = await makeStore()
        store.sessions(in: .morning)[0].increment()

        clock.now = date(2026, 10, 3, 12)
        store.refreshPeriods()

        #expect(store.sessions(in: .morning)[0].count == 1)
    }

    @Test("Смена времени на уже прошедшее: прогресс остаётся до прежнего времени обнуления", arguments: [
        // В 08:00 — на 07:00.
        (date(2026, 10, 3, 8), DayTime(hour: 7, minute: 0)),
        // В 16:45 — на 16:30 (раньше обнуление пропускалось до завтрашних 16:30).
        (date(2026, 10, 3, 16, 45), DayTime(hour: 16, minute: 30)),
    ])
    func changingResetTimeKeepsProgressUntilPreviousReset(changedAt: Date, newTime: DayTime) async {
        let settings = AzkarResetSettings(defaults: defaults)
        let store = AzkarStore(
            repository: Self.repository,
            defaults: defaults,
            resetSettings: settings,
            now: { [clock] in clock.now },
            calendar: berlin
        )
        await store.load(.morning)
        store.sessions(in: .morning)[0].increment()

        clock.now = changedAt
        settings.setResetTime(newTime, for: .morning)
        store.refreshPeriods()
        #expect(store.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 3, 17)
        store.refreshPeriods()
        #expect(store.sessions(in: .morning)[0].count == 0)
    }

    @Test("Оверлей завершения после перезапуска не показывается снова")
    func completionAcknowledgementPersists() async {
        let store = await makeStore()
        store.sessions(in: .morning).forEach { session in (0..<3).forEach { _ in session.increment() } }
        store.acknowledgeCompletion(of: .morning)

        let reopened = await makeStore()

        #expect(reopened.progress(of: .morning).isFinished)
        #expect(!reopened.shouldPresentCompletion(of: .morning))
    }

    @Test("Сохранение старого формата (начало периода) читается")
    func readsLegacyFormat() async {
        let periodStart = date(2026, 10, 2, 17).timeIntervalSinceReferenceDate
        let legacy = #"{"periodStart":\#(periodStart),"counts":{"1":2}}"#
        defaults.set(Data(legacy.utf8), forKey: "azkar.progress.morning")

        let store = await makeStore()

        #expect(store.sessions(in: .morning).map(\.count) == [2, 0])
        clock.now = date(2026, 10, 3, 17)
        store.refreshPeriods()
        #expect(store.sessions(in: .morning).map(\.count) == [0, 0])
    }

    @Test("Перелёт Москва → Берлин между запусками: прогресс до 17:00 по Берлину")
    func timeZoneChangeBetweenLaunches() async {
        clock.now = moscowDate(2026, 10, 3, 8)
        let store = await makeStore(calendar: moscow)
        store.sessions(in: .morning)[0].increment()

        // 16:30 по Берлину = 17:30 МСК: московское обнуление прошло, берлинское — ещё нет.
        clock.now = date(2026, 10, 3, 16, 30)
        let restored = await makeStore()
        #expect(restored.sessions(in: .morning)[0].count == 1)

        clock.now = date(2026, 10, 3, 17)
        restored.refreshPeriods()
        #expect(restored.sessions(in: .morning)[0].count == 0)
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
}
