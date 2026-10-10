import Foundation
import SwiftUI
import Testing
@testable import Inabah

@Suite("Сноски хадиса")
struct HadithNotesTests {
    @Test("Слово из перевода — в кавычках, пояснение ко всему хадису — без", arguments: [
        ("намерениям", true),
        ("Дела оцениваются", true),
        ("ДЕЛА  ОЦЕНИВАЮТСЯ!", true),
        ("всё", true),
        ("оцени", false),
        ("Различия передач", false),
        ("", false),
        ("—", false),
    ])
    func quotedTerm(term: String, expected: Bool) {
        let translation = "«Поистине, дела оцениваются только по намерениям», — и всё."

        #expect(HadithNoteTerm.isQuoted(term, in: translation) == expected)
    }

    @Test("«ё» и «е» не различаются")
    func yoFolding() {
        #expect(HadithNoteTerm.isQuoted("Всё", in: "и все это"))
    }
}

/// Цвета сносок — как на Android (`NoteColorsTest`): текст — OKLab-смесь белого с `tabHadith`, читается.
@Suite("Цвета сносок")
struct NoteColorsTests {
    private typealias Linear = (r: Double, g: Double, b: Double)

    private static func linear(_ color: Color) -> Linear {
        let resolved = color.resolve(in: EnvironmentValues())
        return (Double(resolved.linearRed), Double(resolved.linearGreen), Double(resolved.linearBlue))
    }

    private static func oklab(_ c: Linear) -> (Double, Double, Double) {
        let l = cbrt(0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b)
        let m = cbrt(0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b)
        let s = cbrt(0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b)
        return (
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private static func luminance(_ c: Linear) -> Double {
        0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b
    }

    @Test("Текст сносок — смесь белого и цвета хадисов в OKLab (30 %)", arguments: ThemeStyle.allCases)
    func noteTextIsOklabMix(style: ThemeStyle) {
        let palette = style.theme.palette
        let white = Self.oklab(Self.linear(palette.onAccent))
        let tab = Self.oklab(Self.linear(palette.tabHadith))
        let t = Palette.noteTextTint
        let expected = (white.0 + (tab.0 - white.0) * t, white.1 + (tab.1 - white.1) * t, white.2 + (tab.2 - white.2) * t)

        let actual = Self.oklab(Self.linear(palette.noteText))

        #expect(abs(actual.0 - expected.0) < 0.01)
        #expect(abs(actual.1 - expected.1) < 0.01)
        #expect(abs(actual.2 - expected.2) < 0.01)
    }

    @Test("Текст сносок читается на шапке хадисов (≥ 4.5:1)", arguments: ThemeStyle.allCases)
    func noteTextContrast(style: ThemeStyle) {
        let palette = style.theme.palette
        let text = Self.luminance(Self.linear(palette.noteText))
        let background = Self.luminance(Self.linear(palette.hadithHeader))

        #expect(Contrast.ratio(text, background) >= 4.5)
    }
}
