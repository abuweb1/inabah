import Foundation
import SwiftUI
import Testing
import UIKit
@testable import Inabah

@Suite("Фон арабского текста")
struct ParchmentStyleTests {
    @Test("Все цвета вариантов есть в ассетах", arguments: ParchmentStyle.allCases)
    func assetsExist(style: ParchmentStyle) {
        for token in ParchmentToken.allCases {
            #expect(UIColor(named: style.assetName(token), in: .main, compatibleWith: nil) != nil, "\(style.assetName(token))")
        }
    }

    /// Арабский текст — основное, ради чего экран: 7:1 (WCAG AAA) к каждому оттенку градиента.
    @Test("Текст читается на каждом оттенке фона (≥ 7:1)", arguments: ParchmentStyle.allCases)
    func textContrast(style: ParchmentStyle) throws {
        for background in ParchmentToken.backgrounds {
            let ratio = try Contrast.ratio(style.assetName(.text), style.assetName(background))
            #expect(ratio >= 7, "\(style.rawValue): текст на \(background.rawValue) — \(ratio)")
        }
    }

    /// Рамка, «✦» и значок «N раз» — 4,5:1 (значок — мелкий текст).
    @Test("Акцент читается на каждом оттенке фона (≥ 4,5:1)", arguments: ParchmentStyle.allCases)
    func accentContrast(style: ParchmentStyle) throws {
        for background in ParchmentToken.backgrounds {
            let ratio = try Contrast.ratio(style.assetName(.accent), style.assetName(background))
            #expect(ratio >= 4.5, "\(style.rawValue): акцент на \(background.rawValue) — \(ratio)")
        }
    }

    /// Буква в слове-примере («Учимся читать») - свой цвет, не текст и не рамка: цвет рамки на «Сепии» и
    /// «Дымчатом» сливался (как Android `ParchmentStyleTest`).
    @Test("Выделение буквы читается на каждом оттенке фона (≥ 4,5:1) и отличается от текста и рамки",
          arguments: ParchmentStyle.allCases)
    func markContrast(style: ParchmentStyle) throws {
        for background in ParchmentToken.backgrounds {
            let ratio = try Contrast.ratio(style.assetName(.mark), style.assetName(background))
            #expect(ratio >= 4.5, "\(style.rawValue): выделение на \(background.rawValue) — \(ratio)")
        }
        let colors = style.colors
        #expect(colors.mark != colors.text)
        #expect(colors.mark != colors.accent)
    }

    /// Фон рисунков руководства (как Android): у «Пергамента» - исходный цвет рисунков, у остальных - `light`.
    @Test("Фон рисунков: у «Пергамента» - цвет рисунков, у остальных - светлый тон варианта", arguments: ParchmentStyle.allCases)
    func illustrationBackground(style: ParchmentStyle) throws {
        let illustration = SRGBColor(Color(style.assetName(.illustration), bundle: .main))
        if style == .classic {
            #expect(abs(illustration.red - 0xFB / 255.0) < 0.002)
            #expect(abs(illustration.green - 0xEE / 255.0) < 0.002)
            #expect(abs(illustration.blue - 0xD1 / 255.0) < 0.002)
        } else {
            #expect(illustration == SRGBColor(Color(style.assetName(.light), bundle: .main)))
        }
    }

    @Test("«Пергамент» — прежний вид: тема по умолчанию не меняется")
    func classicKeepsDefaultTheme() {
        #expect(Theme.inabah.withParchment(.classic) == Theme.inabah)
        #expect(ParchmentStyle.classic.colors.highlight == Theme.inabah.palette.gold)
        #expect(ParchmentStyle.classic.colors.text == Theme.inabah.palette.parchmentInk)
        #expect(ParchmentStyle.classic.colors.accent == Theme.inabah.palette.successDeep)
    }

    /// Счётчик и кнопка плеера — золотые с тёмными чернилами при любом фоне (и у «Ночного»).
    @Test("Вариант не меняет золото, чернила кнопок и градиент счётчика", arguments: ParchmentStyle.allCases)
    func buttonsStayGolden(style: ParchmentStyle) {
        for base in ThemeStyle.allCases.map(\.theme) {
            let theme = base.withParchment(style)
            #expect(theme.palette.gold == base.palette.gold)
            #expect(theme.palette.parchmentInk == base.palette.parchmentInk)
            #expect(theme.gradients.counterButton == base.gradients.counterButton)
            #expect(theme.gradients.parchmentStripe == base.gradients.parchmentStripe)
        }
    }

    @Test("Фон не меняет палитру: тот же фон на любой палитре")
    func parchmentIndependentOfPalette() {
        let jade = ParchmentStyle.jade.colors
        for base in ThemeStyle.allCases.map(\.theme) {
            let theme = base.withParchment(.jade)
            #expect(theme.palette.parchmentText == jade.text)
            #expect(theme.palette.parchmentAccent == jade.accent)
            #expect(theme.palette.parchmentMark == jade.mark)
            #expect(theme.palette.parchmentIllustration == jade.illustration)
            #expect(theme.palette.background == base.palette.background)
            #expect(theme.palette.accent == base.palette.accent)
        }
    }
}

@MainActor
@Suite("Выбор фона арабского текста")
struct ParchmentSettingsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("ParchmentSettingsTests")
    }

    @Test("По умолчанию — «Пергамент»")
    func defaultIsClassic() {
        #expect(ParchmentSettings(defaults: defaults).style == .classic)
    }

    @Test("Выбор сохраняется между запусками")
    func persists() {
        ParchmentSettings(defaults: defaults).select(.night)

        #expect(ParchmentSettings(defaults: defaults).style == .night)
    }

    @Test("Повторный выбор того же фона не уведомляет наблюдателей")
    func sameStyleDoesNotNotify() {
        let settings = ParchmentSettings(defaults: defaults)
        let notified = NotificationFlag()
        withObservationTracking {
            _ = settings.style
        } onChange: {
            notified.set()
        }

        settings.select(.classic)

        #expect(!notified.value)
    }

    @Test("Повреждённое значение — «Пергамент»")
    func corruptedValueFallsBack() {
        defaults.set("parchment-from-the-future", forKey: "appearance.parchment")

        #expect(ParchmentSettings(defaults: defaults).style == .classic)
    }
}
