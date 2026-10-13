import Foundation
import Observation
import OSLog
import UIKit
import UserNotifications

/// Разрешены ли уведомления: и доступ дан, и баннеры не выключены в настройках iPhone (аудит Android, К1).
nonisolated enum ReminderAuthorization: Sendable {
    case notDetermined
    case allowed
    case denied
}

/// Центр уведомлений — за протоколом, чтобы планирование проверялось тестами без системы.
protocol NotificationCentering: AnyObject {
    func authorization() async -> ReminderAuthorization
    func requestAuthorization() async -> Bool
    func pendingIdentifiers() async -> [String]
    func removePending(_ identifiers: [String])
    func add(_ request: UNNotificationRequest) async throws
}

final class SystemNotificationCenter: NotificationCentering {
    private var center: UNUserNotificationCenter { .current() }

    func authorization() async -> ReminderAuthorization {
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined:
            return .notDetermined
        case .authorized, .provisional, .ephemeral:
            return settings.alertSetting == .disabled ? .denied : .allowed
        default:
            return .denied
        }
    }

    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
    }

    func pendingIdentifiers() async -> [String] {
        await center.pendingNotificationRequests().map(\.identifier)
    }

    func removePending(_ identifiers: [String]) {
        center.removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    func add(_ request: UNNotificationRequest) async throws {
        try await center.add(request)
    }
}

/// Ставит напоминания в центр уведомлений (docs/07-screen-settings.md «Напоминания»). Приложение не
/// запускается в момент уведомления, поэтому план — на неделю вперёд (`ReminderPlan`) и пересобирается
/// целиком при каждом открытии, уходе в фон и изменении настроек: прежние свои запросы снимаются.
@Observable
final class ReminderScheduler {
    private(set) var authorization: ReminderAuthorization = .notDetermined

    @ObservationIgnored private let center: any NotificationCentering
    @ObservationIgnored private let settings: ReminderSettings
    @ObservationIgnored private let windowSettings: AzkarWindowSettings
    @ObservationIgnored private let history: AzkarHistory
    @ObservationIgnored private let hadithStore: HadithStore
    @ObservationIgnored private let progress: HadithProgress
    @ObservationIgnored private let order: HadithCollectionOrder
    @ObservationIgnored private let repository: any ContentRepository
    @ObservationIgnored private let calendar: Calendar
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private var pending: Task<Void, Never>?

    private let logger = Logger(subsystem: "app.inabah", category: "reminders")

    init(
        center: any NotificationCentering,
        settings: ReminderSettings,
        windowSettings: AzkarWindowSettings,
        history: AzkarHistory,
        hadithStore: HadithStore,
        progress: HadithProgress,
        order: HadithCollectionOrder,
        repository: any ContentRepository,
        calendar: Calendar = .autoupdatingCurrent,
        now: @escaping () -> Date = Date.init
    ) {
        self.center = center
        self.settings = settings
        self.windowSettings = windowSettings
        self.history = history
        self.hadithStore = hadithStore
        self.progress = progress
        self.order = order
        self.repository = repository
        self.calendar = calendar
        self.now = now
    }

    var isAllowed: Bool { authorization == .allowed }

    func refreshAuthorization() async {
        let value = await center.authorization()
        if value != authorization { authorization = value }
    }

    /// Запрос разрешения — при включении напоминания; результат ждётся, а не теряется (аудит Android, К8).
    func requestAuthorization() async -> Bool {
        let granted = await center.requestAuthorization()
        await refreshAuthorization()
        return granted && isAllowed
    }

    /// Пересобрать план. Пересборки идут строго по очереди: предыдущая отменяется и дожидается —
    /// две одновременные не перемешивают запросы и план хадисов (аудит iOS 1.1.1, А1). На время
    /// пересборки — фоновая задача: при уходе в фон система даёт её закончить (А2).
    func reschedule() {
        let previous = pending
        let backgroundTask = UIApplication.shared.beginBackgroundTask(withName: "reminders")
        pending = Task {
            previous?.cancel()
            await previous?.value
            if !Task.isCancelled { await rescheduleNow() }
            UIApplication.shared.endBackgroundTask(backgroundTask)
        }
    }

    /// Сначала ставятся запросы нового плана (идентификаторы постоянные — тот же запрос заменяется),
    /// потом снимаются прежние, которых в плане нет: прерванная пересборка не оставляет неделю без
    /// напоминаний (А2). Отменённая — останавливается, ничего не снимая и не записывая.
    func rescheduleNow() async {
        await refreshAuthorization()
        let existing = await center.pendingIdentifiers().filter { $0.hasPrefix(ReminderPlan.identifierPrefix) }
        // Нет разрешения — ничего не ставится и «показанный» хадис не сдвигается (аудит Android, К1).
        guard isAllowed else {
            center.removePending(existing)
            return
        }
        guard !Task.isCancelled else { return }

        let texts: ReminderTexts
        do {
            guard let loaded = try await repository.reminderTexts() else {
                logger.error("В reminders.json нет текстов на языке интерфейса")
                return
            }
            texts = loaded
        } catch {
            logger.error("Не прочитаны тексты напоминаний: \(String(describing: error), privacy: .public)")
            return
        }

        let moment = now()
        let preferences = settings.preferences
        let windows = Dictionary(uniqueKeysWithValues: AzkarSection.allCases.map { ($0, windowSettings.window(for: $0)) })
        let planned = ReminderPlan.plan(
            preferences: preferences,
            windows: windows,
            now: moment,
            calendar: calendar,
            history: history.days
        ) { section, day in
            ReminderPlan.isCompleted(history.record(of: section, on: day))
        }
        let hadiths = await hadithsOfDay(for: planned.filter { $0.kind == .hadithOfDay }, now: moment)
        guard !Task.isCancelled else { return }

        var added = Set<String>()
        for reminder in planned {
            guard !Task.isCancelled else { return }
            guard let content = content(for: reminder, texts: texts, hadith: hadiths[reminder.at]) else { continue }
            // Компоненты — в календаре и поясе расписания, явно (А9): триггер читает их тем же календарём.
            var components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: reminder.at)
            components.calendar = calendar
            components.timeZone = calendar.timeZone
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            do {
                try await center.add(UNNotificationRequest(identifier: reminder.identifier, content: content, trigger: trigger))
                added.insert(reminder.identifier)
            } catch {
                logger.error("Напоминание не поставлено: \(String(describing: error), privacy: .public)")
            }
        }
        guard !Task.isCancelled else { return }
        center.removePending(existing.filter { !added.contains($0) })
        settings.setHadithPlan(Dictionary(uniqueKeysWithValues: hadiths.map { ($0.key.timeIntervalSince1970, $0.value.id) }))
    }

    /// Хадисы дня по моментам: прежний план показывает, какой уже показан (`last`), новый — выбирается
    /// после загрузки всех сборников (аудит Android, К7); упавший сборник просто не участвует. План
    /// сохраняет вызывающий — только когда запросы поставлены.
    private func hadithsOfDay(for reminders: [PlannedReminder], now moment: Date) async -> [Date: Hadith] {
        let previous = settings.hadithPlan
        if let shown = previous.filter({ $0.key <= moment.timeIntervalSince1970 }).max(by: { $0.key < $1.key }) {
            settings.setLastHadith(shown.value)
        }
        guard !reminders.isEmpty else { return [:] }
        await hadithStore.loadAll()
        let loaded = Dictionary(uniqueKeysWithValues: HadithCollection.allCases.map { ($0, hadithStore.hadiths(in: $0)) })
        let picks = ReminderPlan.pickHadiths(
            count: reminders.count,
            order: order.collections,
            hadiths: loaded,
            isRead: { progress.status(of: $0).isRead },
            last: settings.lastHadith
        )
        var byMoment: [Date: Hadith] = [:]
        for (reminder, hadith) in zip(reminders, picks) { byMoment[reminder.at] = hadith }
        return byMoment
    }

    private func content(for reminder: PlannedReminder, texts: ReminderTexts, hadith: Hadith?) -> UNNotificationContent? {
        let text: ReminderText?
        var userInfo = ReminderPayload.userInfo(kind: reminder.kind)
        switch reminder.kind {
        case .hadithOfDay:
            guard let hadith, let template = texts.hadithOfDay else { return nil }
            text = template.filled([
                .collection: String(localized: hadith.collection.title),
                .number: String(hadith.number),
                .text: hadith.translation?.text ?? "",
            ])
            userInfo = ReminderPayload.userInfo(kind: .hadithOfDay, hadith: hadith.id)
        case .morningStart, .eveningStart:
            text = reminder.kind.section.flatMap { texts.azkarStart[$0] }
        case .morningUnfinished, .eveningUnfinished:
            text = reminder.kind.section.flatMap { texts.azkarUnfinished[$0] }
        case .morningStreak, .eveningStreak:
            guard let streak = reminder.streak else { return nil }
            text = reminder.kind.section
                .flatMap { texts.streakAtRisk[$0] }?
                .filled([.streak: String(localized: AzkarStreak.daysText(streak))])
        }
        // Пустой заголовок и тело после подстановок — уведомления нет (аудит Android, К6).
        guard let text, !(text.title.isEmpty && text.body.isEmpty) else { return nil }
        let content = UNMutableNotificationContent()
        content.title = text.title
        content.body = text.body
        content.sound = .default
        content.userInfo = userInfo
        content.interruptionLevel = .active
        return content
    }
}

/// Нажатие на напоминание открывает раздел азкаров, главную азкаров («серия под угрозой») или хадис;
/// в открытом приложении — баннер.
/// Ставится делегатом центра при запуске — до первого уведомления (аудит Android, К3).
final class ReminderNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    private let router: AppRouter

    init(router: AppRouter) {
        self.router = router
    }

    // Варианты с обработчиком завершения, не `async`: в `async` система получает завершение из
    // фонового потока и тут же обновляет снимок окна — UIKit падал на проверке главного потока
    // (1.1.1 сборка 3: нажатие на любое напоминание закрывало приложение). Центр вызывает делегата
    // на главной очереди — переход и завершение идут там же, синхронно.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let target = ReminderPayload.target(from: response.notification.request.content.userInfo)
        MainActor.assumeIsolated {
            if let target { router.open(target) }
        }
        completionHandler()
    }
}
