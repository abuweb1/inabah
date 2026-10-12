import Foundation

/// Изменения одной версии из `data/versions.json` (общий с Android): «Что нового» и «История версий».
nonisolated struct ReleaseNote: Hashable, Sendable {
    let version: AppVersionNumber
    /// День выхода — только для показа («12 октября 2026 г.»): полночь UTC этой даты.
    let date: Date
    /// Пункты на выбранном языке: общие (`items`), затем только для iOS (`ios`).
    let items: [String]
    let language: ContentLanguage
}
