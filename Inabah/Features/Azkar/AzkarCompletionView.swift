import SwiftUI

/// «مَا شَاءَ اللَّهُ» — показывается, когда прочитаны все азкары раздела.
///
/// Под сообщением (решения пользователя 2026-10-11, Android `CompletionOverlay`, снимок `13f`) —
/// напоминание о втором разделе, если он за этот день не прочитан, серия дней и аят аль-Ахзаб 33:41–42;
/// кнопки «← На главную» и «Сделать напоминание» — в один ряд. Не помещается — прокручивается.
/// День и серию окно берёт само: экран списка не перерисовывается от истории.
struct AzkarCompletionView: View {
    let section: AzkarSection
    let onGoHome: () -> Void

    @Environment(AzkarStore.self) private var store
    @Environment(AzkarHistory.self) private var history
    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let titleSize: Double = 52
    private static let arabicSize: Double = 24
    private static let reminderIconSize: CGFloat = 17
    private static let pulsePhases: [CGFloat] = [1, 1.05]
    private static let restingPhase: [CGFloat] = [1]

    /// Аль-Ахзаб 33:41–42 — огласовка сверена пользователем.
    private static let verse = FeaturedVerse(
        arabic: "يَا أَيُّهَا الَّذِينَ آمَنُوا اذْكُرُوا اللَّهَ ذِكْرًا كَثِيرًا وَسَبِّحُوهُ بُكْرَةً وَأَصِيلًا",
        translation: "azkar.completion.verse.translation",
        reference: "azkar.completion.verse.reference"
    )

    var body: some View {
        let today = store.streakDay
        let reminder = section.completionReminder(day: today.flatMap { history.days[$0] })
        let streak = today.map { AzkarStreak(days: history.days, today: $0).current } ?? 0
        GeometryReader { viewport in
            ScrollView {
                content(reminder: reminder, streak: streak)
                    // Натуральная высота: иначе высота экрана делится между строками и аят обрезается «…».
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal, Spacing.xxl)
                    .padding(.vertical, Spacing.section)
                    // По центру, пока помещается; выше экрана — прокрутка.
                    .frame(maxWidth: .infinity, minHeight: viewport.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .background(theme.palette.background)
    }

    private func content(reminder: AzkarSection?, streak: Int) -> some View {
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
                color: theme.palette.parchmentText,
                alignment: .center
            )
            .padding(.vertical, Spacing.m)
            .padding(.horizontal, Spacing.xlPlus)
            .surface(
                theme.gradients.parchment.linear,
                cornerRadius: Radius.control,
                border: theme.palette.parchmentAccent,
                lineWidth: Size.parchmentBorder
            )
            .accessibilityLabel(Text("azkar.completion.hamd"))

            Text(section.completionMessage)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(theme.palette.textSecondary)

            if let reminder {
                CompletionLine(text: reminder.completionReminderMessage) {
                    Image(systemName: reminder.symbolName)
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(reminder.iconColor(in: theme))
                }
            }
            if streak > 0 {
                CompletionLine(text: AzkarStreakCapsule.text(current: streak)) {
                    Image("tabIconAzkar")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(theme.palette.gold)
                }
            }

            FeaturedVerseBox(verse: Self.verse)

            // В ряд (решение пользователя 2026-10-11): слева «На главную», справа «Сделать напоминание» —
            // прочитал сам, напомни другим (2026-10-06): тот же текст, что у кнопки в шапке.
            HStack(spacing: Spacing.m) {
                Button(action: onGoHome) {
                    Label("azkar.completion.home", systemImage: "arrow.left")
                }
                ShareButton(title: "reminder.action", showsTitle: true, iconSize: Self.reminderIconSize) {
                    section.reminderText
                }
            }
            .buttonStyle(PrimaryButtonStyle(compact: true))
            // Одна высота, даже если текст соседней перенёсся на вторую строку.
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Строка окна со значком слева: напоминание о втором разделе, серия дней. Для VoiceOver — одна фраза.
private struct CompletionLine<Icon: View>: View {
    let text: LocalizedStringResource
    @ViewBuilder let icon: Icon

    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .subheadline) private var iconSize: CGFloat = 18

    var body: some View {
        HStack(spacing: Spacing.s) {
            icon
                .frame(width: iconSize, height: iconSize)
                .accessibilityHidden(true)
            Text(text)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundStyle(theme.palette.onAccent)
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    AzkarCompletionView(section: .morning, onGoHome: {})
        .appEnvironment(.preview)
}
