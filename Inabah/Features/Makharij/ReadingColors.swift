import SwiftUI

/// Цвета «Учимся читать» - всё от цвета раздела `tabMakharij` (как Android `ReadingColors`,
/// `ReadingPresentation.kt`). Смешивание - в OKLab (`SRGBColor.mixed`), как `lerp` Compose: цвета совпадают
/// с Android. Считается один раз в экране и передаётся строкам, не в каждой строке.
nonisolated struct ReadingColors: Hashable, Sendable {
    private enum Share {
        static let onTintShade = 0.85
        static let tile = 0.1
        static let tileBorder = 0.4
        static let highlight = 0.16
        static let cardStart = 0.66
        static let cardEnd = 0.46
        static let sheetTint = 0.22
        static let parchmentCell = 0.06
        static let parchmentCellBorder = 0.18
        static let parchmentCaption = 0.75
    }

    /// Номера, рамка текущего шага, звучащий элемент, заливка кнопок.
    let tint: Color
    /// Текст на заливке `tint`: цвет раздела, затемнённый на 85 %.
    let onTint: Color
    /// Плитки и строки шагов.
    let tile: Color
    let tileBorder: Color
    /// Текущий шаг, выбранная буква.
    let highlight: Color
    let highlightBorder: Color
    /// Карточка курса на главной (белый текст читается: контраст ≥ 4,5).
    let card: ThemeGradient
    /// Кружок номера шага и лист буквы: 22 % цвета раздела на чёрном.
    let sheet: Color
    /// Буква в слове-примере на пергаменте.
    let letterHighlight: Color
    /// Ячейка слога на пергаменте, её рамка и подпись.
    let parchmentCell: Color
    let parchmentCellBorder: Color
    let parchmentCaption: Color

    init(palette: Palette) {
        let tint = palette.tabMakharij
        let tintSRGB = SRGBColor(tint)
        self.tint = tint
        onTint = tintSRGB.mixed(with: .black, by: Share.onTintShade).color
        tile = tint.opacity(Share.tile)
        tileBorder = tint.opacity(Share.tileBorder)
        highlight = tint.opacity(Share.highlight)
        highlightBorder = tint
        // Compose `Brush.linearGradient` по умолчанию - от верхнего левого угла к нижнему правому.
        card = ThemeGradient(
            [
                .init(color: tintSRGB.mixed(with: .black, by: Share.cardStart).color, location: 0),
                .init(color: tintSRGB.mixed(with: .black, by: Share.cardEnd).color, location: 1),
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        sheet = SRGBColor.black.mixed(with: tintSRGB, by: Share.sheetTint).color
        letterHighlight = palette.parchmentMark
        parchmentCell = palette.parchmentText.opacity(Share.parchmentCell)
        parchmentCellBorder = palette.parchmentText.opacity(Share.parchmentCellBorder)
        parchmentCaption = palette.parchmentText.opacity(Share.parchmentCaption)
    }
}
