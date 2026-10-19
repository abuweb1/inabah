import SwiftUI

/// Строка шага: кружок с номером (✓ у пройденного, ✦ у итога), название и подзаголовок, справа - пример
/// шага, замок или «отщёлкивание» замка (Android `StepRow`, `ReadingStepsScreen.kt`).
struct ReadingStepRow: View {
    static let badgeSize: CGFloat = 40

    let step: ReadingStep
    let state: ReadingStepState
    let isJustUnlocked: Bool
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    @ScaledMetric(relativeTo: .subheadline) private var badgeSize: CGFloat = Self.badgeSize
    @ScaledMetric(relativeTo: .subheadline) private var numberSize: CGFloat = 20
    @ScaledMetric(relativeTo: .subheadline) private var sparkleSize: CGFloat = 18
    @ScaledMetric(relativeTo: .subheadline) private var checkSize: CGFloat = 17
    @ScaledMetric(relativeTo: .subheadline) private var lockSize: CGFloat = 16

    private enum Layout {
        /// Цифры Scheherazade сидят ниже середины строки - поднять на эту долю кегля.
        static let numberLift: CGFloat = 0.16
        static let exampleSize: Double = 22
        static let subtitleLines = 2
    }

    var body: some View {
        HStack(spacing: Spacing.m) {
            badge
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Text(verbatim: step.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(theme.palette.onAccent)
                Text(verbatim: step.subtitle)
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
                    .lineLimit(Layout.subtitleLines)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(Spacing.m)
        .surface(
            state == .current ? colors.highlight : colors.tile,
            cornerRadius: Radius.box,
            border: state == .current ? colors.highlightBorder : colors.tileBorder
        )
        .contentShape(.rect(cornerRadius: Radius.box))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: accessibilityLabel))
        .accessibilityValue(state.accessibilityValue.map { Text($0) } ?? Text(verbatim: ""))
    }

    private var badge: some View {
        ZStack {
            if state == .done {
                Circle().fill(theme.palette.success)
                Image(systemName: "checkmark")
                    .font(.system(size: checkSize, weight: .bold))
                    .foregroundStyle(theme.palette.onAccent)
            } else {
                Circle()
                    .fill(colors.sheet)
                    .strokeBorder(colors.tint, lineWidth: Size.hairline)
                if step.isFinal {
                    SparkleShape()
                        .fill(colors.tint)
                        .frame(width: sparkleSize, height: sparkleSize)
                } else {
                    Text(verbatim: step.number.formatted(.arabicIndic))
                        .font(.arabic(size: numberSize))
                        .foregroundStyle(colors.tint)
                        .lineLimit(1)
                        .offset(y: -numberSize * Layout.numberLift)
                }
            }
        }
        .frame(width: badgeSize, height: badgeSize)
        .breathing(isActive: state == .current)
    }

    @ViewBuilder
    private var trailing: some View {
        if state == .locked {
            Image(systemName: "lock.fill")
                .font(.system(size: lockSize))
                .foregroundStyle(theme.palette.onAccentSecondary)
        } else if isJustUnlocked {
            UnlockingExample(example: step.example, lockSize: lockSize, exampleSize: Layout.exampleSize)
        } else if let example = step.example {
            ExampleText(example: example, size: Layout.exampleSize)
        }
    }

    private var accessibilityLabel: String {
        let title = String(localized: step.displayTitle)
        return step.subtitle.isEmpty ? title : "\(title), \(step.subtitle)"
    }
}

/// Пример шага справа: арабский, пергаментно-золотой, в одну строку.
private struct ExampleText: View {
    let example: String
    let size: Double

    @Environment(\.theme) private var theme

    var body: some View {
        Text(verbatim: example)
            .font(.arabic(size: size))
            .foregroundStyle(theme.palette.parchmentLight)
            .lineLimit(1)
            .environment(\.layoutDirection, .rightToLeft)
    }
}

/// Открытие шага: замок поворачивается, растёт и гаснет, пример проявляется, вокруг - вспышка ✦
/// (Android `UnlockingExample`). Один раз - пока шаг не отмечен как увиденный (`unlockSeen`).
private struct UnlockingExample: View {
    let example: String?
    let lockSize: CGFloat
    let exampleSize: Double

    @Environment(\.theme) private var theme
    @State private var progress = 0.0
    @State private var showsBurst = false

    private enum Layout {
        /// Вспышка - втрое шире замка.
        static let burstScale: CGFloat = 3
        /// Вспышка начинается вместе с замком - после паузы `Motion.unlock`.
        static let burstDelay = Duration.milliseconds(400)
    }

    var body: some View {
        UnlockFrame(progress: progress, example: example, lockSize: lockSize, exampleSize: exampleSize)
            .overlay {
                if showsBurst {
                    SparkleBurst(color: theme.palette.gold)
                        .frame(width: lockSize * Layout.burstScale, height: lockSize * Layout.burstScale)
                }
            }
            .task {
                withAnimation(Motion.unlock) { progress = 1 }
                try? await Task.sleep(for: Layout.burstDelay)
                guard !Task.isCancelled else { return }
                showsBurst = true
            }
    }
}

/// Кадр открытия при доле `progress`: замок гаснет в первой половине, пример проявляется во второй.
private struct UnlockFrame: View, Animatable {
    var progress: Double
    let example: String?
    let lockSize: CGFloat
    let exampleSize: Double

    @Environment(\.theme) private var theme

    private enum Layout {
        static let lockTurn = -30.0
    }

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        ZStack {
            if let example {
                ExampleText(example: example, size: exampleSize)
                    .opacity(min(max((progress - 0.5) * 2, 0), 1))
            }
            Image(systemName: "lock.fill")
                .font(.system(size: lockSize))
                .foregroundStyle(theme.palette.gold)
                .opacity(min(max(1 - 2 * progress, 0), 1))
                .rotationEffect(.degrees(Layout.lockTurn * progress))
                .scaleEffect(1 + progress)
        }
    }
}
