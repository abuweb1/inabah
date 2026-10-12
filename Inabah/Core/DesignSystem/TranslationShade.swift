import SwiftUI

// Ползунок «Фон перевода» (решение пользователя 2026-10-10, docs/analysis/2026-10-10-shared-translation-block.md):
// −1 — темнее, 0 — как задумано, +1 — светлее до предела читаемости в этой палитре. Формула и пороги —
// один в один с Android (`TranslationShade.kt`, docs/android/05-design-system.md, 5.6).

/// Карточка перевода, к которой относится предел осветления.
nonisolated enum TranslationCard: Sendable {
    case azkar
    case hadith
}

nonisolated enum TranslationShade {
    /// Доля чёрного у фона перевода при −1 — «почти чёрный», текст не теряется.
    static let darkMax = 0.8
    /// Перевод — как основной текст (WCAG AAA), остальные строки — AA.
    static let textMin = 7.0
    static let labelMin = 4.5
    /// Источник зикра (`textTertiary`) уже сейчас ниже AA — не ниже 3:1.
    static let sourceMin = 3.0
    /// Во сколько раз быстрее фона светлеет источник зикра (к `textSecondary` — при 1/4 осветления).
    static let sourceLift = 4.0
    /// Шагов двоичного поиска предела — точность 1/4096 доли.
    static let searchSteps = 12
    /// Меньший сдвиг цвет не меняет — тема без изменений.
    static let noShade = 1e-4

    /// Наибольшая доля осветления (0…1) фона `card`, при которой строки на нём читаются; если в палитре уже
    /// меньше порога — не хуже, чем сейчас.
    static func lightLimit(_ palette: Palette, _ card: TranslationCard) -> Double {
        let colors = Colors(palette)
        let required = readability(colors, card, 0).map { min($0.ratio, $0.minimum) }
        func readable(_ t: Double) -> Bool {
            zip(readability(colors, card, t), required).allSatisfy { $0.ratio >= $1 }
        }
        if readable(1) { return 1 }
        var low = 0.0
        var high = 1.0
        for _ in 0..<searchSteps {
            let mid = (low + high) / 2
            if readable(mid) { low = mid } else { high = mid }
        }
        return low
    }

    /// Цвета палитры в sRGB — переводятся один раз на расчёт.
    struct Colors {
        let zikrCard, accentDim, accentLight, zikrSource, textPrimary, textSecondary: SRGBColor
        let hadithCard, tabHadith, noteText, noteFill, onAccentSecondary: SRGBColor

        init(_ p: Palette) {
            zikrCard = SRGBColor(p.zikrCard)
            accentDim = SRGBColor(p.accentDim)
            accentLight = SRGBColor(p.accentLight)
            zikrSource = SRGBColor(p.zikrSource)
            textPrimary = SRGBColor(p.textPrimary)
            textSecondary = SRGBColor(p.textSecondary)
            hadithCard = SRGBColor(p.hadithCard)
            tabHadith = SRGBColor(p.tabHadith)
            noteText = SRGBColor(p.noteText)
            noteFill = SRGBColor(p.noteFill)
            onAccentSecondary = SRGBColor(p.onAccentSecondary)
        }

        func zikrSource(at t: Double) -> SRGBColor {
            zikrSource.mixed(with: textSecondary, by: min(t * TranslationShade.sourceLift, 1))
        }
    }

    /// Контраст каждой строки на фоне, осветлённом на долю `t`, — пары (контраст, порог).
    static func readability(_ c: Colors, _ card: TranslationCard, _ t: Double) -> [(ratio: Double, minimum: Double)] {
        switch card {
        case .azkar:
            let background = c.zikrCard.mixed(with: c.accentLight, by: t)
            let bubble = c.accentDim.mixed(with: c.accentLight, by: t)
            return [
                (SRGBColor.contrast(c.textPrimary, on: bubble), textMin),
                (SRGBColor.contrast(c.textSecondary, on: background), labelMin),
                (SRGBColor.contrast(c.zikrSource(at: t), on: background), sourceMin),
            ]
        case .hadith:
            let background = c.hadithCard.mixed(with: c.tabHadith, by: t)
            return [
                (SRGBColor.contrast(c.textPrimary, on: background), textMin),
                (SRGBColor.contrast(c.noteText, on: background), labelMin),
                (SRGBColor.contrast(c.onAccentSecondary, on: background), labelMin),
                (SRGBColor.contrast(c.noteText, on: c.noteFill.composited(over: background)), labelMin),
            ]
        }
    }
}

/// Пределы осветления и цвета палитры в sRGB считаются один раз на палитру (К10 аудита Android, А5 аудита
/// iOS: не на каждый кадр ползунка). Палитр немного — по числу палитр и фонов арабского текста.
@MainActor
private enum TranslationShadeLimits {
    static var cache: [Palette: (azkar: Double, hadith: Double)] = [:]
    static var colors: [Palette: TranslationShade.Colors] = [:]

    static func limits(_ palette: Palette) -> (azkar: Double, hadith: Double) {
        if let cached = cache[palette] { return cached }
        let limits = (TranslationShade.lightLimit(palette, .azkar), TranslationShade.lightLimit(palette, .hadith))
        cache[palette] = limits
        return limits
    }

    static func colors(_ palette: Palette) -> TranslationShade.Colors {
        if let cached = colors[palette] { return cached }
        let value = TranslationShade.Colors(palette)
        colors[palette] = value
        return value
    }
}

extension Theme {
    /// Тема со сдвинутым фоном перевода: карточка и пузырь зикра (`zikrCard`, `accentDim`) и карточка хадиса
    /// (`hadithCard`); светлее — и источник зикра (`zikrSource`). Применять к теме **без** сдвига
    /// (`ThemedModifier` — после `withParchment`; образец в «Палитре» — так же).
    @MainActor
    func withTranslationShade(_ shade: Double) -> Theme {
        let value = shade.isNaN ? 0 : min(max(shade, -1), 1)
        guard abs(value) >= TranslationShade.noShade else { return self }
        let c = TranslationShadeLimits.colors(palette)
        var theme = self
        if value < 0 {
            let t = -value * TranslationShade.darkMax
            theme.palette.zikrCard = c.zikrCard.mixed(with: .black, by: t).color
            theme.palette.accentDim = c.accentDim.mixed(with: .black, by: t).color
            theme.palette.hadithCard = c.hadithCard.mixed(with: .black, by: t).color
        } else {
            let limits = TranslationShadeLimits.limits(palette)
            let azkar = value * limits.azkar
            let hadith = value * limits.hadith
            theme.palette.zikrCard = c.zikrCard.mixed(with: c.accentLight, by: azkar).color
            theme.palette.accentDim = c.accentDim.mixed(with: c.accentLight, by: azkar).color
            theme.palette.zikrSource = c.zikrSource(at: azkar).color
            theme.palette.hadithCard = c.hadithCard.mixed(with: c.tabHadith, by: hadith).color
        }
        return theme
    }
}
