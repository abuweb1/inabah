import SwiftUI

/// Цвета своей стеклянной кнопки — копия Android `glassColors(tint, barBackground)` (`TopBar.kt`),
/// которая повторяет стекло iOS 26 по снимкам. Системное `.glass` — только при `ChromeStyle.system`.
///
/// Два вида: над прозрачной шапкой (главная, календарь) — полупрозрачный цвет раздела, значок `onAccent`;
/// над цветной шапкой (списки, хадис) — непрозрачное стекло насыщеннее шапки, обводка и значок в тон
/// раздела. При нажатии подложка заливается, обводка разгорается, содержимое перекрашивается; капсула
/// «А− А+» светится от нажатой стороны (`glowNear` → `glowFar`). Всё — сдвигами OKLCH, у каждого
/// оттенка раздела свой набор.
nonisolated struct GlassColors: Hashable, Sendable {
    let fill: SRGBColor
    let rim: SRGBColor
    /// Значок в покое; `nil` — `onAccent` темы (прозрачная шапка).
    let icon: SRGBColor?
    let pressedFill: SRGBColor
    let pressedRim: SRGBColor
    let pressedIcon: SRGBColor
    let glowNear: SRGBColor
    let glowFar: SRGBColor
    /// Доля ширины капсулы от нажатой стороны, где свет ещё яркий.
    let glowSolid: Double

    static let fillOpacity = 0.2
    static let rimOpacity = 0.4

    /// Над прозрачной шапкой.
    init(tint: SRGBColor) {
        let shifts = Shifts.nearest(toHue: tint.oklch.h)
        fill = tint.opacity(Self.fillOpacity)
        rim = tint.opacity(Self.rimOpacity)
        icon = nil
        pressedFill = tint.shifted(by: shifts.pressedFill)
        pressedRim = tint.shifted(by: shifts.pressedRim)
        pressedIcon = tint.shifted(by: shifts.pressedIcon)
        glowNear = tint.shifted(by: shifts.glowNear)
        glowFar = tint.shiftedOKLCH(lightness: Header.transparentGlowFarLightness, chroma: 0, hue: 0)
        glowSolid = shifts.glowSolid
    }

    /// Над цветной шапкой `header`: от тёмной шапки (вечерние) к средней (утренние, хадисы) — плавно.
    init(tint: SRGBColor, header: SRGBColor) {
        let section = OKLCH(tint)
        let bar = OKLCH(header)
        let shifts = Shifts.nearest(toHue: section.h)
        let midness = min(max((bar.l - Header.darkLightness) / (Header.midLightness - Header.darkLightness), 0), 1)
        let midIcon = section.shifted(by: shifts.icon)

        fill = bar.shifted(by: Header.darkFill).lerp(to: bar.shifted(by: shifts.fill), by: midness).color
        rim = OKLCH(l: bar.l + Header.darkRimLightness, c: bar.c * Header.darkRimChroma, h: bar.h)
            .lerp(to: section.shifted(by: shifts.rim), by: midness).color
        icon = OKLCH(l: Header.darkIconLightness, c: midIcon.c * Header.darkIconChroma, h: midIcon.h)
            .lerp(to: midIcon, by: midness).color
        pressedFill = tint.shifted(by: shifts.headerPressedFill)
        pressedRim = tint.shifted(by: shifts.pressedRim)
        pressedIcon = tint.shifted(by: shifts.headerPressedIcon)
        glowNear = tint.shifted(by: shifts.glowNear)
        glowFar = OKLCH(l: bar.l + Header.glowFarLightness, c: bar.c * Header.glowFarChroma, h: bar.h).color
        glowSolid = shifts.glowSolid
    }

    /// Правила для цветной шапки — одни для всех палитр (Android, `DARK_HEADER_L` и соседние).
    private enum Header {
        /// Светлота шапки (OKLCH L), между которыми стекло переходит от «тёмной» формы к «средней».
        static let darkLightness = 0.2
        static let midLightness = 0.43
        /// Над тёмной шапкой подложка светлее шапки, обводка — шапка светлее и насыщеннее, значок почти белый.
        static let darkFill = [0.06, 0.006, -4]
        static let darkRimLightness = 0.235
        static let darkRimChroma = 1.6
        static let darkIconLightness = 0.99
        static let darkIconChroma = 0.3
        /// Дальний край света капсулы — шапка светлее и насыщеннее.
        static let glowFarLightness = 0.135
        static let glowFarChroma = 1.65
        static let transparentGlowFarLightness = -0.2
    }

    /// Сдвиги одного оттенка (светлота, насыщенность, оттенок) — числа Android `GlassShifts`, снятые со
    /// снимков iOS. `fill` — от цвета шапки средней светлоты; остальное — от цвета раздела. `pressedFill` /
    /// `pressedIcon` — над прозрачной шапкой (Android `transparentPressed*`), `headerPressed*` — над цветной.
    struct Shifts: Hashable, Sendable {
        let anchorHue: Double
        let fill: [Double]
        let rim: [Double]
        let icon: [Double]
        let pressedFill: [Double]
        let pressedRim: [Double]
        let pressedIcon: [Double]
        let headerPressedFill: [Double]
        let headerPressedIcon: [Double]
        let glowNear: [Double]
        let glowSolid: Double

        /// Зелёный (хадисы, «Изумрудная»).
        static let green = Shifts(
            anchorHue: 164,
            fill: [-0.037, 0.009, -5.6],
            rim: [-0.21, -0.01, 0],
            icon: [0.155, -0.055, 19],
            pressedFill: [-0.072, 0.031, -3],
            pressedRim: [0.044, 0.052, -3],
            pressedIcon: [0.127, 0, 31],
            headerPressedFill: [-0.072, 0.031, -3],
            headerPressedIcon: [0.127, 0, 31],
            glowNear: [0.092, 0.048, 5],
            glowSolid: 0.35
        )
        /// Фиолетовый (азкары, «Фиолетовая»): розовый свет держится дальше середины капсулы.
        static let violet = Shifts(
            anchorHue: 299,
            fill: [-0.012, 0.031, -2],
            rim: [-0.188, 0.094, 4],
            icon: [0.197, -0.06, 27],
            pressedFill: [-0.036, 0.151, 23.4],
            pressedRim: [0.004, 0.114, 24],
            pressedIcon: [0.258, -0.115, 0],
            headerPressedFill: [0.015, 0.136, 29],
            headerPressedIcon: [0.258, -0.115, 0],
            glowNear: [0.006, 0.146, 29],
            glowSolid: 0.6
        )
        /// Янтарный («Янтарная»).
        static let amber = Shifts(
            anchorHue: 70,
            fill: [-0.034, 0.013, -8.9],
            rim: [-0.165, 0.041, -25.7],
            icon: [0.189, -0.046, 30.2],
            pressedFill: [-0.052, 0.066, -18.3],
            pressedRim: [0.02, 0.06, -19.1],
            pressedIcon: [0.16, 0.058, 34],
            headerPressedFill: [-0.096, 0.04, -19.1],
            headerPressedIcon: [0.22, -0.117, 0],
            glowNear: [-0.059, 0.05, -18.3],
            glowSolid: 0.4
        )
        /// Сине-серый (настройки, «Графит»).
        static let graphite = Shifts(
            anchorHue: 260,
            fill: [-0.051, 0.026, -0.3],
            rim: [-0.208, 0.068, 4.6],
            icon: [0.217, -0.072, -62.9],
            pressedFill: [-0.065, -0.011, 8.2],
            pressedRim: [-0.008, 0.097, 5.1],
            pressedIcon: [0.233, -0.09, 0],
            headerPressedFill: [-0.124, 0.077, 5.1],
            headerPressedIcon: [0.233, -0.09, 0],
            glowNear: [-0.08, 0.067, 2.1],
            glowSolid: 0.4
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

/// Цвет в OKLCH — для смешения форм стекла, как Android `OkLch` (оттенок — по короткой дуге).
private nonisolated struct OKLCH {
    var l: Double
    var c: Double
    var h: Double

    init(l: Double, c: Double, h: Double) {
        self.l = l
        self.c = c
        self.h = h
    }

    init(_ color: SRGBColor) {
        let value = color.oklch
        self.init(l: value.l, c: value.c, h: value.h)
    }

    /// Сдвиг (светлота, насыщенность, оттенок): светлота — в 0…1, насыщенность — не ниже нуля.
    func shifted(by shift: [Double]) -> OKLCH {
        OKLCH(l: min(max(l + shift[0], 0), 1), c: max(c + shift[1], 0), h: h + shift[2])
    }

    func lerp(to other: OKLCH, by t: Double) -> OKLCH {
        let dh = ((other.h - h).truncatingRemainder(dividingBy: 360) + 360 + 180).truncatingRemainder(dividingBy: 360) - 180
        return OKLCH(l: l + (other.l - l) * t, c: c + (other.c - c) * t, h: (h + dh * t + 360).truncatingRemainder(dividingBy: 360))
    }

    var color: SRGBColor {
        SRGBColor(oklch: (l, c, h))
    }
}

private extension SRGBColor {
    nonisolated func shifted(by shift: [Double]) -> SRGBColor {
        shiftedOKLCH(lightness: shift[0], chroma: shift[1], hue: shift[2])
    }
}
