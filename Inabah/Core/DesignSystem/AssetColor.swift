import SwiftUI

/// Имена цветов из `Assets.xcassets/Palette`.
///
/// Свой `nonisolated` enum вместо сгенерированных `ColorResource`: в модуле с изоляцией
/// `MainActor` по умолчанию сгенерированные символы изолированы на главном акторе,
/// а тема нужна как значение по умолчанию в `EnvironmentValues` (nonisolated).
/// Соответствие имён ассетам проверяет тест `AssetColorTests`.
nonisolated enum AssetColor: String, CaseIterable, Sendable {
    // Surface
    case appBackground, cardBackground, actionBackground, headerBackground, shadow
    // Text
    case textPrimary, textSecondary, textTertiary, onAccent
    // Accent
    case accentPurple, accentPurpleLight, accentPurpleDim, shadowPurple
    // Status
    case success, successLight, successDeep, successDim, statusRead, statusMemorized
    // Gold
    case gold, goldLight, goldDeep, sunRays
    // Parchment
    case parchmentLight, parchmentMid, parchmentDeep, parchmentInk
    // Gradients
    case azkarBackgroundTop, azkarBackgroundMid, azkarBackgroundBottom
    case morningCardStart, morningCardMid, morningCardEnd
    case eveningCardStart, eveningCardMid, eveningCardEnd
    case hadithBackgroundTop, hadithBackgroundMid, hadithBackgroundBottom
}

nonisolated extension Color {
    init(asset: AssetColor) {
        self.init(asset.rawValue, bundle: .main)
    }
}
