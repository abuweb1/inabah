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

    /// Фон экрана раздела: вечерние — в цветах своей карточки, чтобы разделы различались не только названием.
    func background(in theme: Theme) -> ThemeGradient {
        switch self {
        case .morning: theme.gradients.azkarBackground
        case .evening: theme.gradients.eveningBackground
        }
    }

    /// Цвет навбара и шапки прогресса раздела.
    func headerColor(in theme: Theme) -> Color {
        switch self {
        case .morning: theme.palette.header
        case .evening: theme.palette.eveningHeader
        }
    }

    /// Кольцо прогресса на карточке: утро — солнечное на светлой карточке, вечер — золотое на тёмной.
    func ringStyle(in theme: Theme) -> NavCardRingStyle {
        switch self {
        case .morning:
            NavCardRingStyle(track: theme.palette.track, fill: theme.palette.sunRays, text: theme.palette.onAccent)
        case .evening:
            NavCardRingStyle(track: theme.palette.goldTrack, fill: theme.palette.gold, text: theme.palette.gold)
        }
    }

    func cardShadow(in theme: Theme) -> ShadowToken {
        switch self {
        case .morning: .navCard(theme.palette.accentShadow, strength: .light)
        case .evening: .navCard(theme.palette.shadow, strength: .medium)
        }
    }
}
