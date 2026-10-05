import Foundation
import Observation

/// Азкары и прогресс их чтения в текущем периоде.
///
/// Прогресс сохраняется между запусками и обнуляется каждый день во время из
/// `AzkarResetSettings` (утренние — 17:00, вечерние — 02:00 по умолчанию). Период раздела
/// (`AzkarPeriod`) действует до ближайшего обнуления; сохранённый счёт истёкшего периода
/// не восстанавливается.
///
/// Живёт на уровне приложения, поэтому счёт не теряется при переходе между экранами.
@Observable
final class AzkarStore {
    /// Календарь расписания — им же экран настроек переводит время в дату для `DatePicker`.
    @ObservationIgnored let calendar: Calendar
    @ObservationIgnored private let repository: any ContentRepository
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let resetSettings: AzkarResetSettings
    @ObservationIgnored private let now: () -> Date
    private(set) var sections: [AzkarSection: Loadable<[ZikrSession]>] = [:]
    /// Выполнение разделов. Хранится, а не вычисляется из сессий: читатели (главная, шапка
    /// списка) зависят только от него и не перерисовываются на каждое нажатие счётчика —
    /// значение меняется, лишь когда зикр выполнен или сброшен.
    private var progressBySection: [AzkarSection: SectionProgress] = [:]

    /// Период, к которому относится загруженный прогресс раздела.
    @ObservationIgnored private var periods: [AzkarSection: AzkarPeriod] = [:]
    @ObservationIgnored private var refreshTask: Task<Void, Never>?
    @ObservationIgnored private var timeChangeTasks: [Task<Void, Never>] = []

    /// - Parameters:
    ///   - now, calendar: источник времени — подменяются в тестах.
    init(
        repository: any ContentRepository,
        defaults: UserDefaults = .standard,
        resetSettings: AzkarResetSettings? = nil,
        now: @escaping () -> Date = Date.init,
        calendar: Calendar = .autoupdatingCurrent
    ) {
        self.repository = repository
        self.defaults = defaults
        self.resetSettings = resetSettings ?? AzkarResetSettings(defaults: defaults)
        self.now = now
        self.calendar = calendar
        self.resetSettings.onResetTimeChange { [weak self] in self?.adoptNewResetTimes() }
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
            periods[section] = restored.period
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

    private func countDidChange(in section: AzkarSection) {
        updateProgress(of: section)
        save(section)
    }

    /// Пересчёт выполнения раздела; запись — только при изменении (`@Observable` уведомляет
    /// и о записи того же значения).
    private func updateProgress(of section: AzkarSection) {
        let sessions = sessions(in: section)
        let progress = SectionProgress(completed: sessions.count(where: \.isCompleted), total: sessions.count)
        if progressBySection[section] != progress {
            progressBySection[section] = progress
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

    // MARK: - Обнуление по времени

    /// Время обнуления изменили в настройках: текущий прогресс сохраняется, граница периода
    /// переносится по правилам `AzkarPeriod.reschedule` — ближайшее обнуление не пропускается,
    /// а прокрутка колеса времени через «сейчас» не стирает прочитанное.
    private func adoptNewResetTimes() {
        let current = now()
        for section in AzkarSection.allCases {
            guard var period = periods[section] else { continue }
            period.reschedule(to: schedule(for: section), at: current)
            guard period != periods[section] else { continue }
            periods[section] = period
            save(section)
        }
        scheduleNextRefresh()
    }

    /// Обнуляет разделы, у которых истёк период, и переносит границы при смене часового пояса.
    /// Вызывается по таймеру, при возврате приложения на экран, при смене системного времени
    /// или часового пояса.
    func refreshPeriods() {
        let current = now()
        for section in AzkarSection.allCases {
            guard let loaded = periods[section] else { continue }
            let schedule = schedule(for: section)
            var period = loaded
            period.relocate(to: schedule, at: current)
            if period.isExpired(at: current) {
                period = AzkarPeriod(startingAt: current, schedule: schedule)
                sessions(in: section).forEach { $0.discardProgress() }
                updateProgress(of: section)
                acknowledgedCompletions.remove(section)
            }
            guard period != loaded else { continue }
            periods[section] = period
            save(section)
        }
        scheduleNextRefresh()
    }

    private func schedule(for section: AzkarSection) -> AzkarResetSchedule {
        AzkarResetSchedule(time: resetSettings.resetTime(for: section), calendar: calendar)
    }

    /// Задача спит до ближайшего обнуления среди загруженных разделов. Пока приложение
    /// приостановлено, она не срабатывает — тогда период пересчитывается при возврате на экран.
    private func scheduleNextRefresh() {
        refreshTask?.cancel()
        guard let next = periods.values.map(\.validUntil).min() else { return }
        let delay = max(next.timeIntervalSince(now()), 0)
        refreshTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            self?.refreshPeriods()
        }
    }

    /// Ручная смена времени или часового пояса — период мог истечь или его граница — сместиться.
    /// Переход на летнее время мгновений не меняет: таймер спит до абсолютного момента обнуления.
    private func observeSystemTimeChanges() {
        for name in [Notification.Name.NSSystemClockDidChange, .NSSystemTimeZoneDidChange] {
            timeChangeTasks.append(Task { [weak self] in
                for await _ in NotificationCenter.default.notifications(named: name).map({ _ in () }) {
                    self?.refreshPeriods()
                }
            })
        }
    }

    // MARK: - Хранение

    /// Прогресс раздела: период, счёт по номерам зикров, показан ли оверлей завершения.
    private nonisolated struct StoredProgress: Codable {
        var period: AzkarPeriod?
        /// Формат до 2026-10-03: вместо периода — его начало.
        var periodStart: Date?
        var counts: [Int: Int]
        var completionShown: Bool?
    }

    /// Что восстановить при загрузке раздела.
    private struct RestoredProgress {
        var period: AzkarPeriod
        var counts: [Int: Int] = [:]
        var completionShown = false
        /// Период новый или изменён (истёк, старый формат, другой часовой пояс) — записать.
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
        let current = now()
        let schedule = schedule(for: section)
        let fresh = RestoredProgress(period: AzkarPeriod(startingAt: current, schedule: schedule))
        guard let stored = storedProgress(of: section),
              let storedPeriod = stored.period
                ?? stored.periodStart.map({ AzkarPeriod(legacyPeriodStart: $0, schedule: schedule) })
        else { return fresh }
        var period = storedPeriod
        period.relocate(to: schedule, at: current)
        guard !period.isExpired(at: current) else { return fresh }
        return RestoredProgress(
            period: period,
            counts: stored.counts,
            completionShown: stored.completionShown ?? false,
            needsSave: period != stored.period
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

    /// Разделы, для которых «مَا شَاءَ اللَّهُ» уже показан в этом периоде. Сохраняется вместе
    /// с прогрессом — после перезапуска выполненный раздел не поздравляет снова. Читается только
    /// из задач, не из `body`, — наблюдение не нужно.
    @ObservationIgnored private var acknowledgedCompletions: Set<AzkarSection> = []

    /// Показать оверлей завершения — один раз за прохождение раздела, а не при каждом открытии.
    func shouldPresentCompletion(of section: AzkarSection) -> Bool {
        progress(of: section).isFinished && !acknowledgedCompletions.contains(section)
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
