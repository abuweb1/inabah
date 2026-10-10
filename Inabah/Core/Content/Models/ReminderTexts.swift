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

    private static func fill(_ text: String, _ values: [ReminderPlaceholder: String]) -> String {
        values
            .reduce(text) { result, value in result.replacingOccurrences(of: "{\(value.key.rawValue)}", with: value.value) }
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Подстановки текстов (имена — как в `reminders.json` и `check-data`).
nonisolated enum ReminderPlaceholder: String, Hashable, Sendable {
    case collection
    case number
    case text
}

/// Тексты напоминаний (`data/reminders.json`) на выбранном языке; вида или раздела нет —
/// `nil` у него, и уведомление не показывается.
nonisolated struct ReminderTexts: Hashable, Sendable {
    let azkarStart: [AzkarSection: ReminderText]
    let azkarUnfinished: [AzkarSection: ReminderText]
    let hadithOfDay: ReminderText?
}
