import Foundation

extension ReadingStep {
    /// «Шаг 4 · Огласовки» или «Итог · Читаем зикр» (Android `stepTitle`): навбар урока, VoiceOver строки.
    var displayTitle: LocalizedStringResource {
        isFinal ? "makharij.final.title \(title)" : "makharij.step.title \(number) \(title)"
    }
}

extension ReadingStepState {
    /// Состояние строки для VoiceOver; у открытого шага - нет.
    var accessibilityValue: LocalizedStringResource? {
        switch self {
        case .done: "makharij.step.state.done"
        case .current: "makharij.step.state.current"
        case .locked: "makharij.step.state.locked"
        case .open: nil
        }
    }
}
