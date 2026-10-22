import SwiftUI

/// Кнопка курса во всю ширину (Android `ReadingButton` / `ReadingOutlineButton`): заливка цветом раздела
/// с тёмным текстом или рамка цвета раздела. Размер текста закреплён, нажатие - 0,98 и прозрачность 0,8.
struct ReadingButtonStyle: ButtonStyle {
    let colors: ReadingColors
    /// `false` - контурная кнопка (справка, «Читаем слова», «Весь алфавит»).
    var isProminent = true

    private enum Layout {
        static let pressedOpacity = 0.8
    }

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .multilineTextAlignment(.center)
            .foregroundStyle(isProminent ? colors.onTint : colors.tint)
            .padding(.horizontal, Spacing.xl)
            .frame(maxWidth: .infinity, minHeight: Size.primaryButtonHeight)
            .background {
                if isProminent {
                    RoundedRectangle(cornerRadius: Radius.box).fill(colors.tint)
                } else {
                    RoundedRectangle(cornerRadius: Radius.box).strokeBorder(colors.tint, lineWidth: Size.hairline)
                }
            }
            .contentShape(.rect(cornerRadius: Radius.box))
            .scaleEffect(configuration.isPressed ? PressFeedback.wideScale : 1)
            .opacity(configuration.isPressed ? Layout.pressedOpacity : 1)
            .animation(Motion.press, value: configuration.isPressed)
            .fixedTextSize()
    }
}

/// Плитка курса: подложка цвета раздела и рамка (строки текста, правила, темы).
struct ReadingTile: ViewModifier {
    let colors: ReadingColors
    var cornerRadius = Radius.card
    var lineWidth = Size.hairline
    var isHighlighted = false

    func body(content: Content) -> some View {
        content.surface(
            isHighlighted ? colors.highlight : colors.tile,
            cornerRadius: cornerRadius,
            border: isHighlighted ? colors.highlightBorder : colors.tileBorder,
            lineWidth: lineWidth
        )
    }
}

/// Ячейка на пергаменте: слог, форма, слово-пример, формула. `isHighlighted` - звучит запись слова-примера
/// (цвет раздела); `isPlaying` - звучит слог «Послушать алфавит» или формула (`parchmentMark`, рамка 1,5 pt).
struct ParchmentCell: ViewModifier {
    let colors: ReadingColors
    var isHighlighted = false
    var isPlaying = false

    func body(content: Content) -> some View {
        content
            .surface(fill, cornerRadius: Radius.box, border: border, lineWidth: isPlaying ? ReadingMetrics.tileBorder : Size.hairline)
            .animation(Motion.highlight, value: isHighlighted)
            .animation(Motion.highlight, value: isPlaying)
    }

    private var fill: Color {
        if isPlaying { return colors.parchmentPlaying }
        return isHighlighted ? colors.highlight : colors.parchmentCell
    }

    private var border: Color {
        if isPlaying { return colors.parchmentPlayingBorder }
        return isHighlighted ? colors.highlightBorder : colors.parchmentCellBorder
    }
}

extension View {
    func readingTile(_ colors: ReadingColors, cornerRadius: CGFloat = Radius.card, lineWidth: CGFloat = Size.hairline, isHighlighted: Bool = false) -> some View {
        modifier(ReadingTile(colors: colors, cornerRadius: cornerRadius, lineWidth: lineWidth, isHighlighted: isHighlighted))
    }

    func parchmentCell(_ colors: ReadingColors, isHighlighted: Bool = false, isPlaying: Bool = false) -> some View {
        modifier(ParchmentCell(colors: colors, isHighlighted: isHighlighted, isPlaying: isPlaying))
    }
}

/// Толщина рамки плиток урока (буквы, знаки, фишки листа буквы).
enum ReadingMetrics {
    static let tileBorder: CGFloat = 1.5
}
