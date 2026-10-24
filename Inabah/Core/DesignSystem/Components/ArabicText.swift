import SwiftUI

/// Арабский текст: справа налево, Scheherazade New (суры и аяты Корана — шрифт мусхафа).
/// Единственное место, где задаются направление и шрифт арабского — экраны используют только этот компонент.
struct ArabicText: View {
    let text: String
    let size: Double
    var color: Color
    var bold = false
    /// `.leading` — к правому краю (направление справа налево), `.center` — по центру.
    var alignment: TextAlignment = .leading
    var lineLimit: Int?
    /// Текст Корана — шрифтом мусхафа, кегль × `AppFont.mushafScale`; `bold` не действует.
    var mushaf = false

    /// Дополнительный межстрочный интервал как доля кегля. У Scheherazade New высокая
    /// собственная строка (место под огласовки), поэтому добавка меньше, чем `line-height: 2` в CSS.
    /// У Hafs своя строка ниже (1,76 кегля против 2,04) — её даёт сам шрифт, добавка та же.
    private static let lineSpacingRatio = 0.3

    var body: some View {
        let points = mushaf ? size * AppFont.mushafScale : size
        Text(verbatim: text)
            .font(mushaf ? .mushaf(size: points) : .arabic(size: points, bold: bold))
            .lineSpacing(points * Self.lineSpacingRatio)
            .foregroundStyle(color)
            .multilineTextAlignment(alignment)
            .lineLimit(lineLimit)
            .frame(maxWidth: .infinity, alignment: alignment == .center ? .center : .leading)
            .environment(\.layoutDirection, .rightToLeft)
    }

    /// Начало текста для однострочного превью (мини-строка зикра, свёрнутый хадис): первые слова
    /// и «…» — вёрстка и обрезка длинного арабского текста ради одной строки заметно дороже.
    /// Как `arabicPreview` на Android: слов не больше `words` — все через одиночный пробел.
    nonisolated static func preview(of text: String, words: Int = 8) -> String {
        let parts = text.split(whereSeparator: \.isWhitespace)
        let joined = parts.prefix(words).joined(separator: " ")
        return parts.count > words ? joined + "…" : joined
    }
}

#Preview {
    ArabicText(text: "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ", size: 21, color: Theme.inabah.palette.parchmentInk)
        .padding()
        .background(Theme.inabah.gradients.parchment.linear)
}
