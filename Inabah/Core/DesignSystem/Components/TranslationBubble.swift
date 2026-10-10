import SwiftUI

extension View {
    /// Пузырь перевода: подложка `fill` со скруглёнными правыми углами и полоса `stripe` по переднему
    /// краю (в RTL — справа). Как у перевода зикра (`ZikrTranslationView`) и сносок хадиса; на Android —
    /// `Modifier.translationBubble`.
    func translationBubble(fill: Color, stripe: Color) -> some View {
        modifier(TranslationBubbleModifier(fill: fill, stripe: stripe))
    }
}

private struct TranslationBubbleModifier: ViewModifier {
    let fill: Color
    let stripe: Color

    private static let radius: CGFloat = 8

    func body(content: Content) -> some View {
        content
            .padding(.vertical, Spacing.s)
            .padding(.horizontal, Spacing.m)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(fill, in: .rect(cornerRadii: .init(bottomTrailing: Self.radius, topTrailing: Self.radius)))
            .overlay(alignment: .leading) {
                Rectangle().fill(stripe).frame(width: Size.accentStripe)
            }
    }
}
