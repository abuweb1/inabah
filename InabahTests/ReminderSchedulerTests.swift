import Foundation
import Testing
import UserNotifications
@testable import Inabah

/// Центр уведомлений в памяти.
private final class FakeNotificationCenter: NotificationCentering {
    var status: ReminderAuthorization
    var requests: [UNNotificationRequest] = []
    /// Стопки, доставленные уведомления которых убирались.
    var clearedThreads: [String] = []

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
    func removeDelivered(thread: String) async { clearedThreads.append(thread) }
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
        hadithOfDay: ReminderText(title: "Прочти хадис · {collection}, № {number}", body: "{text}"),
        readingLesson: ReminderText(title: "Пора на урок", body: "{step}")
    )

    private static let berlin: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
        return calendar
    }()

    /// «Сейчас» служб: 2026-10-12 06:00 по Берлину.
    private static let now = berlin.date(from: DateComponents(year: 2026, month: 10, day: 12, hour: 6))!

    private func scheduler(
        _ center: FakeNotificationCenter,
        settings: ReminderSettings,
        course: ReadingCourse? = nil,
        readingProgress: ReadingProgress? = nil
    ) -> ReminderScheduler {
        let hadith = Hadith(
            id: HadithID(collection: .nawawi, number: 1),
            arabic: "ا",
            translation: HadithTranslation(language: .base, narrator: nil, text: "Дела — по намерениям", source: nil)
        )
        let repository = InMemoryContentRepository(hadiths: [.nawawi: [hadith]], reminders: Self.texts, course: course)
        let now = Self.now
        return ReminderScheduler(
            center: center,
            settings: settings,
            windowSettings: AzkarWindowSettings(defaults: defaults),
            history: AzkarHistory(defaults: defaults),
            hadithStore: HadithStore(repository: repository),
            progress: HadithProgress(defaults: defaults),
            order: HadithCollectionOrder(defaults: defaults),
            readingProgress: readingProgress,
            repository: repository,
            calendar: Self.berlin,
            now: { now }
        )
    }

    // MARK: - «Пора на урок»

    @Test("«Пора на урок»: первый непройденный шаг в тексте, стопка, нажатие - урок шага")
    func readingLessonContent() async throws {
        let course = try await ReadingCourseFixture.load()
        let center = FakeNotificationCenter(status: .allowed)
        let settings = ReminderSettings(defaults: defaults)
        settings.setReading(true, for: .morning)
        let progress = ReadingProgress(defaults: defaults, calendar: Self.berlin, now: { Self.now })

        await scheduler(center, settings: settings, course: course, readingProgress: progress).rescheduleNow()

        let step = try #require(ReadingLogic.currentStep(course: course, completed: []))
        let first = try #require(center.requests.first)
        #expect(center.requests.count == ReminderPlan.horizonDays)
        #expect(first.content.title == "Пора на урок")
        #expect(first.content.body == String(localized: step.displayTitle))
        #expect(first.content.threadIdentifier == ReminderPlan.readingThread)
        #expect(ReminderPayload.target(from: first.content.userInfo) == .readingLesson(step.number))
        #expect(center.clearedThreads.isEmpty, "сегодня шаг не пройден - пришедшие не убираются")
    }

    @Test("Шаг сегодня пройден - сегодняшних нет, пришедшие убираются")
    func readingFinishedToday() async throws {
        let course = try await ReadingCourseFixture.load()
        let center = FakeNotificationCenter(status: .allowed)
        let settings = ReminderSettings(defaults: defaults)
        settings.setReading(true, for: .evening)
        let progress = ReadingProgress(defaults: defaults, calendar: Self.berlin, now: { Self.now })
        progress.markStepFinished()

        await scheduler(center, settings: settings, course: course, readingProgress: progress).rescheduleNow()

        let today = ReadingProgress.dayKey(of: Self.now, in: Self.berlin)
        let days = center.requests.compactMap { ($0.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() }
            .map { ReadingProgress.dayKey(of: $0, in: Self.berlin) }
        #expect(!days.isEmpty)
        #expect(!days.contains(today))
        #expect(center.clearedThreads == [ReminderPlan.readingThread])
    }

    @Test("Без курса - напоминаний чтения нет, остальные ставятся")
    func readingWithoutCourse() async {
        let center = FakeNotificationCenter(status: .allowed)
        let settings = ReminderSettings(defaults: defaults)
        settings.setReading(true, for: .day)
        settings.setHadithOfDay(true)

        await scheduler(center, settings: settings, readingProgress: ReadingProgress(defaults: defaults)).rescheduleNow()

        #expect(!center.requests.isEmpty)
        #expect(!center.requests.contains { $0.content.threadIdentifier == ReminderPlan.readingThread })
    }

    @Test("Напоминание урока отвечает на своё предложение, не на предложение азкаров (как Android)")
    func readingOfferAnswer() throws {
        let azkar = ReminderSettings(defaults: defaults)
        azkar.setHadithOfDay(true)
        #expect(azkar.offerAnswered)
        #expect(!azkar.readingOfferAnswered, "азкары и хадис дня не отвечают на предложение урока")

        let storage = try IsolatedDefaults("ReminderSchedulerTests.readingOffer")
        let reading = ReminderSettings(defaults: storage.defaults)
        reading.setReading(true, for: .evening)
        #expect(reading.readingOfferAnswered)
        #expect(!reading.offerAnswered, "урок не скрывает карточку азкаров")
        #expect(storage.defaults.bool(forKey: "reminders.reading.offer.answered"))
        #expect(ReminderSettings(defaults: storage.defaults).readingOfferAnswered, "после перезапуска")
    }

    @Test("Слоты и время чтения - в ключах как на Android, время пишется только выбранное")
    func readingSettingsKeys() {
        let settings = ReminderSettings(defaults: defaults)
        settings.setReading(true, for: .day)
        settings.setReadingTime(ReadingReminderSlot.day.defaultTime, for: .day)
        #expect(defaults.object(forKey: "reminders.reading.day.time") == nil, "время по умолчанию не записывается")
        settings.setReadingTime(DayTime(hour: 15, minute: 30), for: .day)

        #expect(defaults.bool(forKey: "reminders.reading.day"))
        #expect(defaults.integer(forKey: "reminders.reading.day.time") == 15 * 60 + 30)
        let reloaded = ReminderSettings(defaults: defaults)
        #expect(reloaded.preferences.readingSlots == [.day])
        #expect(reloaded.preferences.readingTime(.day) == DayTime(hour: 15, minute: 30))
        #expect(reloaded.preferences.readingTime(.morning) == DayTime(hour: 9, minute: 0))
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

    @Test("Раздел в текущем окне дочитан — «ещё не прочитаны» на него не ставится")
    func completedWindowSkipsUnfinished() async {
        AzkarHistory(defaults: defaults).record(SectionProgress(completed: 16, total: 16), of: .morning, on: "2026-10-12")
        let center = FakeNotificationCenter(status: .allowed)
        let settings = ReminderSettings(defaults: defaults)
        settings.setStart(true, for: .morning)
        settings.setUnfinished(true)

        await scheduler(center, settings: settings).rescheduleNow()

        let unfinished = center.requests.filter { $0.identifier.contains("morningUnfinished") }
        #expect(unfinished.count == ReminderPlan.horizonDays - 1)
    }

    @Test("Показанный хадис — из прежнего плана по прошедшему времени; новый план сохраняется")
    func shownFromPreviousPlan() async {
        let center = FakeNotificationCenter(status: .allowed)
        let settings = ReminderSettings(defaults: defaults)
        settings.setHadithOfDay(true)
        let shown = HadithID(collection: .qudsi, number: 4)
        settings.setHadithPlan([1_000: shown, 9_999_999_999: HadithID(collection: .qudsi, number: 5)])

        await scheduler(center, settings: settings).rescheduleNow()

        #expect(settings.lastHadith == shown)
        #expect(settings.hadithPlan.count == ReminderPlan.horizonDays)
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
