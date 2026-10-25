import SwiftUI

extension View {
    /// Пузырь-подсказка у элемента (Android `HintBubble`): правым краем по элементу, уголок — к его центру;
    /// `below` — под элементом, иначе над ним. Касаний не перехватывает и для VoiceOver не элемент:
    /// подсказка дублирует подпись кнопки. Появление и исчезновение анимирует вызывающий.
    func hintBubble(_ text: LocalizedStringResource, isVisible: Bool, tint: Color, textColor: Color, below: Bool) -> some View {
        modifier(HintBubbleModifier(text: text, isVisible: isVisible, tint: tint, textColor: textColor, below: below))
    }
}

private struct HintBubbleModifier: ViewModifier {
    let text: LocalizedStringResource
    let isVisible: Bool
    let tint: Color
    let textColor: Color
    let below: Bool

    @State private var anchor: CGSize = .zero
    @State private var bubbleSize: CGSize = .zero

    private enum Layout {
        static let arrow = CGSize(width: 12, height: 6)
        static let gap: CGFloat = 2
    }

    private var bubbleWidth: CGFloat { bubbleSize.width }
    private var anchorWidth: CGFloat { anchor.width }

    func body(content: Content) -> some View {
        content
            .onGeometryChange(for: CGSize.self) { $0.size } action: { anchor = $0 }
            .overlay(alignment: .topTrailing) {
                if isVisible {
                    bubble
                        // Явный сдвиг от верха элемента: под ним - на его высоту, над ним - на свою.
                        .offset(y: below ? anchor.height + Layout.gap : -bubbleSize.height - Layout.gap)
                        .opacity(bubbleSize == .zero ? 0 : 1)
                        .transition(.opacity)
                }
            }
    }

    private var bubble: some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(textColor)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, Spacing.m)
            .padding(.vertical, Spacing.xs)
            .padding(below ? .top : .bottom, Layout.arrow.height)
            .background {
                PopoverShape(
                    radius: Radius.control,
                    arrowX: bubbleWidth - anchorWidth / 2,
                    arrowSize: Layout.arrow,
                    pointsUp: below
                )
                .fill(tint)
            }
            .onGeometryChange(for: CGSize.self) { $0.size } action: { bubbleSize = $0 }
            .fixedTextSize()
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
