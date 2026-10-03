import SwiftUI

/// «مَا شَاءَ اللَّهُ» — показывается, когда прочитаны все азкары раздела.
struct AzkarCompletionView: View {
    let section: AzkarSection
    let onGoHome: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let titleSize: Double = 52
    private static let arabicSize: Double = 24

    var body: some View {
        VStack(spacing: Spacing.l) {
            // Каллиграфия — главный элемент экрана, без значка. Пульсация живёт ровно столько,
            // сколько оверлей на экране (phaseAnimator), и выключается при «Уменьшении движения».
            ArabicText(
                text: "مَا شَاءَ اللَّهُ",
                size: Self.titleSize,
                color: theme.palette.gold,
                bold: true,
                alignment: .center
            )
            .phaseAnimator([1.0, 1.05]) { content, scale in
                content.scaleEffect(reduceMotion ? 1 : scale)
            } animation: { _ in
                .easeInOut(duration: 1.2)
            }
            .accessibilityLabel(Text("azkar.completion.title"))
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
