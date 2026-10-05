import SwiftUI

/// Ползунок с отметками шагов (как выбор размера текста в Telegram): «А» по краям, дорожка
/// с засечками на каждом шаге, пройденная часть — в акцент темы, круглый бегунок.
/// Бегунок прилипает к ближайшему шагу прямо во время перетаскивания; нажатие на дорожку —
/// сразу на этот шаг. Системный `Slider` с каплей сюда не подходил — нет засечек.
struct StepSlider: View {
    /// Подпись для VoiceOver.
    let label: LocalizedStringResource
    /// Название выбранного шага — значение для VoiceOver.
    let valueTitle: LocalizedStringResource
    let count: Int
    let index: Int
    let onSelect: (Int) -> Void

    @Environment(\.theme) private var theme
    @State private var width: CGFloat = 1

    private enum Layout {
        static let trackHeight: CGFloat = 4
        static let tickWidth: CGFloat = 3
        static let tickHeight: CGFloat = 12
        static let thumbSize: CGFloat = 26
        /// «А» по краям — фиксированного размера: это значки шкалы, а не текст.
        static let smallLetterSize: CGFloat = 14
        static let largeLetterSize: CGFloat = 26
    }

    private var lastIndex: Int { max(count - 1, 1) }

    var body: some View {
        HStack(spacing: Spacing.m) {
            letter(size: Layout.smallLetterSize)
            track
            letter(size: Layout.largeLetterSize)
        }
        .frame(minHeight: Size.minTapTarget)
        .accessibilityElement()
        .accessibilityLabel(Text(label))
        .accessibilityValue(Text(valueTitle))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: select(index + 1)
            case .decrement: select(index - 1)
            @unknown default: break
            }
        }
        .sensoryFeedback(.selection, trigger: index)
    }

    private func letter(size: CGFloat) -> some View {
        Text(verbatim: "А")
            .font(.system(size: size))
            .foregroundStyle(theme.palette.onAccentSecondary)
            .accessibilityHidden(true)
    }

    /// Дорожка с засечками. Крайние засечки и бегунок стоят на расстоянии радиуса бегунка
    /// от краёв: бегунок на крайнем шаге не вылезает за дорожку.
    private var track: some View {
        let inset = Layout.thumbSize / 2
        let usable = max(width - Layout.thumbSize, 1)
        let step = usable / CGFloat(lastIndex)
        let thumbX = inset + step * CGFloat(index)
        let shadow = ShadowToken.card(theme.palette)

        return ZStack(alignment: .leading) {
            Capsule()
                .fill(theme.palette.track)
                .frame(width: usable, height: Layout.trackHeight)
                .offset(x: inset)
            Capsule()
                .fill(theme.palette.accentLight)
                .frame(width: usable, height: Layout.trackHeight)
                .scaleEffect(x: CGFloat(index) / CGFloat(lastIndex), anchor: .leading)
                .offset(x: inset)
            ForEach(0..<count, id: \.self) { position in
                Capsule()
                    .fill(position <= index ? theme.palette.accentLight : theme.palette.track)
                    .frame(width: Layout.tickWidth, height: Layout.tickHeight)
                    .offset(x: inset + step * CGFloat(position) - Layout.tickWidth / 2)
            }
            // Бегунок — фигура без содержимого: тень у неё самой, двигается сдвигом.
            Circle()
                .fill(theme.palette.onAccent)
                .shadow(color: shadow.color, radius: shadow.radius, y: shadow.y)
                .frame(width: Layout.thumbSize, height: Layout.thumbSize)
                .offset(x: thumbX - inset)
        }
        .frame(maxWidth: .infinity, minHeight: Size.minTapTarget, alignment: .leading)
        .contentShape(.rect)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = max($0, 1) }
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    let position = ((value.location.x - inset) / step).rounded()
                    select(Int(position))
                }
        )
        .animation(.snappy(duration: Motion.stepSnapSeconds), value: index)
    }

    private func select(_ position: Int) {
        let clamped = position.clamped(to: 0...(count - 1))
        guard clamped != index else { return }
        onSelect(clamped)
    }
}
