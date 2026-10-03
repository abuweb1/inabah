import SwiftUI

/// Тексты и оформление раздела — в одном месте, чтобы новый раздел добавлялся одним `case`.
extension AzkarSection {
    var title: LocalizedStringResource {
        switch self {
        case .morning: "azkar.morning.title"
        case .evening: "azkar.evening.title"
        }
    }

    var subtitle: LocalizedStringResource {
        switch self {
        case .morning: "azkar.morning.subtitle"
        case .evening: "azkar.evening.subtitle"
        }
    }

    /// «16 зикров · После фаджра».
    func cardMeta(count: Int) -> LocalizedStringResource {
        switch self {
        case .morning: "azkar.morning.meta \(count)"
        case .evening: "azkar.evening.meta \(count)"
        }
    }

    var completionMessage: LocalizedStringResource {
        switch self {
        case .morning: "azkar.completion.morning"
        case .evening: "azkar.completion.evening"
        }
    }

    var symbolName: String {
        switch self {
        case .morning: "sun.max.fill"
        case .evening: "moon.stars.fill"
        }
    }

    func cardGradient(in theme: Theme) -> ThemeGradient {
        switch self {
        case .morning: theme.gradients.morningCard
        case .evening: theme.gradients.eveningCard
        }
    }

    func iconColor(in theme: Theme) -> Color {
        switch self {
        case .morning: theme.palette.sunRays
        case .evening: theme.palette.gold
        }
    }

    func cardShadow(in theme: Theme) -> ShadowToken {
        let color = switch self {
        case .morning: theme.palette.accentShadow.opacity(0.35)
        case .evening: theme.palette.shadow.opacity(0.4)
        }
        return ShadowToken(color: color, radius: 12, y: 8)
    }
}
