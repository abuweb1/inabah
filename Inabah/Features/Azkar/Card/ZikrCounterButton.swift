import SwiftUI

/// Кольцо-счётчик: нажатие засчитывает одно прочтение, по заполнении — зелёная галочка.
struct ZikrCounterButton: View {
    let count: Int
    let total: Int
    let action: () -> Void

    @Environment(\.theme) private var theme
    @State private var flashTrigger = 0

    /// Подпись «из N» ужимается, чтобы помещаться в круг.
    private static let captionMinimumScale: CGFloat = 0.7

    private var isCompleted: Bool { count >= total }
    private var fraction: Double { Double(count) / Double(max(total, 1)) }

    var body: some View {
        Button(action: action) {
            ZStack {
                ProgressRing(
                    fraction: fraction,
                    trackColor: theme.palette.goldTrack,
                    fillColor: isCompleted ? theme.palette.success : theme.palette.gold,
                    lineWidth: Size.ringStroke
                )
                face
                    .padding(Spacing.xs)
            }
            .frame(width: Size.counter, height: Size.counter)
            .contentShape(.circle)
            // Вспышка «готово» — тень отдельного круга под кнопкой, а не всего содержимого.
            .background {
                Circle()
                    .fill(theme.palette.success)
                    .keyframeAnimator(initialValue: 0.0, trigger: flashTrigger) { [glowColor = theme.palette.success] circle, glow in
                        circle
                            .opacity(glow)
                            .shadow(color: glowColor.opacity(glow), radius: 14 * glow)
                    } keyframes: { _ in
                        LinearKeyframe(1, duration: 0.2)
                        LinearKeyframe(0, duration: 0.35)
                    }
            }
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.counterScale))
        .disabled(isCompleted)
        .onChange(of: isCompleted) { _, completed in
            if completed { flashTrigger += 1 }
        }
        .accessibilityLabel(Text("zikr.counter.label"))
        .accessibilityValue(Text("zikr.counter.value \(count) \(total)"))
        .accessibilityHint(isCompleted ? Text("zikr.completed") : Text("zikr.counter.hint"))
    }

    private var face: some View {
        let gradient = isCompleted ? theme.gradients.counterButtonDone : theme.gradients.counterButton
        return ZStack {
            // Тень — у круга, а не у лица с цифрами: смена числа не пересчитывает тень.
            let shadow = ShadowToken.goldButton(theme.palette)
            Circle()
                .fill(gradient.linear)
                .shadow(color: isCompleted ? .clear : shadow.color, radius: shadow.radius, y: shadow.y)
            if isCompleted {
                Image(systemName: "checkmark")
                    .font(.title3.weight(.bold))
                    .foregroundStyle(theme.palette.onAccent)
                    .transition(.scale.combined(with: .opacity))
            } else {
                VStack(spacing: 0) {
                    Text(count, format: .number)
                        .font(.title3.weight(.bold))
                        .monospacedDigit()
                        .contentTransition(.numericText(value: Double(count)))
                    // Мелкая подпись, но масштабируется с Dynamic Type (ограничено, чтобы влезала в круг).
                    Text("zikr.counter.of \(total)")
                        .font(.caption2)
                        .dynamicTypeSize(...DynamicTypeSize.large)
                        .minimumScaleFactor(Self.captionMinimumScale)
                }
                .foregroundStyle(theme.palette.parchmentInk)
            }
        }
        .animation(.snappy, value: count)
    }
}
