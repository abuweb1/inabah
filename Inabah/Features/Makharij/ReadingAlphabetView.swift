import SwiftUI

/// «Весь алфавит» шага (Android `ReadingAlphabetScreen.kt`): все слоги таблицы карточками по 4 буквы или
/// все закрытые слоги (формулы с `sample`); слог открывает лист буквы.
struct ReadingAlphabetView: View {
    let stepNumber: Int

    @Environment(ReadingSettings.self) private var settings

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { _ in Text("makharij.alphabet.table.title") },
            subtitle: { course in
                let hasSyllables = course?.step(stepNumber)?.blocks.contains { if case .syllables = $0 { true } else { false } } ?? true
                return Text(hasSyllables ? "makharij.alphabet.table.subtitle" : "makharij.alphabet.formulas.subtitle")
            },
            actions: { EmptyView() },
            content: { course, colors, openSheet in
                ScrollView {
                    content(step: course.step(stepNumber), colors: colors, openSheet: openSheet)
                        .padding(.horizontal, Spacing.xl)
                        .padding(.top, Spacing.s)
                        .padding(.bottom, Spacing.xl)
                }
            }
        )
    }

    @ViewBuilder
    private func content(step: ReadingStep?, colors: ReadingColors, openSheet: LetterSheetOpener) -> some View {
        let blocks = step?.blocks ?? []
        if let syllables = blocks.lazy.compactMap({ if case .syllables(let b) = $0 { b } else { nil } }).first {
            SyllablesTable(block: syllables, isTransliterationHidden: settings.transliterationHidden, colors: colors, onTap: openSheet.open)
        } else if let formulas = blocks.lazy.compactMap({ if case .formulas(let b) = $0, b.sample != nil { b } else { nil } }).first {
            FormulasCards(block: formulas, isTransliterationHidden: settings.transliterationHidden, colors: colors)
        }
    }
}

/// Справка к уроку или тема «Особых случаев» (Android `ReadingExtraScreen.kt`): текст, правила и все
/// формулы; буквы правил открывают лист буквы. Глаза нет, транскрипция - по настройке урока.
struct ReadingExtraView: View {
    let stepNumber: Int
    let topic: Int?

    @Environment(ReadingSettings.self) private var settings

    @State private var info: LessonInfo?

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { course in Text(verbatim: course?.step(stepNumber)?.extra(topic: topic)?.title ?? "") },
            subtitle: { course in course?.step(stepNumber).map { Text($0.displayTitle) } },
            showsTransliterationToggle: false,
            actions: { EmptyView() },
            content: { course, colors, openSheet in
                ScrollView {
                    VStack(spacing: Spacing.m) {
                        let blocks = course.step(stepNumber)?.extra(topic: topic)?.blocks ?? []
                        ForEach(Array(blocks.enumerated()), id: \.offset) { index, block in
                            switch block {
                            case .text(let text):
                                LessonTextBlock(block: text, id: index, colors: colors, info: $info)
                            case .rule(let rule):
                                LessonRuleBlock(block: rule, colors: colors, onTap: openSheet.open)
                            case .formulas(let formulas):
                                LessonFormulasBlock(
                                    items: formulas.items, columns: formulas.columns,
                                    isTransliterationHidden: settings.transliterationHidden, colors: colors
                                )
                            default:
                                EmptyView()
                            }
                        }
                    }
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, Spacing.s)
                    .padding(.bottom, Spacing.xl)
                }
                .lessonInfoPopover($info, colors: colors)
            }
        )
    }
}
