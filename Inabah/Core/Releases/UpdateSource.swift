import SwiftUI

/// Новая версия в App Store — для значка «Обновить» на главной «Азкаров».
nonisolated struct AvailableUpdate: Hashable, Sendable {
    let version: String
}

/// Откуда узнать о новой версии. Заготовка (решение пользователя 2026-10-10): в 1.1.1 приложение
/// в сеть не ходит — `NoUpdateSource`; запрос к App Store (раз в сутки) и правка `PRIVACY.md` — в 1.2.0.
nonisolated protocol UpdateSource: Sendable {
    func availableUpdate() async -> AvailableUpdate?
}

/// Обновлений «нет» — без сети; значок не показывается.
nonisolated struct NoUpdateSource: UpdateSource {
    func availableUpdate() async -> AvailableUpdate? { nil }
}

extension EnvironmentValues {
    /// Источник новой версии для значка «Обновить»; по умолчанию — без сети.
    @Entry var updateSource: any UpdateSource = NoUpdateSource()
}

/// Заданный ответ — превью и тесты значка.
nonisolated struct FixedUpdateSource: UpdateSource {
    let update: AvailableUpdate?

    func availableUpdate() async -> AvailableUpdate? { update }
}
