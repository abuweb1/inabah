import Foundation
import Testing
import UserNotifications
@testable import Inabah

/// Центр уведомлений в памяти.
private final class FakeNotificationCenter: NotificationCentering {
    var status: ReminderAuthorization
    var requests: [UNNotificationRequest] = []

    init(status: ReminderAuthorization) {
        self.status = status
    }

    func authorization() async -> ReminderAuthorization { status }
    func requestAuthorization() async -> Bool { status == .allowed }
    func pendingIdentifiers() async -> [String] { requests.map(\.identifier) }
    func removePending(_ identifiers: [String]) { requests.removeAll { identifiers.contains($0.identifier) } }
    func add(_ request: UNNotificationRequest) async throws {
        requests.removeAll { $0.identifier == request.identifier }
        requests.append(request)
    }
}

@MainActor
@Suite("Служба напоминаний")
struct ReminderSchedulerTests {
    let storage: IsolatedDefaults
    var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("ReminderSchedulerTests")
    }

    private static let texts = ReminderTexts(
        azkarStart: [.morning: ReminderText(title: "Утро", body: "начало"), .evening: ReminderText(title: "Вечер", body: "начало")],
        azkarUnfinished: [.morning: ReminderText(title: "Ещё не поздно", body: "утро")],
        hadithOfDay: ReminderText(title: "Прочти хадис · {collection}, № {number}", body: "{text}")
    )

    private func scheduler(_ center: FakeNotificationCenter, settings: ReminderSettings) -> ReminderScheduler {
        let hadith = Hadith(
            id: HadithID(collection: .nawawi, number: 1),
            arabic: "ا",
            translation: HadithTranslation(language: .base, narrator: nil, text: "Дела — по намерениям", source: nil)
        )
        let repository = InMemoryContentRepository(hadiths: [.nawawi: [hadith]], reminders: Self.texts)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
        let now = calendar.date(from: DateComponents(year: 2026, month: 10, day: 12, hour: 6))!
        return ReminderScheduler(
            center: center,
            settings: settings,
            windowSettings: AzkarWindowSettings(defaults: defaults),
            history: AzkarHistory(defaults: defaults),
            hadithStore: HadithStore(repository: repository),
            progress: HadithProgress(defaults: defaults),
            order: HadithCollectionOrder(defaults: defaults),
            repository: repository,
            calendar: calendar,
            now: { now }
        )
    }

    @Test("Нет разрешения — ничего не ставится, показанный хадис не сдвигается (К1)")
    func deniedSchedulesNothing() async {
        let center = FakeNotificationCenter(status: .denied)
        let settings = ReminderSettings(defaults: defaults)
        settings.setStart(true, for: .morning)
        settings.setHadithOfDay(true)
        settings.setHadithPlan([0: HadithID(collection: .qudsi, number: 3)])

        await scheduler(center, settings: settings).rescheduleNow()

        #expect(center.requests.isEmpty)
        #expect(settings.lastHadith == nil)
    }

    @Test("Разрешено — тексты с подстановками, хадис дня и переход по нажатию")
    func allowedSchedules() async throws {
        let center = FakeNotificationCenter(status: .allowed)
        let settings = ReminderSettings(defaults: defaults)
        settings.setStart(true, for: .morning)
        settings.setUnfinished(true)
        settings.setHadithOfDay(true)

        await scheduler(center, settings: settings).rescheduleNow()

        #expect(center.requests.count == ReminderPlan.horizonDays * 3)
        let hadith = try #require(center.requests.first { $0.identifier.contains("hadithOfDay") })
        #expect(hadith.content.title == "Прочти хадис · 40 хадисов ан-Навави, № 1")
        #expect(hadith.content.body == "Дела — по намерениям")
        #expect(ReminderPayload.target(from: hadith.content.userInfo) == .hadith(HadithID(collection: .nawawi, number: 1)))
        #expect(center.requests.contains { $0.content.title == "Ещё не поздно" })
    }

    @Test("Повторное планирование снимает прежние запросы; выключили — пусто")
    func rescheduleReplaces() async {
        let center = FakeNotificationCenter(status: .allowed)
        let settings = ReminderSettings(defaults: defaults)
        settings.setStart(true, for: .evening)
        let scheduler = scheduler(center, settings: settings)

        await scheduler.rescheduleNow()
        await scheduler.rescheduleNow()
        #expect(center.requests.count == ReminderPlan.horizonDays)

        settings.setStart(false, for: .evening)
        await scheduler.rescheduleNow()
        #expect(center.requests.isEmpty)
    }

    @Test("Настройки: по умолчанию выключено, 20:00; включение отвечает на предложение; мусор в «показанном» — nil")
    func settingsDefaults() {
        let settings = ReminderSettings(defaults: defaults)
        #expect(settings.preferences == ReminderPreferences())
        #expect(settings.preferences.hadithTime == DayTime(hour: 20, minute: 0))
        #expect(!settings.offerAnswered)

        settings.setHadithOfDay(true)
        #expect(settings.offerAnswered)

        defaults.set("bukhari_x", forKey: "reminders.hadith.last")
        #expect(ReminderSettings(defaults: defaults).lastHadith == nil)
        #expect(ReminderSettings(defaults: defaults).preferences.hadithOfDay)
    }
}
