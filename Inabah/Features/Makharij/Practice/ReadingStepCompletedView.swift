import SwiftUI

/// Шаг пройден (Android `StepCompletedOverlay`): «مَا شَاءَ اللَّهُ» в двух вспышках ✦, что пройдено, аят 20:114,
/// «Следующий шаг» и «К списку шагов». Слой поверх экрана под навбаром; касания вниз не проходят.
/// Общий у проверки, «Читаем слова» и итога «Читаем зикр».
struct ReadingStepCompletedView: View {
    let step: ReadingStep
    let next: ReadingStep?
    let colors: ReadingColors

    @Environment(AppRouter.self) private var router
    @Environment(\.theme) private var theme

    private enum Layout {
        static let title: Double = 52
        static let titleBreath: CGFloat = 1.05
        static let burst: CGFloat = 220
        /// Вторая волна - шире и дольше.
        static let secondWave: CGFloat = 1.4
        static let burstStars = 12
        static let burstDuration = 1.2
        static let secondWaveDuration = 1.8
    }

    private static let verse = FeaturedVerse(
        arabic: "وَقُل رَّبِّ زِدْنِي عِلْمًا",
        translation: "makharij.done.verse.translation",
        reference: "makharij.done.verse.reference"
    )

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: Spacing.l) {
                    ZStack {
                        SparkleBurst(color: theme.palette.gold, count: Layout.burstStars, duration: Layout.burstDuration)
                            .frame(width: Layout.burst, height: Layout.burst)
                        SparkleBurst(color: theme.palette.parchmentLight, count: Layout.burstStars, duration: Layout.secondWaveDuration)
                            .frame(width: Layout.burst * Layout.secondWave, height: Layout.burst * Layout.secondWave)
                        Text(verbatim: "مَا شَاءَ اللَّهُ")
                            .font(.arabic(size: Layout.title, bold: true))
                            .foregroundStyle(theme.palette.gold)
                            .fixedSize()
                            .breathing(scale: Layout.titleBreath)
                            .accessibilityLabel(Text("azkar.completion.title"))
                            .accessibilityAddTraits(.isHeader)
                    }
                    Text(step.isFinal ? "makharij.done.final \(step.title)" : "makharij.done.step \(step.number) \(step.title)")
                        .font(.title3.bold())
                        .foregroundStyle(theme.palette.onAccent)
                        .multilineTextAlignment(.center)
                    if let words = step.words {
                        Text("makharij.done.words \(words.items.count) \(Text("zikr.repetitions \(words.repeat)"))")
                            .font(.subheadline)
                            .foregroundStyle(theme.palette.onAccentSecondary)
                            .multilineTextAlignment(.center)
                    }
                    FeaturedVerseBox(verse: Self.verse)
                    if let next {
                        Button {
                            router.open(MakharijRoute.lesson(next.number))
                        } label: {
                            Text("makharij.quiz.nextStep \(next.title)")
                        }
                        .buttonStyle(ReadingButtonStyle(colors: colors))
                    }
                    Button("makharij.quiz.toSteps") { router.open(MakharijRoute.steps) }
                        .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
                }
                .padding(.horizontal, Spacing.xxl)
                .padding(.vertical, Spacing.section)
                .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
        }
        .background { theme.gradients.makharijBackground.linear.ignoresSafeArea() }
        .contentShape(.rect)
        .accessibilityAddTraits(.isModal)
    }
}
