import Foundation

/// Заголовок и текст уведомления; у хадиса дня — подстановки `{collection}`, `{number}`, `{text}`.
nonisolated struct ReminderText: Hashable, Sendable {
    let title: String
    let body: String

    /// Заменить известные подстановки и обрезать пробелы; подстановки нет в тексте — ничего
    /// (пользователь мог её убрать).
    func filled(_ values: [ReminderPlaceholder: String]) -> ReminderText {
        ReminderText(title: Self.fill(title, values), body: Self.fill(body, values))
    }

    /// Подстановки — в постоянном порядке, `{text}` последней: текст хадиса может содержать фигурные
    /// скобки, и внутри него ничего не заменяется (аудит iOS 1.1.1, А8).
    private static func fill(_ text: String, _ values: [ReminderPlaceholder: String]) -> String {
        ReminderPlaceholder.allCases
            .reduce(text) { result, placeholder in
                guard let value = values[placeholder] else { return result }
                return result.replacingOccurrences(of: "{\(placeholder.rawValue)}", with: value)
            }
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Подстановки текстов (имена — как в `reminders.json` и `check-data`); порядок объявления — порядок замены.
nonisolated enum ReminderPlaceholder: String, CaseIterable, Hashable, Sendable {
    case collection
    case number
    /// Серия «12 дней подряд» — в «серия под угрозой».
    case streak
    case text
}

/// Тексты напоминаний (`data/reminders.json`) на выбранном языке; вида или раздела нет —
/// `nil` у него, и уведомление не показывается.
nonisolated struct ReminderTexts: Hashable, Sendable {
    let azkarStart: [AzkarSection: ReminderText]
    let azkarUnfinished: [AzkarSection: ReminderText]
    let hadithOfDay: ReminderText?
    /// «Серия под угрозой» (2026-10-11), подстановка `{streak}`.
    var streakAtRisk: [AzkarSection: ReminderText] = [:]
}
