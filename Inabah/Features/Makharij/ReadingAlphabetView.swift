import SwiftUI

/// «Весь алфавит» шага (Android `ReadingAlphabetScreen.kt`): все слоги таблицы карточками по 4 буквы или
/// все закрытые слоги (`ReadingBlock.ClosedSyllables`); слог открывает лист буквы. «Послушать алфавит» (▶ в
/// навбаре) - слоги по порядку, панель поверх таблицы, звучащий слог подсвечен и не уходит с экрана.
struct ReadingAlphabetView: View {
    let stepNumber: Int

    @Environment(ReadingStore.self) private var store
    @State private var playback: AlphabetPlayback?

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { _ in Text("makharij.alphabet.table.title") },
            subtitle: { course in
                let isClosed = course?.step(stepNumber).flatMap(AlphabetTable.init(step:))?.isClosed ?? false
                return Text(isClosed ? "makharij.alphabet.formulas.subtitle" : "makharij.alphabet.table.subtitle")
            },
            intro: .alphabet(stepNumber),
            actions: {
                if let table = store.course.value?.step(stepNumber).flatMap(AlphabetTable.init(step:)), !table.order.isEmpty {
                    ReadingHeaderButton(
                        symbol: playback == nil ? "play.fill" : "stop.fill",
                        label: playback == nil ? "makharij.alphabet.play" : "makharij.alphabet.stop"
                    ) {
                        playback = playback == nil ? AlphabetPlayback(count: table.order.count) : nil
                    }
                }
            },
            content: { course, colors, openSheet in
                if let table = course.step(stepNumber).flatMap(AlphabetTable.init(step:)) {
                    AlphabetTableContent(table: table, colors: colors, openSheet: openSheet, playback: $playback)
                }
            }
        )
    }
}

/// Таблица «Весь алфавит» шага: слоги (шаги 2, 8) или закрытые слоги (шаг «Сукун») и их порядок звучания.
private struct AlphabetTable {
    let syllables: ReadingBlock.Syllables?
    let closed: ReadingBlock.ClosedSyllables?
    let order: [TableSyllable]

    var isClosed: Bool { closed != nil }

    init?(step: ReadingStep) {
        let blocks = step.blocks
        if let block = blocks.lazy.compactMap({ if case .syllables(let b) = $0 { b } else { nil } }).first {
            syllables = block
            closed = nil
            order = ReadingLogic.syllablesPlayOrder(block)
        } else if let block = blocks.lazy.compactMap({ if case .closedSyllables(let b) = $0 { b } else { nil } }).first {
            syllables = nil
            closed = block
            order = ReadingLogic.closedSyllablesPlayOrder(block)
        } else {
            return nil
        }
    }
}

private struct AlphabetTableContent: View {
    let table: AlphabetTable
    let colors: ReadingColors
    let openSheet: LetterSheetOpener
    @Binding var playback: AlphabetPlayback?

    @Environment(ReadingSettings.self) private var settings
    @State private var cellFrames = TableCellFrames()

    private enum Layout {
        /// Панель закрыла бы первый ряд (выше начала не прокрутить) - таблица съезжает под неё: кнопка панели
        /// и её поля (как Android `Size.visibleTapTarget + Spacing.s × 3`).
        static let shift: CGFloat = Size.minTapTarget + Spacing.s * 3
        /// Звучащий слог ближе к краю окна - прокрутить к нему (Android `keepInViewWhen`, 112).
        static let keepInView: CGFloat = 112
    }

    var body: some View {
        let playingID = playback.flatMap { table.order.indices.contains($0.index) ? table.order[$0.index].id : nil }
        let opener = LetterSheetOpener { letter in
            // Лист буквы звучит сам - алфавит на паузу.
            playback?.pause()
            openSheet(letter)
        }
        ScrollViewReader { proxy in
            ScrollView {
                tableView(playingID: playingID, onTap: opener.open)
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, Spacing.s)
                    // Снизу всегда запас на сдвиг под панель - конец таблицы досягаем и при игре.
                    .padding(.bottom, Spacing.xl + Layout.shift)
                    .offset(y: playback == nil ? 0 : Layout.shift)
                    .animation(Motion.collapse, value: playback == nil)
            }
            .coordinateSpace(.named(TableCellFrames.space))
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { cellFrames.viewport = $0 }
            .onChange(of: playingID) { _, id in
                guard let id, let frame = cellFrames.frames[id] else { return }
                if frame.minY < Layout.keepInView || frame.maxY > cellFrames.viewport - Layout.keepInView {
                    withAnimation(Motion.collapse) { proxy.scrollTo(id, anchor: .center) }
                }
            }
        }
        .alphabetPlayer(
            $playback, items: table.order.map(AlphabetItem.syllable), colors: colors,
            previousLabel: "makharij.alphabet.previousSyllable", nextLabel: "makharij.alphabet.nextSyllable"
        )
    }

    @ViewBuilder
    private func tableView(playingID: String?, onTap: @escaping (ReadingLetter) -> Void) -> some View {
        if let syllables = table.syllables {
            SyllablesTable(
                block: syllables, isTransliterationHidden: settings.transliterationHidden, colors: colors,
                playingID: playingID, cellFrames: cellFrames, onTap: onTap
            )
        } else if let closed = table.closed {
            ClosedSyllablesTable(
                block: closed, isTransliterationHidden: settings.transliterationHidden, colors: colors,
                playingID: playingID, cellFrames: cellFrames, onTap: onTap
            )
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
            intro: .extra(stepNumber, topic: topic),
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
