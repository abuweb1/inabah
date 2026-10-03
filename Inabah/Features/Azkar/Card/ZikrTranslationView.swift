import SwiftUI

/// Транслитерация, перевод и источник зикра (раскрывается кнопкой «Аа»).
struct ZikrTranslationView: View {
    let translation: ZikrTranslation

    @Environment(\.theme) private var theme

    private static let bubbleRadius: CGFloat = 8

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            if let transliteration = translation.transliteration {
                Text(verbatim: transliteration)
                    .font(.footnote.italic())
                    .foregroundStyle(theme.palette.textSecondary)
            }
            Text(verbatim: translation.text)
                .font(.subheadline)
                .foregroundStyle(theme.palette.textPrimary)
                .padding(.vertical, Spacing.s)
                .padding(.horizontal, Spacing.m)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    theme.palette.accentDim,
                    in: .rect(cornerRadii: .init(bottomTrailing: Self.bubbleRadius, topTrailing: Self.bubbleRadius))
                )
                .overlay(alignment: .leading) {
                    Rectangle().fill(theme.palette.accent).frame(width: Size.accentStripe)
                }
            if let source = translation.source {
                Text(verbatim: source)
                    .font(.caption)
                    .foregroundStyle(theme.palette.textTertiary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // Здесь только контент (подписей интерфейса нет) — язык перевода на весь блок.
        .environment(\.locale, Locale(identifier: translation.language.rawValue))
    }
}
