import Foundation
import Observation

/// Азкары и прогресс их чтения.
///
/// Прогресс относится к отрезку времени (`AzkarPeriod`): времени азкаров из
/// `AzkarWindowSettings` (утренние 5:00–12:00, вечерние 17:00–02:00 по умолчанию) или промежутку
/// до него. На каждой границе счётчики обнуляются — строго, даже посреди чтения. Отметка
/// на главной, оверлей завершения и история (`AzkarHistory`) — только во время азкаров;
/// вне его счёт работает, но никуда не идёт и к началу окна обнуляется.
///
/// Живёт на уровне приложения, поэтому счёт не теряется при переходе между экранами.
@Observable
final class AzkarStore {
    /// Календарь расписания — им же экраны переводят время суток в дату для показа и `DatePicker`.
    @ObservationIgnored let calendar: Calendar
    @ObservationIgnored private let repository: any ContentRepository
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let windowSettings: AzkarWindowSettings
    @ObservationIgnored private let history: AzkarHistory
    @ObservationIgnored private let now: () -> Date
    private(set) var sections: [AzkarSection: Loadable<[ZikrSession]>] = [:]
    /// Выполнение разделов. Хранится, а не вычисляется из сессий: читатели (главная, шапка
    /// списка) зависят только от него и не перерисовываются на каждое нажатие счётчика —
    /// значение меняется, лишь когда зикр выполнен или сброшен.
    private var progressBySection: [AzkarSection: SectionProgress] = [:]
    /// Разделы, у которых сейчас время азкаров. Меняется только на границах.
    private var sectionsInWindow: Set<AzkarSection> = []

    /// Отрезок, к которому относится загруженный прогресс раздела.
    @ObservationIgnored private var periods: [AzkarSection: AzkarPeriod] = [:]
    @ObservationIgnored private var refreshTask: Task<Void, Never>?
    @ObservationIgnored private var timeChangeTasks: [Task<Void, Never>] = []

    /// - Parameters:
    ///   - now, calendar: источник времени — подменяются в тестах.
    init(
        repository: any ContentRepository,
        defaults: UserDefaults = .standard,
        windowSettings: AzkarWindowSettings? = nil,
        history: AzkarHistory? = nil,
        now: @escaping () -> Date = Date.init,
        calendar: Calendar = .autoupdatingCurrent
    ) {
        self.repository = repository
        self.defaults = defaults
        self.windowSettings = windowSettings ?? AzkarWindowSettings(defaults: defaults)
        self.history = history ?? AzkarHistory(defaults: defaults)
        self.now = now
        self.calendar = calendar
        observeSystemTimeChanges()
    }

    deinit {
        refreshTask?.cancel()
        timeChangeTasks.forEach { $0.cancel() }
    }

    func state(of section: AzkarSection) -> Loadable<[ZikrSession]> {
        sections[section] ?? .idle
    }

    func sessions(in section: AzkarSection) -> [ZikrSession] {
        state(of: section).value ?? []
    }

    /// Загружает раздел, если он ещё не загружен и не загружается. Повторный вызов после ошибки — повторная попытка.
    func load(_ section: AzkarSection) async {
        guard !state(of: section).isLoadingOrLoaded else { return }
        sections[section] = .loading
        do {
            let azkar = try await repository.azkar(in: section)
            let restored = restoredProgress(of: section)
            let sessions = azkar.map {
                ZikrSession(zikr: $0, count: restored.counts[$0.id.number] ?? 0) { [weak self] in
                    self?.countDidChange(in: section)
                }
            }
            adopt(restored.period, for: section)
            if restored.completionShown {
                acknowledgedCompletions.insert(section)
            }
            sections[section] = .loaded(sessions)
            updateProgress(of: section)
            if restored.needsSave { save(section) }
            scheduleNextRefresh()
        } catch let error as ContentError {
            sections[section] = .failed(error)
        } catch {
            sections[section] = .failed(.unknown(String(describing: error)))
        }
    }

    func loadAll() async {
        for section in AzkarSection.allCases {
            await load(section)
        }
    }

    func progress(of section: AzkarSection) -> SectionProgress {
        progressBySection[section] ?? SectionProgress(completed: 0, total: 0)
    }

    /// Сейчас время азкаров раздела: прогресс идёт в отметку на главной и в историю.
    func isInWindow(_ section: AzkarSection) -> Bool {
        sectionsInWindow.contains(section)
    }

    private func countDidChange(in section: AzkarSection) {
        updateProgress(of: section)
        save(section)
    }

    /// Пересчёт выполнения раздела; запись — только при изменении (`@Observable` уведомляет
    /// и о записи того же значения). Во время азкаров выполнение уходит в историю.
    private func updateProgress(of section: AzkarSection) {
        let sessions = sessions(in: section)
        let progress = SectionProgress(completed: sessions.count(where: \.isCompleted), total: sessions.count)
        if progressBySection[section] != progress {
            progressBySection[section] = progress
        }
        if let period = periods[section], period.isWindow, progress.total > 0 {
            history.record(progress, of: section, on: period.day)
        }
    }

    /// Есть ли что сбрасывать: начат хотя бы один зикр. Раздел, который ещё не загружен
    /// (или не загрузился), — по сохранённому прогрессу.
    func hasProgress(of section: AzkarSection) -> Bool {
        if periods[section] != nil {
            return sessions(in: section).contains { $0.count > 0 }
        }
        return storedProgress(of: section).map { !$0.counts.isEmpty } ?? false
    }

    /// Ручной сброс раздела из настроек: счётчики — в 0, флаг «مَا شَاءَ اللَّهُ» — сброшен.
    /// Не загруженный ещё раздел — просто забыть сохранённый прогресс.
    func resetProgress(of section: AzkarSection) {
        acknowledgedCompletions.remove(section)
        if periods[section] != nil {
            sessions(in: section).forEach { $0.discardProgress() }
            updateProgress(of: section)
            save(section)
        } else {
            defaults.removeObject(forKey: Self.key(for: section))
        }
    }

    // MARK: - Время азкаров

    /// Сверяет отрезок каждого раздела с расписанием на сейчас. Тот же отрезок (окно или
    /// промежуток с той же датой) — счёт сохраняется, обновляется только граница; другой —
    /// счётчики и флаг оверлея обнуляются.
    ///
    /// Вызывается по таймеру на границе, при возврате приложения на экран, при смене системного
    /// времени или часового пояса и при уходе с экрана настроек времени — так прокрутка колеса
    /// времени не стирает прочитанное, а результат зависит только от итогового значения.
    func reconcile() {
        let current = now()
        for section in AzkarSection.allCases {
            guard let loaded = periods[section] else { continue }
            let expected = schedule(for: section).period(at: current)
            guard expected != loaded else { continue }
            adopt(expected, for: section)
            if !expected.isSameSpan(as: loaded) {
                sessions(in: section).forEach { $0.discardProgress() }
                acknowledgedCompletions.remove(section)
                updateProgress(of: section)
            }
            save(section)
        }
        scheduleNextRefresh()
    }

    private func adopt(_ period: AzkarPeriod, for section: AzkarSection) {
        periods[section] = period
        let isInWindow = period.isWindow
        if sectionsInWindow.contains(section) != isInWindow {
            if isInWindow {
                sectionsInWindow.insert(section)
            } else {
                sectionsInWindow.remove(section)
            }
        }
    }

    private func schedule(for section: AzkarSection) -> AzkarWindowSchedule {
        AzkarWindowSchedule(window: windowSettings.window(for: section), calendar: calendar)
    }

    /// Задача спит до ближайшей границы среди загруженных разделов. Пока приложение
    /// приостановлено, она не срабатывает — тогда отрезок сверяется при возврате на экран.
    private func scheduleNextRefresh() {
        refreshTask?.cancel()
        guard let next = periods.values.map(\.validUntil).min() else { return }
        let delay = max(next.timeIntervalSince(now()), 0)
        refreshTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            self?.reconcile()
        }
    }

    /// Ручная смена времени или часового пояса — отрезок мог смениться или его граница — сместиться.
    /// Переход на летнее время мгновений не меняет: таймер спит до абсолютного момента границы.
    private func observeSystemTimeChanges() {
        for name in [Notification.Name.NSSystemClockDidChange, .NSSystemTimeZoneDidChange] {
            timeChangeTasks.append(Task { [weak self] in
                for await _ in NotificationCenter.default.notifications(named: name).map({ _ in () }) {
                    self?.reconcile()
                }
            })
        }
    }

    // MARK: - Хранение

    /// Прогресс раздела: отрезок, счёт по номерам зикров, показан ли оверлей завершения.
    /// Сохранение версии 1.0.0 (другой формат периода) не декодируется — прогресс начинается заново.
    private nonisolated struct StoredProgress: Codable {
        var period: AzkarPeriod
        var counts: [Int: Int]
        var completionShown: Bool
    }

    /// Что восстановить при загрузке раздела.
    private struct RestoredProgress {
        var period: AzkarPeriod
        var counts: [Int: Int] = [:]
        var completionShown = false
        /// Отрезок новый или его граница сдвинулась — записать.
        var needsSave = true
    }

    private static func key(for section: AzkarSection) -> String {
        "azkar.progress.\(section.rawValue)"
    }

    private func storedProgress(of section: AzkarSection) -> StoredProgress? {
        guard let data = defaults.data(forKey: Self.key(for: section)) else { return nil }
        return try? JSONDecoder().decode(StoredProgress.self, from: data)
    }

    private func restoredProgress(of section: AzkarSection) -> RestoredProgress {
        let expected = schedule(for: section).period(at: now())
        guard let stored = storedProgress(of: section), stored.period.isSameSpan(as: expected) else {
            return RestoredProgress(period: expected)
        }
        return RestoredProgress(
            period: expected,
            counts: stored.counts,
            completionShown: stored.completionShown,
            needsSave: stored.period != expected
        )
    }

    private func save(_ section: AzkarSection) {
        guard let period = periods[section] else { return }
        let counts = Dictionary(
            sessions(in: section).filter { $0.count > 0 }.map { ($0.zikr.id.number, $0.count) },
            uniquingKeysWith: { first, _ in first }
        )
        let stored = StoredProgress(
            period: period,
            counts: counts,
            completionShown: acknowledgedCompletions.contains(section)
        )
        guard let data = try? JSONEncoder().encode(stored) else { return }
        defaults.set(data, forKey: Self.key(for: section))
    }

    // MARK: - Оверлей завершения

    /// Разделы, для которых «مَا شَاءَ اللَّهُ» уже показан в этом окне. Сохраняется вместе
    /// с прогрессом — после перезапуска выполненный раздел не поздравляет снова. Читается только
    /// из задач, не из `body`, — наблюдение не нужно.
    @ObservationIgnored private var acknowledgedCompletions: Set<AzkarSection> = []

    /// Показать оверлей завершения — один раз за окно и только во время азкаров.
    func shouldPresentCompletion(of section: AzkarSection) -> Bool {
        isInWindow(section) && progress(of: section).isFinished && !acknowledgedCompletions.contains(section)
    }

    func acknowledgeCompletion(of section: AzkarSection) {
        guard acknowledgedCompletions.insert(section).inserted else { return }
        save(section)
    }

    /// Раздел снова не выполнен (сбросили зикр) — следующее завершение покажет оверлей снова.
    func resetCompletionAcknowledgement(of section: AzkarSection) {
        guard acknowledgedCompletions.remove(section) != nil else { return }
        save(section)
    }
}

nonisolated struct SectionProgress: Hashable, Sendable {
    let completed: Int
    let total: Int

    var fraction: Double { total == 0 ? 0 : Double(completed) / Double(total) }
    var isFinished: Bool { total > 0 && completed == total }
}
