import Foundation
import Testing
@testable import Inabah

/// Календарь с фиксированным часовым поясом (с переходом на летнее время) — тесты не зависят от машины.
private let berlin: Calendar = {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
    return calendar
}()

private func date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
    berlin.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
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
}

@MainActor
@Suite("Время обнуления в настройках")
struct AzkarResetSettingsTests {
    private let defaults: UserDefaults

    init() throws {
        defaults = try #require(UserDefaults(suiteName: "AzkarResetSettingsTests.\(UUID().uuidString)"))
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
    private let defaults: UserDefaults
    /// Текущее время — меняется тестом.
    private final class Clock {
        var now: Date
        init(_ now: Date) { self.now = now }
    }
    private let clock = Clock(date(2026, 10, 3, 8))

    init() throws {
        defaults = try #require(UserDefaults(suiteName: "AzkarProgressPersistenceTests.\(UUID().uuidString)"))
    }

    private static let repository = InMemoryContentRepository(azkar: [
        .morning: [1, 2].map {
            Zikr(id: ZikrID(section: .morning, number: $0), arabic: "سُبْحَانَ اللَّهِ",
                 repetitions: 3, audioFileName: "morning_0\($0).mp3", translation: nil)
        },
    ])

    private func makeStore() async -> AzkarStore {
        let store = AzkarStore(
            repository: Self.repository,
            defaults: defaults,
            now: { [clock] in clock.now },
            calendar: berlin
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

    @Test("Смена времени обнуления не стирает текущий прогресс")
    func changingResetTimeKeepsProgress() async {
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

        // В 08:00 время обнуления переставили на 07:00 — граница уже прошла, но прогресс остаётся.
        settings.setResetTime(DayTime(hour: 7, minute: 0), for: .morning)
        store.refreshPeriods()

        #expect(store.sessions(in: .morning)[0].count == 1)
    }
}
