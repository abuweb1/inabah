import SwiftUI

/// Фон под арабским текстом — «пергамент» азкаров и хадисов (Настройки → Палитра).
///
/// Решение пользователя 2026-10-06: готовый набор (не произвольный цвет — читаемость и красота
/// градиента), один на оба раздела, цвета — как на Android (`ParchmentStyle.kt`). Не зависит от
/// палитры: подходит к любой. Ассеты — `Palette/Parchment/<Вариант>`, их пишет
/// `scripts/generate-parchment-palettes.py`; «Пергамент» — нынешние ассеты, вид по умолчанию прежний.
nonisolated enum ParchmentStyle: String, CaseIterable, Identifiable, Sendable {
    /// Золотой пергамент — как было.
    case classic
    /// Тёплая старая бумага — к «Янтарной».
    case sepia
    /// Приглушённый лиловый с золотой рамкой — к «Фиолетовой».
    case amethyst
    /// Глубокий зелёный с золотой рамкой — к «Изумрудной».
    case jade
    /// Медово-коричневый с золотой рамкой — к «Янтарной».
    case amber
    /// Дымчатый серо-голубой — к «Графиту».
    case smoky
    /// Тёмный — к любой палитре, для чтения в темноте без яркого пятна.
    case night

    var id: Self { self }

    /// Имя ассета цвета: у «Пергамента» — нынешние ассеты, у остальных — `parchment<Вариант><Токен>`.
    func assetName(_ token: ParchmentToken) -> String {
        guard self != .classic else { return token.classicAssetName }
        return "parchment" + rawValue.capitalizedFirst + token.rawValue.capitalizedFirst
    }

    var colors: ParchmentColors {
        func color(_ token: ParchmentToken) -> Color { Color(assetName(token), bundle: .main) }
        return ParchmentColors(
            light: color(.light),
            highlight: color(.highlight),
            mid: color(.mid),
            deep: color(.deep),
            text: color(.text),
            accent: color(.accent)
        )
    }
}

/// Цвета варианта: четыре оттенка градиента (по порядку стопов), текст и акцент
/// (рамка, «✦», значок «N раз»). Порядок — как в генераторе ассетов.
nonisolated enum ParchmentToken: String, CaseIterable, Sendable {
    case light, highlight, mid, deep, text, accent

    /// Оттенки фона — для проверки контраста текста и акцента.
    static let backgrounds: [ParchmentToken] = [.light, .highlight, .mid, .deep]

    fileprivate var classicAssetName: String {
        switch self {
        case .light: AssetColor.parchmentLight.rawValue
        case .highlight: AssetColor.gold.rawValue
        case .mid: AssetColor.parchmentMid.rawValue
        case .deep: AssetColor.parchmentDeep.rawValue
        case .text: AssetColor.parchmentInk.rawValue
        case .accent: AssetColor.successDeep.rawValue
        }
    }
}

nonisolated struct ParchmentColors: Hashable, Sendable {
    let light: Color
    let highlight: Color
    let mid: Color
    let deep: Color
    let text: Color
    let accent: Color
}

nonisolated extension Theme {
    /// Тема с фоном `style` под арабским текстом. Меняются только пергамент, текст и акцент
    /// на нём; золото и «чернила» кнопок (`gold`, `parchmentInk`: счётчик, плеер) остаются —
    /// у «Ночного» они по-прежнему золотые.
    func withParchment(_ style: ParchmentStyle) -> Theme {
        let colors = style.colors
        var theme = self
        theme.palette.applyParchment(colors)
        theme.gradients.parchment = ThemeGradients.parchment(colors)
        return theme
    }
}

nonisolated private extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
