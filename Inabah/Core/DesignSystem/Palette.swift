import SwiftUI

/// Семантические цветовые токены темы.
///
/// Вьюхи обращаются только к `theme.palette.*` — ни HEX, ни имён ассетов, ни `.black`/`.white`,
/// ни «магических» прозрачностей в коде экранов. Значения хранятся в `Assets.xcassets/Palette`;
/// новая тема — новый экземпляр `Palette`.
nonisolated struct Palette: Hashable, Sendable {
    // Поверхности
    var background: Color
    var card: Color
    var actionBackground: Color
    var header: Color
    /// Навбар и шапка вечерних азкаров — тёмный край карточки «Вечерние азкары».
    var eveningHeader: Color
    /// Навбар и шапки раздела «Хадисы».
    var hadithHeader: Color

    // Текст
    var textPrimary: Color
    var textSecondary: Color
    var textTertiary: Color
    /// Текст и иконки поверх цветных и градиентных поверхностей.
    var onAccent: Color
    /// Вторичный текст поверх акцента и градиентов (подписи, подзаголовки).
    var onAccentSecondary: Color
    /// Третичный текст поверх акцента (ссылки, мелкие пометки).
    var onAccentTertiary: Color
    /// Чуть приглушённый основной текст поверх акцента (аят на главных).
    var onAccentStrong: Color

    // Линии и подложки поверх тёмных и градиентных поверхностей
    /// Тонкая рамка карточек и кнопок.
    var hairline: Color
    /// Разделитель внутри карточки.
    var divider: Color
    /// Дорожка полос прогресса.
    var track: Color
    /// Едва заметная подложка и декор (звезда, круги на карточках, плашка аята).
    var subtleFill: Color
    /// Тени.
    var shadow: Color

    // Акцент
    var accent: Color
    var accentLight: Color
    var accentDim: Color
    var accentShadow: Color

    // Статусы
    var success: Color
    var successLight: Color
    var successDeep: Color
    var successDim: Color
    /// Подложка и рамка зелёных плашек на пергаменте (бейдж «✦ N раз»).
    var successTint: Color
    var successBorder: Color
    /// Статусы хадисов «прочитан» / «выучен»: основной цвет, подложка бейджа и кнопки,
    /// плотная подложка активной кнопки, рамка бейджа.
    var statusRead: Color
    var statusReadTint: Color
    var statusReadStrong: Color
    var statusReadBorder: Color
    var statusMemorized: Color
    var statusMemorizedTint: Color
    var statusMemorizedStrong: Color
    var statusMemorizedBorder: Color

    // Золото: кольцо счётчика, кнопка счёта, плеер
    var gold: Color
    var goldLight: Color
    var goldDeep: Color
    /// Золотая рамка (плеер, карточка «Прослушать все»).
    var goldBorder: Color
    /// Дорожка кольца счётчика.
    var goldTrack: Color
    /// Ручка панели плеера.
    var goldMuted: Color
    /// Подложка бейджа номера хадиса без статуса.
    var goldTint: Color
    var sunRays: Color

    // Пергамент под арабским текстом — не зависит от темы интерфейса
    var parchmentLight: Color
    /// Блик в углу пергамента.
    var parchmentGlow: Color
    var parchmentInk: Color
}

nonisolated extension Palette {
    /// Основная тёмная фиолетовая тема (тёмный вариант прототипа).
    static let inabah: Palette = {
        let onAccent = Color(asset: .onAccent)
        let gold = Color(asset: .gold)
        let successDeep = Color(asset: .successDeep)
        let statusRead = Color(asset: .statusRead)
        let statusMemorized = Color(asset: .statusMemorized)
        return Palette(
            background: Color(asset: .appBackground),
            card: Color(asset: .cardBackground),
            actionBackground: Color(asset: .actionBackground),
            header: Color(asset: .headerBackground),
            eveningHeader: Color(asset: .eveningCardStart),
            hadithHeader: Color(asset: .hadithHeaderBackground),
            textPrimary: Color(asset: .textPrimary),
            textSecondary: Color(asset: .textSecondary),
            textTertiary: Color(asset: .textTertiary),
            onAccent: onAccent,
            onAccentSecondary: onAccent.opacity(0.6),
            onAccentTertiary: onAccent.opacity(0.4),
            onAccentStrong: onAccent.opacity(0.85),
            hairline: onAccent.opacity(0.1),
            divider: onAccent.opacity(0.08),
            track: onAccent.opacity(0.15),
            subtleFill: onAccent.opacity(0.07),
            shadow: Color(asset: .shadow),
            accent: Color(asset: .accentPurple),
            accentLight: Color(asset: .accentPurpleLight),
            accentDim: Color(asset: .accentPurpleDim),
            accentShadow: Color(asset: .shadowPurple),
            success: Color(asset: .success),
            successLight: Color(asset: .successLight),
            successDeep: successDeep,
            successDim: Color(asset: .successDim),
            successTint: successDeep.opacity(0.1),
            successBorder: successDeep.opacity(0.25),
            statusRead: statusRead,
            statusReadTint: statusRead.opacity(0.13),
            statusReadStrong: statusRead.opacity(0.32),
            statusReadBorder: statusRead.opacity(0.3),
            statusMemorized: statusMemorized,
            statusMemorizedTint: statusMemorized.opacity(0.12),
            statusMemorizedStrong: statusMemorized.opacity(0.3),
            statusMemorizedBorder: statusMemorized.opacity(0.3),
            gold: gold,
            goldLight: Color(asset: .goldLight),
            goldDeep: Color(asset: .goldDeep),
            goldBorder: gold.opacity(0.25),
            goldTrack: gold.opacity(0.12),
            goldMuted: gold.opacity(0.45),
            goldTint: gold.opacity(0.13),
            sunRays: Color(asset: .sunRays),
            parchmentLight: Color(asset: .parchmentLight),
            parchmentGlow: Color(asset: .parchmentLight).opacity(0.7),
            parchmentInk: Color(asset: .parchmentInk)
        )
    }()
}

/// Тень как токен темы — один стиль для однотипных элементов.
nonisolated struct ShadowToken: Hashable, Sendable {
    var color: Color
    var radius: CGFloat
    var y: CGFloat
}

nonisolated extension ShadowToken {
    /// Карточки ленты.
    static func card(_ palette: Palette) -> ShadowToken { ShadowToken(color: palette.shadow.opacity(0.35), radius: 6, y: 2) }
    /// Плавающая панель (мини-плеер).
    static func floating(_ palette: Palette) -> ShadowToken { ShadowToken(color: palette.shadow.opacity(0.45), radius: 18, y: 6) }
    /// Золотые кнопки (счётчик, плей).
    static func goldButton(_ palette: Palette) -> ShadowToken { ShadowToken(color: palette.goldDeep.opacity(0.4), radius: 10, y: 4) }

    /// Плотность тени навигационной карточки — подбирается под цвет карточки.
    enum NavCardStrength {
        case light, medium, strong

        var opacity: Double {
            switch self {
            case .light: 0.35
            case .medium: 0.4
            case .strong: 0.45
            }
        }
    }

    /// Навигационные карточки главных экранов: цветная тень снизу.
    static func navCard(_ color: Color, strength: NavCardStrength) -> ShadowToken {
        ShadowToken(color: color.opacity(strength.opacity), radius: 12, y: 8)
    }
}
