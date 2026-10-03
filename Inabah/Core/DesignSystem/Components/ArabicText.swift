import SwiftUI

/// Арабский текст: справа налево, Scheherazade New. Единственное место, где задаются
/// направление и шрифт арабского — экраны используют только этот компонент.
struct ArabicText: View {
    let text: String
    let size: Double
    var color: Color
    var bold = false
    /// `.leading` — к правому краю (направление справа налево), `.center` — по центру.
    var alignment: TextAlignment = .leading
    var lineLimit: Int?

    /// Дополнительный межстрочный интервал как доля кегля. У Scheherazade New высокая
    /// собственная строка (место под огласовки), поэтому добавка меньше, чем `line-height: 2` в CSS.
    private static let lineSpacingRatio = 0.3

    var body: some View {
        Text(verbatim: text)
            .font(.arabic(size: size, bold: bold))
            .lineSpacing(size * Self.lineSpacingRatio)
            .foregroundStyle(color)
            .multilineTextAlignment(alignment)
            .lineLimit(lineLimit)
            .frame(maxWidth: .infinity, alignment: alignment == .center ? .center : .leading)
            .environment(\.layoutDirection, .rightToLeft)
    }
}

#Preview {
    ArabicText(text: "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَٰهَ إِلَّا أَنْتَ", size: 21, color: Theme.inabah.palette.parchmentInk)
        .padding()
        .background(Theme.inabah.gradients.parchment.linear)
}
