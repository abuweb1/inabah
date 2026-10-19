import SwiftUI

/// Урок шага «Учимся читать» (Android `ReadingLessonScreen.kt`): «лицо» шага, блоки урока, кнопка справки,
/// «Готово» / «Проверить себя» и «Читаем слова». Шаг без блоков - «Урок готовится».
struct ReadingLessonView: View {
    let stepNumber: Int

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { course in
                course?.step(stepNumber).map { Text($0.displayTitle) } ?? Text("makharij.reading.title")
            },
            subtitle: { course in
                course?.step(stepNumber).flatMap { $0.subtitle.isEmpty ? nil : Text(verbatim: $0.subtitle) }
            },
            actions: { EmptyView() },
            content: { course, colors, openSheet in
                if let step = course.step(stepNumber), step.hasLesson {
                    LessonContent(course: course, step: step, colors: colors, openSheet: openSheet)
                } else {
                    ReadingLessonSoon()
                }
            }
        )
    }
}

/// Шаг без содержания: «Урок готовится».
struct ReadingLessonSoon: View {
    var body: some View {
        NoticeCard(symbol: "hourglass", title: "makharij.lesson.soon.title", message: "makharij.lesson.soon.message")
            .padding(Spacing.xl)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct LessonContent: View {
    let course: ReadingCourse
    let step: ReadingStep
    let colors: ReadingColors
    let openSheet: LetterSheetOpener

    @Environment(AppRouter.self) private var router
    @Environment(ReadingProgress.self) private var progress
    @Environment(ReadingSettings.self) private var settings

    @State private var info: LessonInfo?
    /// Зерно случайных слогов и формул: тот же набор при перерисовке, новый - при новом заходе в урок.
    @State private var seed = UInt64.random(in: .min ... .max)

    var body: some View {
        let actions = LessonActions(
            openSheet: openSheet,
            openLetterForms: { router.makharijPath.append(.letterForms(step: step.number, letter: $0.letter)) },
            openAlphabet: { router.makharijPath.append(.alphabet(step.number)) },
            openTopic: { router.makharijPath.append(.extra(step: step.number, topic: $0)) },
            openZikr: { router.makharijPath.append(.zikr(step: step.number, zikr: $0)) }
        )
        ScrollView {
            VStack(spacing: Spacing.m) {
                if let example = step.example {
                    LessonHero(example: example)
                }
                ForEach(Array(step.blocks.enumerated()), id: \.offset) { index, block in
                    LessonBlockView(
                        block: block, index: index, step: step, colors: colors, seed: seed,
                        isTransliterationHidden: settings.transliterationHidden, info: $info, actions: actions
                    )
                }
                if let extra = step.extra {
                    Button { router.makharijPath.append(.extra(step: step.number, topic: nil)) } label: {
                        Text(verbatim: extra.title)
                    }
                    .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
                }
                primaryButton
                if step.words != nil {
                    Button("makharij.words.open") { router.makharijPath.append(.words(step.number)) }
                        .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
                }
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.s)
            .padding(.bottom, Spacing.xl)
        }
        .lessonInfoPopover($info, colors: colors)
    }

    @ViewBuilder
    private var primaryButton: some View {
        switch ReadingLogic.primaryAction(for: step) {
        case .done:
            Button("makharij.lesson.done") {
                progress.markLessonDone(step.number)
                if !router.makharijPath.isEmpty { router.makharijPath.removeLast() }
            }
            .buttonStyle(ReadingButtonStyle(colors: colors))
            .padding(.top, Spacing.s)
        case .check:
            Button("makharij.lesson.check") { router.makharijPath.append(.quiz(step.number)) }
                .buttonStyle(ReadingButtonStyle(colors: colors))
                .padding(.top, Spacing.s)
        case .none:
            EmptyView()
        }
    }
}

/// Блок урока по виду (`ReadingBlock`); итоговые зикры - этап «Читаем зикр».
struct LessonBlockView: View {
    let block: ReadingBlock
    let index: Int
    let step: ReadingStep
    let colors: ReadingColors
    let seed: UInt64
    let isTransliterationHidden: Bool
    @Binding var info: LessonInfo?
    let actions: LessonActions

    var body: some View {
        switch block {
        case .text(let text):
            LessonTextBlock(block: text, id: index, colors: colors, info: $info)
        case .marks(let marks):
            LessonMarksBlock(block: marks, colors: colors)
        case .letters(let letters):
            LessonLettersGrid(letters: letters.letters, colors: colors, onTap: actions.openSheet.open)
        case .syllables(let syllables):
            if let sample = syllables.sample {
                LessonSampleSyllables(
                    samples: ReadingLogic.sampleSyllables(letters: syllables.letters, marks: syllables.marks, count: sample, seed: seed),
                    isTransliterationHidden: isTransliterationHidden, colors: colors, actions: actions
                )
            } else {
                SyllablesTable(block: syllables, isTransliterationHidden: isTransliterationHidden, colors: colors, onTap: actions.openSheet.open)
            }
        case .rule(let rule):
            LessonRuleBlock(block: rule, colors: colors, onTap: actions.openSheet.open)
        case .forms(let forms):
            LessonFormsTable(block: forms, colors: colors, onOpen: actions.openLetterForms)
        case .formulas(let formulas):
            if let sample = formulas.sample {
                VStack(spacing: Spacing.m) {
                    LessonFormulasBlock(
                        items: ReadingLogic.sampleFormulas(items: formulas.items, count: sample, seed: seed),
                        columns: formulas.columns, isTransliterationHidden: isTransliterationHidden, colors: colors
                    )
                    Button("makharij.alphabet.formulas.open", action: actions.openAlphabet)
                        .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
                }
            } else {
                LessonFormulasBlock(items: formulas.items, columns: formulas.columns, isTransliterationHidden: isTransliterationHidden, colors: colors)
            }
        case .topic(let topic):
            LessonTopicCard(topic: topic, colors: colors) {
                actions.openTopic(step.topics.firstIndex(of: topic) ?? 0)
            }
        case .zikr(let zikr):
            LessonZikrCard(
                zikr: zikr,
                number: (step.zikrs.firstIndex(of: zikr) ?? 0) + 1,
                colors: colors
            ) {
                actions.openZikr(step.zikrs.firstIndex(of: zikr) ?? 0)
            }
        }
    }
}

/// «Лицо» урока: пример шага на пергаменте, части появляются по очереди справа налево.
private struct LessonHero: View {
    let example: String

    private static let size: Double = 46

    var body: some View {
        ParchmentPanel(bottomCornerRadius: Radius.card) {
            HStack(spacing: 0) {
                ForEach(Array(ReadingLogic.heroParts(example).enumerated()), id: \.offset) { index, part in
                    Spacer(minLength: 0)
                    HeroPart(text: part, index: index, size: Self.size)
                    Spacer(minLength: 0)
                }
            }
            .environment(\.layoutDirection, .rightToLeft)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: example))
    }
}

private struct HeroPart: View {
    let text: String
    let index: Int
    let size: Double

    @Environment(\.theme) private var theme
    @State private var isShown = false

    private enum Layout {
        static let delay = 0.35
        static let startScale: CGFloat = 0.6
        static let rise: CGFloat = 12
    }

    var body: some View {
        Text(verbatim: text)
            .font(.arabic(size: size, bold: true))
            .foregroundStyle(theme.palette.parchmentText)
            .lineLimit(1)
            .fixedSize()
            .opacity(isShown ? 1 : 0)
            .scaleEffect(isShown ? 1 : Layout.startScale)
            .offset(y: isShown ? 0 : Layout.rise)
            .onAppear {
                withAnimation(Motion.collapse.delay(Layout.delay * Double(index))) { isShown = true }
            }
    }
}

#Preview {
    NavigationStack {
        ReadingLessonView(stepNumber: 1)
    }
    .appEnvironment(.preview)
}
