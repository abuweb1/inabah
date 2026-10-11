import SwiftUI

/// Цвета своей стеклянной кнопки над прозрачной шапкой (главная, календарь) — копия Android
/// `glassColors(tint, barBackground = null)` (`TopBar.kt`), которая повторяет стекло iOS 26 по снимкам.
/// Системное `.glass` не используется (решение пользователя 2026-10-11: не привязываться к системе).
///
/// В покое — полупрозрачный цвет раздела; при нажатии подложка заливается, обводка разгорается,
/// содержимое перекрашивается — сдвигами OKLCH от цвета раздела, у каждого оттенка свой набор.
nonisolated struct GlassColors: Hashable, Sendable {
    let fill: SRGBColor
    let rim: SRGBColor
    let pressedFill: SRGBColor
    let pressedRim: SRGBColor
    let pressedIcon: SRGBColor

    static let fillOpacity = 0.2
    static let rimOpacity = 0.4

    init(tint: SRGBColor) {
        let shifts = Shifts.nearest(toHue: tint.oklch.h)
        fill = tint.opacity(Self.fillOpacity)
        rim = tint.opacity(Self.rimOpacity)
        pressedFill = tint.shifted(by: shifts.pressedFill)
        pressedRim = tint.shifted(by: shifts.pressedRim)
        pressedIcon = tint.shifted(by: shifts.pressedIcon)
    }

    /// Сдвиги одного оттенка (светлота, насыщенность, оттенок) — числа Android (`GlassShifts`, поля
    /// `transparentPressedFill` / `pressedRim` / `transparentPressedIcon`), снятые со снимков iOS.
    struct Shifts: Hashable, Sendable {
        let anchorHue: Double
        let pressedFill: [Double]
        let pressedRim: [Double]
        let pressedIcon: [Double]

        /// Зелёный (хадисы, «Изумрудная»).
        static let green = Shifts(
            anchorHue: 164,
            pressedFill: [-0.072, 0.031, -3],
            pressedRim: [0.044, 0.052, -3],
            pressedIcon: [0.127, 0, 31]
        )
        /// Фиолетовый (азкары, «Фиолетовая»).
        static let violet = Shifts(
            anchorHue: 299,
            pressedFill: [-0.036, 0.151, 23.4],
            pressedRim: [0.004, 0.114, 24],
            pressedIcon: [0.258, -0.115, 0]
        )
        /// Янтарный («Янтарная»).
        static let amber = Shifts(
            anchorHue: 70,
            pressedFill: [-0.052, 0.066, -18.3],
            pressedRim: [0.02, 0.06, -19.1],
            pressedIcon: [0.16, 0.058, 34]
        )
        /// Сине-серый (настройки, «Графит»).
        static let graphite = Shifts(
            anchorHue: 260,
            pressedFill: [-0.065, -0.011, 8.2],
            pressedRim: [-0.008, 0.097, 5.1],
            pressedIcon: [0.233, -0.09, 0]
        )

        static let all = [green, violet, amber, graphite]

        /// Набор с ближайшим оттенком по кругу.
        static func nearest(toHue hue: Double) -> Shifts {
            all.min { distance($0.anchorHue, hue) < distance($1.anchorHue, hue) } ?? violet
        }

        private static func distance(_ a: Double, _ b: Double) -> Double {
            abs((a - b + 540).truncatingRemainder(dividingBy: 360) - 180)
        }
    }
}

private extension SRGBColor {
    nonisolated func shifted(by shift: [Double]) -> SRGBColor {
        shiftedOKLCH(lightness: shift[0], chroma: shift[1], hue: shift[2])
    }
}
