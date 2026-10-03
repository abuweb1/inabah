import Foundation
import Testing
import UIKit
@testable import Inabah

/// Относительная яркость и контраст по WCAG 2.x — по значениям ассета в sRGB.
private enum Contrast {
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

@Suite("Палитры оформления")
struct ThemeStyleTests {
    private static let unified = ThemeStyle.allCases.filter { $0 != .sections }

    @Test("«По умолчанию» — прежняя тема без изменений")
    func sectionsIsDefaultTheme() {
        #expect(ThemeStyle.sections.theme == Theme.inabah)
    }

    @Test("У единых стилей есть все цвета, и стили различаются", arguments: unified)
    func assetsExist(style: ThemeStyle) {
        for token in ThemeColorToken.allCases {
            #expect(UIColor(named: token.assetName(for: style), in: .main, compatibleWith: nil) != nil, "\(token.assetName(for: style))")
        }
        #expect(style.theme != Theme.inabah)
    }

    @Test("Вторичный текст читается на карточке зикра (≥ 4.5:1)", arguments: unified)
    func secondaryTextContrast(style: ThemeStyle) throws {
        let text = try Contrast.luminance(ThemeColorToken.textSecondary.assetName(for: style))
        let card = try Contrast.luminance(ThemeColorToken.card.assetName(for: style))
        #expect(Contrast.ratio(text, card) >= 4.5)
    }

    @Test("Белый заголовок читается на карточках навигации (≥ 3:1, крупный жирный)", arguments: unified)
    func cardTitleContrast(style: ThemeStyle) throws {
        let mids: [ThemeColorToken] = [.morningCardMid, .eveningCardMid, .nawawiCardMid, .qudsiCardMid, .ajurriCardMid]
        for token in mids {
            let card = try Contrast.luminance(token.assetName(for: style))
            #expect(Contrast.ratio(1, card) >= 3, "\(token.assetName(for: style))")
        }
    }

    @Test("Утренние светлее вечерних, карточки сборников явно различимы", arguments: unified)
    func cardsAreDistinct(style: ThemeStyle) throws {
        let morning = try Contrast.luminance(ThemeColorToken.morningCardMid.assetName(for: style))
        let evening = try Contrast.luminance(ThemeColorToken.eveningCardMid.assetName(for: style))
        #expect(morning > evening)

        // Расстояние в OKLab: порог заметности ≈ 0.02, «явно различимы» — от 0.07.
        let collections = try [ThemeColorToken.nawawiCardMid, .qudsiCardMid, .ajurriCardMid]
            .map { try Contrast.oklab($0.assetName(for: style)) }
        for i in collections.indices {
            for j in collections.indices where j > i {
                #expect(Contrast.distance(collections[i], collections[j]) >= 0.07)
            }
        }
    }
}

@MainActor
@Suite("Выбор палитры")
struct AppearanceSettingsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("AppearanceSettingsTests")
    }

    @Test("По умолчанию — свой цвет у каждого раздела")
    func defaultStyle() {
        #expect(AppearanceSettings(defaults: defaults).style == .sections)
    }

    @Test("Выбор сохраняется между запусками")
    func persists() {
        AppearanceSettings(defaults: defaults).style = .amber

        #expect(AppearanceSettings(defaults: defaults).style == .amber)
    }

    @Test("Повреждённое значение — палитра по умолчанию")
    func unknownValue() {
        defaults.set("neon", forKey: "appearance.themeStyle")

        #expect(AppearanceSettings(defaults: defaults).style == .sections)
    }
}
