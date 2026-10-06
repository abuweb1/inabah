import SwiftUI

/// «مَا شَاءَ اللَّهُ» — показывается, когда прочитаны все азкары раздела.
struct AzkarCompletionView: View {
    let section: AzkarSection
    let onGoHome: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let titleSize: Double = 52
    private static let arabicSize: Double = 24
    private static let reminderIconSize: CGFloat = 20
    private static let pulsePhases: [CGFloat] = [1, 1.05]
    private static let restingPhase: [CGFloat] = [1]

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
            // При «Уменьшении движения» одна фаза — анимация не запускается вовсе.
            .phaseAnimator(reduceMotion ? Self.restingPhase : Self.pulsePhases) { content, scale in
                content.scaleEffect(scale)
            } animation: { _ in
                Motion.pulse
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
                lineWidth: Size.parchmentBorder
            )
            .accessibilityLabel(Text("azkar.completion.hamd"))

            Text(section.completionMessage)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(theme.palette.textSecondary)

            // «Сделать напоминание» — над «На главную» (решение пользователя 2026-10-06).
            ShareButton(title: "reminder.action", showsTitle: true, iconSize: Self.reminderIconSize) {
                section.reminderText
            }
            .buttonStyle(PrimaryButtonStyle())

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
