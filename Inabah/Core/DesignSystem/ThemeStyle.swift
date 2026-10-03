import SwiftUI

/// Палитра оформления: «по умолчанию» (свой цвет у каждого раздела) или единый стиль
/// в цвет одного из разделов на всё приложение.
nonisolated enum ThemeStyle: String, CaseIterable, Identifiable, Sendable {
    /// Как задумано: азкары фиолетовые, хадисы изумрудные, «Махрадж» янтарный, настройки — графит.
    case sections
    /// Всё приложение в цвет азкаров.
    case violet
    /// В цвет хадисов.
    case emerald
    /// В цвет «Махраджа».
    case amber
    /// В цвет настроек.
    case graphite

    var id: Self { self }

    /// Тема стиля. Темы собираются один раз.
    var theme: Theme {
        switch self {
        case .sections: Theme.inabah
        case .violet: Self.violetTheme
        case .emerald: Self.emeraldTheme
        case .amber: Self.amberTheme
        case .graphite: Self.graphiteTheme
        }
    }

    private static let violetTheme = Theme.unified(.violet)
    private static let emeraldTheme = Theme.unified(.emerald)
    private static let amberTheme = Theme.unified(.amber)
    private static let graphiteTheme = Theme.unified(.graphite)
}

/// Цвета единого стиля. Ассеты — `Palette/Themes/<Стиль>/<стиль><Токен>`, их генерирует
/// `scripts/generate-theme-palettes.py` (наличие каждого проверяет `ThemeStyleTests`).
nonisolated enum ThemeColorToken: String, CaseIterable, Sendable {
    case backgroundTop, backgroundMid, backgroundBottom
    case header, eveningHeader
    case surface, card, action
    case accent, accentLight, accentDim, accentShadow
    case textSecondary, textTertiary
    case tab
    case cardShadow
    case morningCardStart, morningCardMid, morningCardEnd
    case eveningCardStart, eveningCardMid, eveningCardEnd
    case nawawiCardStart, nawawiCardMid, nawawiCardEnd
    case qudsiCardStart, qudsiCardMid, qudsiCardEnd
    case ajurriCardStart, ajurriCardMid, ajurriCardEnd

    /// Имя ассета цвета для стиля: `emerald` + `card` → `emeraldCard`.
    func assetName(for style: ThemeStyle) -> String {
        style.rawValue + rawValue.prefix(1).uppercased() + rawValue.dropFirst()
    }
}

nonisolated extension Theme {
    /// Единый стиль: основа — тема по умолчанию; фоны, шапки, поверхности, акцент, вторичный
    /// текст, вкладки и карточки — в цвет стиля. Смысловые цвета контента (золото, пергамент,
    /// статусы хадисов) не меняются.
    static func unified(_ style: ThemeStyle) -> Theme {
        func color(_ token: ThemeColorToken) -> Color {
            Color(token.assetName(for: style), bundle: .main)
        }
        /// Цвета стиля в форме исходного градиента: те же точки (у утренних и ан-Навави
        /// середина на 0.6, у остальных — 0.55) и направление.
        func gradient(_ stops: [ThemeColorToken], like base: ThemeGradient) -> ThemeGradient {
            var result = base
            result.gradient = Gradient(stops: zip(stops, base.gradient.stops).map {
                Gradient.Stop(color: color($0), location: $1.location)
            })
            return result
        }

        var palette = Palette.inabah
        palette.background = color(.surface)
        palette.card = color(.card)
        palette.actionBackground = color(.action)
        palette.header = color(.header)
        palette.eveningHeader = color(.eveningHeader)
        palette.hadithHeader = color(.header)
        palette.textSecondary = color(.textSecondary)
        palette.textTertiary = color(.textTertiary)
        palette.accent = color(.accent)
        palette.accentLight = color(.accentLight)
        palette.accentDim = color(.accentDim)
        palette.accentShadow = color(.accentShadow)
        palette.morningCardShadow = color(.cardShadow)
        palette.nawawiCardShadow = color(.cardShadow)
        let tab = color(.tab)
        palette.tabAzkar = tab
        palette.tabHadith = tab
        palette.tabMakharij = tab
        palette.tabSettings = tab

        var gradients = ThemeGradients.inabah
        let background = ThemeGradient(
            [
                Gradient.Stop(color: color(.backgroundTop), location: 0),
                Gradient.Stop(color: color(.backgroundMid), location: 0.5),
                Gradient.Stop(color: color(.backgroundBottom), location: 1),
            ],
            cssAngle: ThemeGradients.backgroundAngle
        )
        gradients.azkarBackground = background
        gradients.eveningBackground = background
        gradients.hadithBackground = background
        gradients.makharijBackground = background
        gradients.settingsBackground = background
        gradients.morningCard = gradient([.morningCardStart, .morningCardMid, .morningCardEnd], like: gradients.morningCard)
        gradients.eveningCard = gradient([.eveningCardStart, .eveningCardMid, .eveningCardEnd], like: gradients.eveningCard)
        gradients.nawawiCard = gradient([.nawawiCardStart, .nawawiCardMid, .nawawiCardEnd], like: gradients.nawawiCard)
        gradients.qudsiCard = gradient([.qudsiCardStart, .qudsiCardMid, .qudsiCardEnd], like: gradients.qudsiCard)
        gradients.ajurriCard = gradient([.ajurriCardStart, .ajurriCardMid, .ajurriCardEnd], like: gradients.ajurriCard)

        return Theme(palette: palette, gradients: gradients)
    }
}
