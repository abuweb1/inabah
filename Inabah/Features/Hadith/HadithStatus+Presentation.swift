import SwiftUI

/// Цвета и значки статуса хадиса — общие для строки списка и кнопок экрана хадиса.
extension HadithStatus {
    /// Основной цвет: полоска строки, цифры бейджа, значок.
    func tint(in theme: Theme) -> Color {
        switch self {
        case .none: theme.palette.gold
        case .read: theme.palette.statusRead
        case .memorized: theme.palette.statusMemorized
        }
    }

    /// Полоска слева в строке: без статуса — приглушённое золото.
    func stripe(in theme: Theme) -> Color {
        self == .none ? theme.palette.goldBorder : tint(in: theme)
    }

    func badgeFill(in theme: Theme) -> Color {
        switch self {
        case .none: theme.palette.goldTint
        case .read: theme.palette.statusReadTint
        case .memorized: theme.palette.statusMemorizedTint
        }
    }

    func badgeBorder(in theme: Theme) -> Color {
        switch self {
        case .none: theme.palette.goldBorder
        case .read: theme.palette.statusReadBorder
        case .memorized: theme.palette.statusMemorizedBorder
        }
    }

    /// Значок статуса (`StatusGlyph`) — единый для карточек главной, строк списка и кнопок
    /// экрана хадиса: открытая книга — прочитан, сердце со звездой — выучен. Без статуса — нет.
    var glyph: StatusGlyph.Kind? {
        switch self {
        case .none: nil
        case .read: .read
        case .memorized: .memorized
        }
    }

    var accessibilityLabel: LocalizedStringResource? {
        switch self {
        case .none: nil
        case .read: "hadith.status.read"
        case .memorized: "hadith.status.memorized"
        }
    }
}
