import Foundation
import Observation
import OSLog

/// Что сделать при запуске с окном «Что нового» — чистое правило, как на Android (`WhatsNew.kt`, 3.9).
nonisolated enum WhatsNewDecision: Equatable, Sendable {
    case show
    /// Окно не нужно — версию записать как показанную.
    case recordSilently
    case nothing

    /// Порядок правил важен: (1) новая установка без ключа — тихо; (2) версия не выросла (та же или
    /// откат) — ничего; (3) есть пункты этой версии — показать; (4) иначе — тихо.
    static func decide(
        lastShown: AppVersionNumber?,
        current: AppVersionNumber,
        isFreshInstall: Bool,
        hasNotes: Bool
    ) -> WhatsNewDecision {
        if lastShown == nil, isFreshInstall { return .recordSilently }
        if let lastShown, current <= lastShown { return .nothing }
        return hasNotes ? .show : .recordSilently
    }
}

/// Окно «Что нового» один раз после обновления (решение пользователя 2026-10-10, Android — первым).
///
/// Показанная версия — `app.whatsNew.lastVersion` (ключ Android). Пишется при закрытии окна или
/// тихо — при новой установке и для версии без пунктов; закрыть не успели — окно будет и в
/// следующий раз. «Новая установка» узнаётся в `init`, до того как сторы что-то запишут.
@Observable
final class WhatsNew {
    static let lastVersionKey = "app.whatsNew.lastVersion"
    /// 1.1.0 записывает прогресс азкаров при первом же открытии — по нему видно, что приложение
    /// уже открывали до этой версии (там ключа «Что нового» ещё не было).
    static let existingUserKeys = ["azkar.progress.morning", "azkar.progress.evening"]

    /// Изменения текущей версии, пока окно на экране; `nil` — окна нет.
    private(set) var pending: ReleaseNote?
    /// Проверка закончилась: `pending == nil` до неё не значит «окна не будет» — подсказки ждут этого.
    private(set) var isChecked = false

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let repository: any ContentRepository
    @ObservationIgnored private let current: AppVersionNumber?
    @ObservationIgnored private let isFreshInstall: Bool
    @ObservationIgnored private var checked = false

    private static let logger = Logger(subsystem: "app.inabah", category: "whats-new")

    init(defaults: UserDefaults, repository: any ContentRepository, currentVersion: String = AppVersion.marketing) {
        self.defaults = defaults
        self.repository = repository
        current = AppVersionNumber(currentVersion)
        isFreshInstall = defaults.object(forKey: Self.lastVersionKey) == nil
            && Self.existingUserKeys.allSatisfy { defaults.object(forKey: $0) == nil }
    }

    /// Один раз за запуск. Версия приложения не `X.Y.Z` (отладочная) — ни окна, ни записи.
    func check() async {
        guard !checked else { return }
        checked = true
        defer { isChecked = true }
        guard let current else { return }
        let lastShown = AppVersionNumber(defaults.string(forKey: Self.lastVersionKey))
        let note: ReleaseNote?
        do {
            note = try await repository.releaseNotes().first { $0.version == current }
        } catch {
            Self.logger.error("История версий не загрузилась: \(String(describing: error), privacy: .public)")
            note = nil
        }
        switch WhatsNewDecision.decide(lastShown: lastShown, current: current, isFreshInstall: isFreshInstall, hasNotes: note != nil) {
        case .show: pending = note
        case .recordSilently: record(current)
        case .nothing: break
        }
    }

    /// Все версии для «Истории версий», новые сверху; файл не загрузился — пусто (экран покажет
    /// «Нет истории версий»).
    func history() async -> [ReleaseNote] {
        do {
            return try await repository.releaseNotes()
        } catch {
            Self.logger.error("История версий не загрузилась: \(String(describing: error), privacy: .public)")
            return []
        }
    }

    /// «ОК», нажатие мимо окна — окно закрывается, версия считается показанной.
    func dismiss() {
        guard let note = pending else { return }
        pending = nil
        record(note.version)
    }

    private func record(_ version: AppVersionNumber) {
        defaults.set(version.text, forKey: Self.lastVersionKey)
    }
}
