import SwiftUI

/// Лист буквы (Android `LetterSheet.kt`): плитка-пергамент, имя и номер буквы, пояснение (у алифа) и
/// цепочка «звук → название → слоги» справа налево; слоги с ещё не пройденным знаком - пунктиром.
/// Свой слой поверх экрана, не системный `.sheet`: навбар не затемняется, высота - по содержимому.
/// Закрытие - «×», касание по затемнению или свайп вниз. Звук цепочки - этап записей курса.
struct LetterSheet: View {
    static let scrimOpacity = 0.45

    let course: ReadingCourse
    let letter: ReadingLetter
    let stepNumber: Int
    let colors: ReadingColors
    let onClose: () -> Void

    @Environment(ReadingSettings.self) private var settings
    @Environment(\.theme) private var theme

    @State private var dragOffset: CGFloat = 0

    private enum Layout {
        static let cornerRadius: CGFloat = 24
        static let grabber = CGSize(width: 40, height: 5)
        static let tileBox: CGFloat = 84
        static let tileLetter: Double = 48
        static let arabicName: Double = 18
        static let closeButton: CGFloat = 44
        /// Свайп вниз дальше этого - закрыть.
        static let dismissDistance: CGFloat = 96
    }

    var body: some View {
        VStack(spacing: 0) {
            Capsule()
                .fill(theme.palette.track)
                .frame(width: Layout.grabber.width, height: Layout.grabber.height)
                .accessibilityHidden(true)
            header
                .padding(.top, Spacing.s)
            if let note = letter.note {
                Text(verbatim: note)
                    .font(.footnote)
                    .foregroundStyle(theme.palette.onAccentSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, Spacing.m)
            }
            RTLFlowLayout(spacing: Spacing.s, lineSpacing: Spacing.l) {
                ForEach(ReadingLogic.letterChain(course: course, letter: letter, stepNumber: stepNumber), id: \.self) { item in
                    ChainChip(item: item, isTransliterationHidden: settings.transliterationHidden, colors: colors)
                }
            }
            .padding(.top, Spacing.xl)
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.top, Spacing.s)
        .padding(.bottom, Spacing.xl)
        .frame(maxWidth: .infinity)
        .background {
            let shape = UnevenRoundedRectangle(topLeadingRadius: Layout.cornerRadius, topTrailingRadius: Layout.cornerRadius)
            shape
                .fill(colors.sheet)
                .overlay { shape.stroke(colors.tileBorder, lineWidth: Size.hairline) }
                .shadow(
                    color: ShadowToken.floating(theme.palette).color,
                    radius: ShadowToken.floating(theme.palette).radius,
                    y: ShadowToken.floating(theme.palette).y
                )
                .ignoresSafeArea(edges: .bottom)
        }
        // Касания по листу не доходят до затемнения под ним.
        .contentShape(.rect)
        .offset(y: dragOffset)
        .gesture(
            DragGesture()
                .onChanged { dragOffset = max($0.translation.height, 0) }
                .onEnded { value in
                    if value.translation.height > Layout.dismissDistance {
                        onClose()
                    } else {
                        withAnimation(Motion.collapse) { dragOffset = 0 }
                    }
                }
        )
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onClose)
    }

    private var header: some View {
        HStack(spacing: Spacing.l) {
            ParchmentLetterTile(text: letter.letter, size: Layout.tileLetter, box: Layout.tileBox)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(verbatim: letter.name)
                    .font(.title3.bold())
                    .foregroundStyle(theme.palette.onAccent)
                HStack(spacing: Spacing.xs) {
                    Text("makharij.letter.number \(letter.number)")
                        .font(.subheadline)
                    Text(verbatim: letter.arabicName)
                        .font(.arabic(size: Layout.arabicName))
                        .lineLimit(1)
                }
                .foregroundStyle(theme.palette.onAccentSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(theme.palette.onAccentSecondary)
                    .frame(width: Layout.closeButton, height: Layout.closeButton)
                    .contentShape(.rect)
            }
            .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
            .accessibilityLabel(Text("makharij.letter.close"))
        }
    }
}

/// Фишка цепочки: слог (или звук, название) и транскрипция, подпись - на верхней рамке.
private struct ChainChip: View {
    let item: LetterChainItem
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    private enum Layout {
        static let minWidth: CGFloat = 60
        static let arabic: Double = 26
        /// Подпись заходит на рамку: тело фишки ниже на столько.
        static let labelOverlap: CGFloat = 8
        static let lockedOpacity = 0.45
        static let dash: [CGFloat] = [4, 4]
    }

    var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: Spacing.xxxs) {
                Text(verbatim: item.arabic)
                    .font(.arabic(size: Layout.arabic))
                    .foregroundStyle(theme.palette.onAccent)
                    .lineLimit(1)
                    .fixedSize()
                    .environment(\.layoutDirection, .rightToLeft)
                Text(verbatim: item.transliteration)
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
                    .opacity(isTransliterationHidden ? 0 : 1)
            }
            .padding(.horizontal, Spacing.s)
            .padding(.vertical, Spacing.xs)
            .frame(minWidth: Layout.minWidth)
            .background { border }
            .opacity(item.isLearned ? 1 : Layout.lockedOpacity)
            .padding(.top, Layout.labelOverlap)
            Text(verbatim: label)
                .font(.caption2)
                .foregroundStyle(theme.palette.onAccentSecondary)
                .lineLimit(1)
                .padding(.horizontal, Spacing.xs)
                .background(colors.sheet, in: .rect(cornerRadius: Radius.small))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: accessibilityLabel))
    }

    @ViewBuilder
    private var border: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.box)
        if item.isLearned {
            shape.fill(colors.tile).strokeBorder(colors.tileBorder, lineWidth: ReadingMetrics.tileBorder)
        } else {
            shape.strokeBorder(colors.tileBorder, style: StrokeStyle(lineWidth: ReadingMetrics.tileBorder, dash: Layout.dash))
        }
    }

    /// «звук», «название» или название знака слога («фатхьа»).
    private var label: String {
        switch item.kind {
        case .sound: String(localized: "makharij.chain.sound")
        case .name: String(localized: "makharij.chain.name")
        case .syllable: item.markName ?? ""
        }
    }

    /// Транскрипцию VoiceOver читает и при скрытом глазе.
    private var accessibilityLabel: String {
        item.isLearned
            ? "\(label), \(item.transliteration)"
            : String(localized: "makharij.chain.locked \(label) \(item.transliteration)")
    }
}
