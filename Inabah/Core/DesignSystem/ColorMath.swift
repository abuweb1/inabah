import SwiftUI

/// Цвет в гамма-sRGB (0…1) — для расчётов, которые должны совпасть с Android: `lerp` цветов в Compose
/// идёт в OKLab, `compositeOver` и контраст — по sRGB-компонентам. Цвета темы (ассеты) переводятся
/// сюда один раз при сборке темы, не в `body`.
nonisolated struct SRGBColor: Hashable, Sendable {
    var red: Double
    var green: Double
    var blue: Double
    var alpha: Double = 1

    static let black = SRGBColor(red: 0, green: 0, blue: 0)

    init(red: Double, green: Double, blue: Double, alpha: Double = 1) {
        self.red = red
        self.green = green
        self.blue = blue
        self.alpha = alpha
    }

    /// Значения цвета SwiftUI (ассета или производного) в sRGB.
    init(_ color: Color) {
        let resolved = color.resolve(in: EnvironmentValues())
        self.init(
            red: Self.encode(Double(resolved.linearRed)),
            green: Self.encode(Double(resolved.linearGreen)),
            blue: Self.encode(Double(resolved.linearBlue)),
            alpha: Double(resolved.opacity)
        )
    }

    var color: Color {
        Color(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }

    /// Смешение с `other` на долю `t` в OKLab (альфа — линейно), как `androidx.compose.ui.graphics.lerp`.
    func mixed(with other: SRGBColor, by t: Double) -> SRGBColor {
        let a = oklab
        let b = other.oklab
        let mix = (a.0 + (b.0 - a.0) * t, a.1 + (b.1 - a.1) * t, a.2 + (b.2 - a.2) * t)
        var result = Self(oklab: mix)
        result.alpha = alpha + (other.alpha - alpha) * t
        return result
    }

    /// Наложение полупрозрачного цвета на непрозрачный фон — по компонентам, как `compositeOver`.
    func composited(over background: SRGBColor) -> SRGBColor {
        let outAlpha = alpha + background.alpha * (1 - alpha)
        guard outAlpha > 0 else { return SRGBColor(red: 0, green: 0, blue: 0, alpha: 0) }
        func channel(_ fg: Double, _ bg: Double) -> Double {
            (fg * alpha + bg * background.alpha * (1 - alpha)) / outAlpha
        }
        return SRGBColor(
            red: channel(red, background.red),
            green: channel(green, background.green),
            blue: channel(blue, background.blue),
            alpha: outAlpha
        )
    }

    /// Относительная яркость по WCAG 2.x.
    var luminance: Double {
        0.2126 * Self.decode(red) + 0.7152 * Self.decode(green) + 0.0722 * Self.decode(blue)
    }

    /// Контраст текста `foreground` (с прозрачностью — поверх фона) на непрозрачном `background`.
    static func contrast(_ foreground: SRGBColor, on background: SRGBColor) -> Double {
        let text = foreground.composited(over: background).luminance
        let back = background.luminance
        return (max(text, back) + 0.05) / (min(text, back) + 0.05)
    }

    // MARK: - Пространства

    private static func decode(_ c: Double) -> Double {
        c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
    }

    private static func encode(_ c: Double) -> Double {
        let value = c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1 / 2.4) - 0.055
        return min(max(value, 0), 1)
    }

    private var oklab: (Double, Double, Double) {
        let (r, g, b) = (Self.decode(red), Self.decode(green), Self.decode(blue))
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return (
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private init(oklab lab: (Double, Double, Double)) {
        let l = pow(lab.0 + 0.3963377774 * lab.1 + 0.2158037573 * lab.2, 3)
        let m = pow(lab.0 - 0.1055613458 * lab.1 - 0.0638541728 * lab.2, 3)
        let s = pow(lab.0 - 0.0894841775 * lab.1 - 1.2914855480 * lab.2, 3)
        self.init(
            red: Self.encode(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            green: Self.encode(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            blue: Self.encode(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
        )
    }
}
