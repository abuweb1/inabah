import SwiftUI

/// Заголовок выполненной карточки: точка, начало арабского текста, ✓, сброс и раскрытие.
struct ZikrMiniRow: View {
    let arabic: String
    let isExpanded: Bool
    let onReset: () -> Void
    let onToggleExpanded: () -> Void

    @Environment(\.theme) private var theme

    private static let arabicSize: Double = 17
    private static let checkmarkSize: CGFloat = 28

    var body: some View {
        HStack(spacing: Spacing.xs) {
            Circle()
                .fill(theme.palette.success)
                .frame(width: Size.statusDot, height: Size.statusDot)
                .accessibilityHidden(true)
            ArabicText(text: arabic, size: Self.arabicSize, color: theme.palette.textSecondary, lineLimit: 1)
            Image(systemName: "checkmark")
                .font(.caption.weight(.bold))
                .foregroundStyle(theme.palette.success)
                .frame(width: Self.checkmarkSize, height: Self.checkmarkSize)
                .background(theme.palette.successDim, in: .circle)
                .accessibilityLabel(Text("zikr.completed"))
            Button("zikr.reset", systemImage: "arrow.counterclockwise", action: onReset)
                .labelStyle(.iconOnly)
                .buttonStyle(miniButtonStyle)
            Button(
                isExpanded ? "zikr.collapse" : "zikr.expand",
                systemImage: "chevron.down",
                action: onToggleExpanded
            )
            .labelStyle(.iconOnly)
            .buttonStyle(miniButtonStyle)
            .rotationEffect(.degrees(isExpanded ? 180 : 0))
        }
        .padding(.vertical, Spacing.s)
        .padding(.leading, Spacing.l)
        .padding(.trailing, Spacing.s)
    }

    private var miniButtonStyle: IconButtonStyle {
        IconButtonStyle(
            size: Size.miniButton,
            foreground: theme.palette.textSecondary,
            background: theme.palette.actionBackground,
            border: theme.palette.hairline
        )
    }
}
