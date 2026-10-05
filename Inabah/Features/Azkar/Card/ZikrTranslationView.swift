import SwiftUI

/// Транслитерация, перевод и источник зикра (раскрывается кнопкой «Аа»).
/// Размеры — общие с переводом хадиса (`ContentTextStyle`), шаг — из настройки «Размер текста».
struct ZikrTranslationView: View {
    let translation: ZikrTranslation

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    private static let bubbleRadius: CGFloat = 8

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            if let transliteration = translation.transliteration {
                Text(verbatim: transliteration)
                    .font(.content(.transliteration, scale: scale))
                    .foregroundStyle(theme.palette.textSecondary)
            }
            Text(verbatim: translation.text)
                .font(.content(.translation, scale: scale))
                .lineSpacing(ContentTextStyle.translationLineSpacing * scale)
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
                    .font(.content(.note, scale: scale))
                    .foregroundStyle(theme.palette.textTertiary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        // Здесь только контент (подписей интерфейса нет) — язык перевода на весь блок.
        .environment(\.locale, Locale(identifier: translation.language.rawValue))
    }
}
