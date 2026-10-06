import Foundation
import Testing
import UIKit

/// Относительная яркость и контраст по WCAG 2.x — по значениям ассета в sRGB.
/// Общий для тестов палитр (`ThemeStyleTests`) и фона арабского текста (`ParchmentStyleTests`).
enum Contrast {
    static func luminance(_ name: String) throws -> Double {
        let color = try #require(UIColor(named: name, in: .main, compatibleWith: nil), "нет ассета \(name)")
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        func linear(_ c: CGFloat) -> Double {
            let c = Double(c)
            return c <= 0.03928 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    static func ratio(_ a: Double, _ b: Double) -> Double {
        (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /// Контраст двух ассетов.
    static func ratio(_ first: String, _ second: String) throws -> Double {
        ratio(try luminance(first), try luminance(second))
    }

    /// Цвет в OKLab — для «различимы ли два цвета глазом» (учитывает и тон, и светлоту).
    static func oklab(_ name: String) throws -> (Double, Double, Double) {
        let color = try #require(UIColor(named: name, in: .main, compatibleWith: nil), "нет ассета \(name)")
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        func linear(_ c: CGFloat) -> Double {
            let c = Double(c)
            return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
        }
        let (lr, lg, lb) = (linear(r), linear(g), linear(b))
        let l = cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb)
        let m = cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb)
        let s = cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb)
        return (
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    static func distance(_ x: (Double, Double, Double), _ y: (Double, Double, Double)) -> Double {
        ((x.0 - y.0) * (x.0 - y.0) + (x.1 - y.1) * (x.1 - y.1) + (x.2 - y.2) * (x.2 - y.2)).squareRoot()
    }
}
