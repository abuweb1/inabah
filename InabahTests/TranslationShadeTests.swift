import Foundation
import SwiftUI
import Testing
@testable import Inabah

@Suite("Цветовая математика")
struct ColorMathTests {
    private let white = SRGBColor(red: 1, green: 1, blue: 1)

    @Test("Смешение в OKLab: края — исходные цвета, середина белого и чёрного — как в Compose")
    func oklabMix() {
        let middle = white.mixed(with: .black, by: 0.5)

        #expect(abs(white.mixed(with: .black, by: 0).red - 1) < 1e-6)
        #expect(white.mixed(with: .black, by: 1).red < 1e-6)
        // L = 0,5 в OKLab → линейная яркость 0,125 → sRGB ≈ 0,3885 (Compose `lerp` даёт то же).
        #expect(abs(middle.red - 0.3885) < 0.002)
        #expect(abs(middle.red - middle.blue) < 1e-6)
    }

    @Test("Контраст: белый на чёрном — 21:1; полупрозрачный текст сначала накладывается на фон")
    func contrast() {
        #expect(abs(SRGBColor.contrast(white, on: .black) - 21) < 0.01)
        var half = white
        half.alpha = 0.5
        #expect(SRGBColor.contrast(half, on: .black) < 21)
        #expect(SRGBColor.contrast(half, on: .black) > 4)
    }
}

@MainActor
@Suite("Фон перевода")
struct TranslationShadeTests {
    private static func srgb(_ color: Color) -> SRGBColor { SRGBColor(color) }

    private static func close(_ a: Color, _ b: Color) -> Bool {
        let x = srgb(a), y = srgb(b)
        return abs(x.red - y.red) < 0.004 && abs(x.green - y.green) < 0.004 && abs(x.blue - y.blue) < 0.004
    }

    @Test("Около середины — та же тема", arguments: ThemeStyle.allCases)
    func centerKeepsTheme(style: ThemeStyle) {
        let theme = style.theme
        #expect(theme.withTranslationShade(0) == theme)
        #expect(theme.withTranslationShade(0.00001) == theme)
        #expect(theme.withTranslationShade(.nan) == theme)
    }

    @Test("Темнее: карточки к чёрному на 0,8; золото, пергамент и счётчик не меняются", arguments: ThemeStyle.allCases)
    func darkest(style: ThemeStyle) {
        let theme = style.theme
        let dark = theme.withTranslationShade(-1)

        #expect(Self.close(dark.palette.zikrCard, Self.srgb(theme.palette.zikrCard).mixed(with: .black, by: 0.8).color))
        #expect(Self.close(dark.palette.hadithCard, Self.srgb(theme.palette.hadithCard).mixed(with: .black, by: 0.8).color))
        #expect(dark.palette.gold == theme.palette.gold)
        #expect(dark.palette.parchmentText == theme.palette.parchmentText)
        #expect(dark.palette.card == theme.palette.card)
        #expect(dark.gradients == theme.gradients)
    }

    @Test("Светлее до предела — все строки читаются; предел наибольший", arguments: ThemeStyle.allCases)
    func lightestIsReadable(style: ThemeStyle) {
        let palette = style.theme.palette
        let colors = TranslationShade.Colors(palette)
        for card in [TranslationCard.azkar, .hadith] {
            let limit = TranslationShade.lightLimit(palette, card)
            let required = TranslationShade.readability(colors, card, 0).map { min($0.ratio, $0.minimum) }
            let atLimit = TranslationShade.readability(colors, card, limit)

            #expect(limit >= 0 && limit <= 1)
            #expect(zip(atLimit, required).allSatisfy { $0.ratio >= $1 }, "\(style) \(card)")
            if limit < 1 {
                let beyond = TranslationShade.readability(colors, card, min(limit + 0.01, 1))
                #expect(!zip(beyond, required).allSatisfy { $0.ratio >= $1 }, "\(style) \(card): предел не наибольший")
            }
        }
    }

    @Test("Карточка хадиса «По умолчанию» — шапка хадисов, затемнённая на половину; в единых стилях — карточка")
    func hadithCardToken() {
        let palette = Theme.inabah.palette
        #expect(Self.close(palette.hadithCard, Self.srgb(palette.hadithHeader).mixed(with: .black, by: 0.5).color))
        #expect(ThemeStyle.emerald.theme.palette.hadithCard == ThemeStyle.emerald.theme.palette.card)
        #expect(palette.zikrCard == palette.card)
        #expect(palette.zikrSource == palette.textTertiary)
    }
}

@MainActor
@Suite("Настройка «Фон перевода»")
struct TranslationShadeSettingsTests {
    let storage: IsolatedDefaults

    init() throws {
        storage = try IsolatedDefaults("TranslationShadeSettingsTests")
    }

    @Test("По умолчанию — как задумано; значение сохраняется и ограничивается краями")
    func storeAndClamp() {
        let settings = TranslationShadeSettings(defaults: storage.defaults)
        #expect(settings.value == 0)

        settings.set(0.4)
        #expect(TranslationShadeSettings(defaults: storage.defaults).value == 0.4)

        settings.set(3)
        #expect(settings.value == 1)
        settings.set(.nan)
        #expect(settings.value == 0)
    }

    @Test("Мусор в хранилище — край или «как задумано»")
    func garbage() {
        storage.defaults.set(-7.5, forKey: "appearance.translationShade")
        #expect(TranslationShadeSettings(defaults: storage.defaults).value == -1)
    }
}
