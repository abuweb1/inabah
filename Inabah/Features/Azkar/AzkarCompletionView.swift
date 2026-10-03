import SwiftUI

/// «Машаа Аллах!» — показывается, когда прочитаны все азкары раздела.
struct AzkarCompletionView: View {
    let section: AzkarSection
    let onGoHome: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let emojiSize: CGFloat = 60
    private static let arabicSize: Double = 24

    var body: some View {
        VStack(spacing: Spacing.l) {
            // Пульсация живёт ровно столько, сколько оверлей на экране (phaseAnimator),
            // и выключается при «Уменьшении движения».
            Text(verbatim: "🤲")
                .font(.system(size: Self.emojiSize))
                .phaseAnimator([1.0, 1.08]) { content, scale in
                    content.scaleEffect(reduceMotion ? 1 : scale)
                } animation: { _ in
                    .easeInOut(duration: 1)
                }
                .accessibilityHidden(true)

            Text("azkar.completion.title")
                .font(.title.bold())
                .foregroundStyle(theme.palette.success)
                .accessibilityAddTraits(.isHeader)

            ArabicText(
                text: "الحمد لله رب العالمين",
                size: Self.arabicSize,
                color: theme.palette.parchmentInk,
                alignment: .center
            )
            .padding(.vertical, Spacing.m)
            .padding(.horizontal, Spacing.xlPlus)
            .surface(
                theme.gradients.parchment.linear,
                cornerRadius: Radius.control,
                border: theme.palette.successDeep,
                lineWidth: 2
            )

            Text(section.completionMessage)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(theme.palette.textSecondary)

            Button(action: onGoHome) {
                Label("azkar.completion.home", systemImage: "arrow.left")
            }
            .buttonStyle(PrimaryButtonStyle())
        }
        .padding(.horizontal, Spacing.xxl)
        .padding(.vertical, Spacing.section)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.palette.background)
    }
}

#Preview {
    AzkarCompletionView(section: .morning, onGoHome: {})
}
