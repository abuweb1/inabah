import Foundation
import Observation

/// Азкары и прогресс их чтения в текущем периоде.
///
/// Прогресс сохраняется между запусками и обнуляется каждый день во время из
/// `AzkarResetSettings` (утренние — 17:00, вечерние — 02:00 по умолчанию). Период раздела —
/// от последнего обнуления до следующего (`AzkarResetSchedule`); сохранённый счёт другого
/// периода не восстанавливается.
///
/// Живёт на уровне приложения, поэтому счёт не теряется при переходе между экранами.
@Observable
final class AzkarStore {
    @ObservationIgnored private let repository: any ContentRepository
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let resetSettings: AzkarResetSettings
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private let calendar: Calendar
    private(set) var sections: [AzkarSection: Loadable<[ZikrSession]>] = [:]

    /// Начало периода, к которому относится загруженный прогресс раздела.
    @ObservationIgnored private var periods: [AzkarSection: Date] = [:]
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
        self.resetSettings.onChange = { [weak self] in self?.adoptNewResetTimes() }
        observeSystemTimeChanges()
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
            let period = periodStart(of: section)
            let counts = storedCounts(of: section, period: period)
            let sessions = azkar.map { ZikrSession(zikr: $0, count: counts[$0.id.number] ?? 0) }
            for session in sessions {
                session.onCountChange = { [weak self] in self?.save(section) }
            }
            periods[section] = period
            sections[section] = .loaded(sessions)
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
        let sessions = sessions(in: section)
        return SectionProgress(completed: sessions.count(where: \.isCompleted), total: sessions.count)
    }

    /// Ручной сброс раздела из настроек: счётчики — в 0, флаг «مَا شَاءَ اللَّهُ» — сброшен.
    /// Не загруженный ещё раздел — просто забыть сохранённый прогресс.
    func resetProgress(of section: AzkarSection) {
        if periods[section] != nil {
            sessions(in: section).forEach { $0.reset() }
            save(section)
        } else {
            defaults.removeObject(forKey: Self.key(for: section))
        }
        resetCompletionAcknowledgement(of: section)
    }

    // MARK: - Обнуление по времени

    /// Время обнуления изменили в настройках: текущий прогресс сохраняется и относится к периоду
    /// по новому времени — новое время действует со следующего обнуления. Иначе прокрутка
    /// колеса времени через «сейчас» стирала бы прочитанное.
    private func adoptNewResetTimes() {
        for section in AzkarSection.allCases where periods[section] != nil {
            periods[section] = periodStart(of: section)
            save(section)
        }
        scheduleNextRefresh()
    }

    /// Обнуляет разделы, у которых начался новый период. Вызывается по таймеру, при возврате
    /// приложения на экран, при смене системного времени или часового пояса.
    func refreshPeriods() {
        for section in AzkarSection.allCases {
            guard let loadedPeriod = periods[section] else { continue }
            let period = periodStart(of: section)
            guard period != loadedPeriod else { continue }
            periods[section] = period
            sessions(in: section).forEach { $0.reset() }
            save(section)
            resetCompletionAcknowledgement(of: section)
        }
        scheduleNextRefresh()
    }

    private func schedule(for section: AzkarSection) -> AzkarResetSchedule {
        AzkarResetSchedule(time: resetSettings.resetTime(for: section), calendar: calendar)
    }

    private func periodStart(of section: AzkarSection) -> Date {
        schedule(for: section).periodStart(at: now())
    }

    /// Задача спит до ближайшего обнуления среди загруженных разделов. Пока приложение
    /// приостановлено, она не срабатывает — тогда период пересчитывается при возврате на экран.
    private func scheduleNextRefresh() {
        refreshTask?.cancel()
        let current = now()
        guard let next = periods.keys.map({ schedule(for: $0).nextReset(after: current) }).min() else { return }
        let delay = max(next.timeIntervalSince(current), 0)
        refreshTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            self?.refreshPeriods()
        }
    }

    /// Перевод часов, ручная смена времени или часового пояса — период мог смениться.
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

    /// Прогресс раздела: начало периода и счёт по номерам зикров.
    private nonisolated struct StoredProgress: Codable {
        let periodStart: Date
        let counts: [Int: Int]
    }

    private static func key(for section: AzkarSection) -> String {
        "azkar.progress.\(section.rawValue)"
    }

    private func storedCounts(of section: AzkarSection, period: Date) -> [Int: Int] {
        guard let data = defaults.data(forKey: Self.key(for: section)),
              let stored = try? JSONDecoder().decode(StoredProgress.self, from: data),
              stored.periodStart == period else { return [:] }
        return stored.counts
    }

    private func save(_ section: AzkarSection) {
        guard let period = periods[section] else { return }
        let counts = Dictionary(
            sessions(in: section).filter { $0.count > 0 }.map { ($0.zikr.id.number, $0.count) },
            uniquingKeysWith: { first, _ in first }
        )
        guard let data = try? JSONEncoder().encode(StoredProgress(periodStart: period, counts: counts)) else { return }
        defaults.set(data, forKey: Self.key(for: section))
    }

    // MARK: - Оверлей завершения

    /// Разделы, для которых «Машаа Аллах!» уже показан за это прохождение. Читается только
    /// из задач, не из `body`, — наблюдение не нужно.
    @ObservationIgnored private var acknowledgedCompletions: Set<AzkarSection> = []

    /// Показать оверлей завершения — один раз за прохождение раздела, а не при каждом открытии.
    func shouldPresentCompletion(of section: AzkarSection) -> Bool {
        progress(of: section).isFinished && !acknowledgedCompletions.contains(section)
    }

    func acknowledgeCompletion(of section: AzkarSection) {
        acknowledgedCompletions.insert(section)
    }

    /// Раздел снова не выполнен (сбросили зикр) — следующее завершение покажет оверлей снова.
    func resetCompletionAcknowledgement(of section: AzkarSection) {
        acknowledgedCompletions.remove(section)
    }
}

nonisolated struct SectionProgress: Hashable, Sendable {
    let completed: Int
    let total: Int

    var fraction: Double { total == 0 ? 0 : Double(completed) / Double(total) }
    var isFinished: Bool { total > 0 && completed == total }
}
