import SwiftUI

/// «Проверить себя» (Android `ReadingQuizScreen.kt`): заход - случайные вопросы из запаса шага, варианты
/// перемешаны; неверный ответ - красный, тряска, попробовать ещё раз (верный не подсказывается); верный -
/// зелёный, вспышка ✦, объяснение, «Дальше». После последнего вопроса урок отмечается пройденным.
struct ReadingQuizView: View {
    let stepNumber: Int

    /// Одно на заход: тот же заход при перерисовке, новый - при новом открытии проверки.
    @State private var seed = UInt64.random(in: .min ... .max)
    @State private var index = 0

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { _ in Text("makharij.quiz.title") },
            subtitle: { course in
                let total = course?.step(stepNumber).map { min($0.quizCount, $0.quiz.count) } ?? 0
                return index < total ? Text("makharij.quiz.counter \(index + 1) \(total)") : nil
            },
            showsTransliterationToggle: false,
            actions: { EmptyView() },
            content: { course, colors, _ in
                if let step = course.step(stepNumber) {
                    QuizContent(
                        course: course, step: step,
                        round: ReadingLogic.quizRound(course: course, step: step, seed: seed),
                        colors: colors, index: $index
                    )
                } else {
                    ReadingLessonSoon()
                }
            }
        )
    }
}

private struct QuizContent: View {
    let course: ReadingCourse
    let step: ReadingStep
    let round: [QuizItem]
    let colors: ReadingColors
    @Binding var index: Int

    @Environment(AppRouter.self) private var router
    @Environment(ReadingProgress.self) private var progress

    var body: some View {
        if round.isEmpty {
            ReadingLessonSoon()
        } else if index >= round.count {
            if step.words == nil {
                ReadingStepCompletedView(step: step, next: ReadingLogic.nextStepAfterQuiz(course: course, step: step), colors: colors)
            } else {
                QuizFinished(step: step, colors: colors)
            }
        } else {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) {
                        QuizDots(count: round.count, current: index, colors: colors)
                            .id(QuizScroll.top)
                        QuizQuestion(item: round[index], isLast: index == round.count - 1, colors: colors) {
                            if index == round.count - 1 {
                                progress.markLessonDone(step.number)
                                // Шаг без слов пройден проверкой - окно «Шаг пройден» (как Android).
                                if step.words == nil { progress.markStepFinished() }
                            }
                            index += 1
                            proxy.scrollTo(QuizScroll.top, anchor: .top)
                        }
                        // Новый вопрос - новые варианты: состояние, тряска и вспышка не переходят дальше.
                        .id(index)
                    }
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, Spacing.s)
                    .padding(.bottom, Spacing.xl)
                }
            }
        }
    }
}

private enum QuizScroll: Hashable {
    case top
}

/// Точки вопросов: пройденные - зелёные, текущая - капсула цвета раздела.
private struct QuizDots: View {
    let count: Int
    let current: Int
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    private enum Layout {
        static let dot = Size.statusDot
        static let currentScale: CGFloat = 2.5
    }

    var body: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(0..<count, id: \.self) { number in
                Capsule()
                    .fill(number < current ? theme.palette.success : number == current ? colors.tint : theme.palette.track)
                    .frame(width: number == current ? Layout.dot * Layout.currentScale : Layout.dot, height: Layout.dot)
            }
        }
        .animation(Motion.collapse, value: current)
        .accessibilityHidden(true)
    }
}

/// Состояние варианта ответа.
private enum OptionState {
    case open, wrong, correct, other
}

private struct QuizQuestion: View {
    let item: QuizItem
    let isLast: Bool
    let colors: ReadingColors
    let onNext: () -> Void

    @Environment(\.theme) private var theme

    @State private var wrong: Set<Int> = []
    @State private var isSolved = false

    var body: some View {
        VStack(spacing: 0) {
            prompt
                .font(.footnote)
                .foregroundStyle(theme.palette.onAccentSecondary)
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.xxl)
            QuizTile(text: arabic, isWord: isWord)
                .padding(.top, Spacing.m)
            options
                .padding(.top, Spacing.xxl)
            if isSolved || !wrong.isEmpty {
                feedback
                    .padding(.top, Spacing.xl)
            }
            if isSolved {
                Button(isLast ? "makharij.quiz.finish" : "makharij.quiz.next", action: onNext)
                    .buttonStyle(ReadingButtonStyle(colors: colors))
                    .padding(.top, Spacing.xl)
            }
        }
        .sensoryFeedback(.error, trigger: wrong.count)
        .sensoryFeedback(trigger: isSolved) { _, solved in solved ? .success : nil }
    }

    @ViewBuilder
    private var options: some View {
        let buttons = ForEach(Array(item.options.enumerated()), id: \.offset) { number, option in
            QuizOptionButton(option: option, state: state(of: number, option), colors: colors) {
                if option.isCorrect {
                    isSolved = true
                } else {
                    wrong.insert(number)
                }
            }
        }
        if isWord {
            VStack(spacing: Spacing.s) { buttons }
        } else {
            HStack(spacing: Spacing.s) { buttons }
        }
    }

    private func state(of number: Int, _ option: QuizOption) -> OptionState {
        if isSolved, option.isCorrect { return .correct }
        if wrong.contains(number) { return .wrong }
        return isSolved ? .other : .open
    }

    private var feedback: some View {
        VStack(alignment: .leading, spacing: Spacing.xxs) {
            Text(isSolved ? "makharij.quiz.correct" : "makharij.quiz.wrong")
                .font(.subheadline.bold())
                .foregroundStyle(theme.palette.onAccent)
            if isSolved {
                explanation
                    .font(.subheadline)
                    .foregroundStyle(theme.palette.onAccentStrong)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.l)
        .padding(.vertical, Spacing.m)
        .surface(
            isSolved ? theme.palette.successTint : theme.palette.destructive.opacity(QuizOptionButton.wrongFill),
            cornerRadius: Radius.box,
            border: isSolved ? theme.palette.successBorder : theme.palette.destructive
        )
        .accessibilityElement(children: .combine)
    }

    private var isWord: Bool {
        if case .word = item.question { true } else { false }
    }

    private var prompt: Text {
        switch item.question {
        case .word(let word): word.prompt.map { Text(verbatim: $0) } ?? Text("makharij.quiz.word")
        case .syllable, .closedSyllable: Text("makharij.quiz.syllable")
        case .letterName, .letterInForm: Text("makharij.quiz.letter")
        }
    }

    private var arabic: String {
        switch item.question {
        case .syllable(let q): q.letter.syllable(q.mark)
        case .closedSyllable(let q): q.arabic
        case .letterName(let letter): letter.letter
        case .letterInForm(let q): q.letter.form(q.form)
        case .word(let q): q.arabic
        }
    }

    private var explanation: Text {
        switch item.question {
        case .syllable(let q):
            Text("makharij.quiz.explain.syllable \(q.mark.where.capitalizedFirst) \(q.mark.name) \(q.mark.sound)")
        case .closedSyllable(let q):
            Text("makharij.quiz.explain.closed \(q.letter.name) \(q.transliteration(vowel: q.vowel.id))")
        case .letterName(let letter):
            Text("makharij.quiz.explain.letter \(letter.name) \(letter.sound)")
        case .letterInForm(let q):
            Text("makharij.quiz.explain.form \(q.letter.name) \(String(localized: q.form.place))")
        case .word(let q):
            q.explanation.map { Text(verbatim: $0) } ?? Text("makharij.quiz.explain.word \(q.options[q.answer])")
        }
    }
}

/// Буква, слог или слово вопроса на пергаменте: квадрат 180 или во всю ширину у слова.
private struct QuizTile: View {
    let text: String
    let isWord: Bool

    @Environment(\.theme) private var theme

    private enum Layout {
        static let box: CGFloat = 180
        static let letter: Double = 72
        static let word: Double = 52
    }

    var body: some View {
        Text(verbatim: text)
            .font(.arabic(size: isWord ? Layout.word : Layout.letter, bold: true))
            .foregroundStyle(theme.palette.parchmentText)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .environment(\.layoutDirection, .rightToLeft)
            .frame(maxWidth: isWord ? .infinity : Layout.box)
            .frame(width: isWord ? nil : Layout.box, height: Layout.box)
            .background {
                RoundedRectangle(cornerRadius: Radius.panel).fill(theme.gradients.parchment.linear)
            }
            .overlay {
                RoundedRectangle(cornerRadius: Radius.panel)
                    .strokeBorder(theme.palette.parchmentAccent, lineWidth: Size.parchmentBorder)
            }
            .accessibilityLabel(Text(verbatim: text))
    }
}

private struct QuizOptionButton: View {
    static let wrongFill = 0.22

    let option: QuizOption
    let state: OptionState
    let colors: ReadingColors
    let action: () -> Void

    @Environment(\.theme) private var theme

    private enum Layout {
        static let minHeight: CGFloat = 60
        static let correctFill = 0.25
        static let otherOpacity = 0.3
        static let minScale: CGFloat = 0.6
    }

    var body: some View {
        Button(action: action) {
            Text(verbatim: option.text)
                .font(.title3.weight(.semibold))
                .foregroundStyle(theme.palette.onAccent)
                .lineLimit(1)
                .minimumScaleFactor(Layout.minScale)
                .padding(.horizontal, Spacing.xs)
                .frame(maxWidth: .infinity, minHeight: Layout.minHeight)
                .background {
                    RoundedRectangle(cornerRadius: Radius.card)
                        .fill(fill)
                        .strokeBorder(border, lineWidth: ReadingMetrics.tileBorder)
                }
                .overlay {
                    if state == .correct {
                        SparkleBurst(color: theme.palette.success)
                    }
                }
                .contentShape(.rect(cornerRadius: Radius.card))
                .opacity(state == .other ? Layout.otherOpacity : 1)
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.wideScale))
        .disabled(state != .open)
        .shakeOnChange(of: state == .wrong)
        .popOnChange(of: state == .correct)
        .accessibilityValue(accessibilityValue)
    }

    private var fill: Color {
        switch state {
        case .open, .other: colors.tile
        case .wrong: theme.palette.destructive.opacity(Self.wrongFill)
        case .correct: theme.palette.success.opacity(Layout.correctFill)
        }
    }

    private var border: Color {
        switch state {
        case .open, .other: colors.tileBorder
        case .wrong: theme.palette.destructive
        case .correct: theme.palette.success
        }
    }

    private var accessibilityValue: Text {
        switch state {
        case .correct: Text("makharij.quiz.option.correct")
        case .wrong: Text("makharij.quiz.option.wrong")
        case .open, .other: Text(verbatim: "")
        }
    }
}

/// Проверка окончена, слова ещё не прочитаны: «Шаг пройден» и «Читаем слова».
private struct QuizFinished: View {
    let step: ReadingStep
    let colors: ReadingColors

    @Environment(AppRouter.self) private var router

    var body: some View {
        VStack(spacing: Spacing.m) {
            NoticeCard(symbol: "checkmark.circle", title: "makharij.quiz.done.title", message: "makharij.quiz.done.words")
            Button("makharij.words.open") { router.open(MakharijRoute.words(step.number)) }
                .buttonStyle(ReadingButtonStyle(colors: colors))
            Button("makharij.quiz.toSteps") { router.open(MakharijRoute.steps) }
                .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.bottom, Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private extension String {
    /// «над буквой» → «Над буквой» (начало объяснения).
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}
