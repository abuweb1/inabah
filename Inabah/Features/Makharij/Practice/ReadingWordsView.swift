import SwiftUI
import UIKit

/// «Читаем слова» (Android `ReadingWordsScreen.kt`): каждое слово шага прочитать вслух `repeat` раз -
/// касание по плитке; плитка наливается цветом раздела, прочитанная - зелёная с ✓. Слова - страницами
/// (короткие по 3 в ряд, длинные по 2, словосочетания во всю ширину; у пар - слово и словосочетание рядом),
/// листание - свайпом и кнопками внизу. Транскрипция - на первой странице (глаз), дальше - лампочкой.
struct ReadingWordsView: View {
    let stepNumber: Int

    @State private var page: Int? = 0
    @State private var hintRequest = 0
    @State private var hintPage: Int?

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { _ in Text("makharij.words.title") },
            subtitle: { _ in pageCount.map { Text("makharij.words.group \((page ?? 0) + 1) \($0)") } },
            showsTransliterationToggle: (page ?? 0) == 0,
            actions: {
                if (page ?? 0) > 0 {
                    ReadingHeaderButton(symbol: "lightbulb", label: "makharij.words.hint") {
                        hintPage = page
                        hintRequest += 1
                    }
                }
            },
            content: { course, colors, _ in
                if let step = course.step(stepNumber), let words = step.words, !words.items.isEmpty {
                    WordsContent(
                        course: course, step: step, words: words, colors: colors,
                        page: $page, hintPage: hintPage, pageCount: $pageCountState
                    )
                } else {
                    ReadingLessonSoon()
                }
            }
        )
        // Повторное нажатие на лампочку - снова 0,7 с; другая страница - подсказка гаснет сама.
        .task(id: hintRequest) {
            guard hintRequest > 0 else { return }
            try? await Task.sleep(for: Motion.transliterationHint)
            guard !Task.isCancelled else { return }
            withAnimation { hintPage = nil }
        }
    }

    @State private var pageCountState: Int?

    /// Страниц больше одной - в навбаре «группа N из M».
    private var pageCount: Int? { pageCountState.flatMap { $0 > 0 ? $0 : nil } }
}

private struct WordsContent: View {
    let course: ReadingCourse
    let step: ReadingStep
    let words: ReadingWords
    let colors: ReadingColors
    @Binding var page: Int?
    let hintPage: Int?
    @Binding var pageCount: Int?

    @Environment(ReadingProgress.self) private var progress
    @Environment(ReadingSettings.self) private var settings
    @Environment(AppRouter.self) private var router

    @State private var pages: [[WordRow]] = []
    @State private var info: LessonInfo?
    @State private var zoom: WordZoom?
    @State private var wasDone: Bool?
    @State private var isCelebrating = false

    private static let space = "readingWords"
    private static let readAloudInfo = 0

    private enum Layout {
        /// Поля экрана и пергамента: ширина ряда плиток.
        static let rowInset: CGFloat = Spacing.xl * 2 + Spacing.xxl * 2
    }

    var body: some View {
        let counts = progress.wordCounts[step.number] ?? []
        let completed = ReadingLogic.completedSteps(course: course, lessonsDone: progress.lessonsDone, wordCounts: progress.wordCounts)
        let isStepDone = completed.contains(step.number)
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: Spacing.m) {
                    WordsProgress(step: step, words: words, counts: counts, colors: colors)
                        .padding(.horizontal, Spacing.xl)
                    ReadAloudNote(colors: colors, info: $info, id: Self.readAloudInfo)
                        .padding(.horizontal, Spacing.xl)
                    pager(counts: counts)
                    if ReadingLogic.wordsDone(step: step, counts: counts) {
                        WordsFinished(
                            step: step,
                            isLessonDone: progress.lessonsDone.contains(step.number),
                            next: ReadingLogic.nextStepAfterWords(course: course, step: step, completed: completed, allStepsOpen: settings.allStepsOpen),
                            colors: colors
                        )
                        .padding(.horizontal, Spacing.xl)
                    }
                }
                .padding(.top, Spacing.s)
                .padding(.bottom, Spacing.xl)
            }
            .lessonInfoPopover($info, colors: colors) { _ in WordsHadithCard() }
            GroupNavigation(page: $page, count: pages.count, colors: colors)
                .padding(.horizontal, Spacing.xl)
                .padding(.top, Spacing.s)
                .padding(.bottom, Spacing.m)
        }
        .coordinateSpace(.named(Self.space))
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width in
            pages = Self.pages(for: words, step: step, rowWidth: width - Layout.rowInset)
            pageCount = pages.count > 1 ? pages.count : nil
        }
        .overlay { zoomLayer(counts: counts) }
        .overlay {
            if isCelebrating {
                ReadingStepCompletedView(
                    step: step,
                    next: ReadingLogic.nextStepAfterWords(course: course, step: step, completed: completed, allStepsOpen: settings.allStepsOpen),
                    colors: colors
                )
                .transition(.opacity)
            }
        }
        .animation(Motion.overlay, value: isCelebrating)
        // Шаг стал пройденным на этом экране - праздник; при повторном заходе - нет.
        .onAppear { if wasDone == nil { wasDone = isStepDone } }
        .onChange(of: isStepDone) { _, done in
            guard done, wasDone == false else { return }
            zoom = nil
            isCelebrating = true
        }
    }

    private func pager(counts: [Int]) -> some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 0) {
                    ForEach(Array(pages.enumerated()), id: \.offset) { number, rows in
                        ParchmentPanel(bottomCornerRadius: Radius.card) {
                            WordGrid(
                                rows: rows,
                                words: words,
                                counts: counts,
                                showsTransliteration: (number == 0 && !settings.transliterationHidden) || hintPage == number,
                                zoomedIndex: zoom?.index,
                                colors: colors,
                                coordinateSpace: Self.space,
                                onRead: read,
                                onZoom: { index, frame in zoom = WordZoom(index: index, page: number, source: frame) }
                            )
                        }
                        .padding(.horizontal, Spacing.xl)
                        .containerRelativeFrame(.horizontal)
                        .id(number)
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $page)
            .scrollIndicators(.hidden)
            .onChange(of: page) { _, number in
                // Кнопки «Назад» / «Дальше» листают с анимацией.
                if let number { withAnimation(Motion.collapse) { proxy.scrollTo(number) } }
            }
        }
    }

    @ViewBuilder
    private func zoomLayer(counts: [Int]) -> some View {
        if let zoom, words.items.indices.contains(zoom.index) {
            let word = words.items[zoom.index]
            TileZoomOverlay(
                source: zoom.source,
                sourceTextSize: WordTile.wordSize,
                zoomedTextSize: word.isPhrase ? WordTile.zoomedPhraseSize : WordTile.zoomedWordSize,
                onDismissed: { self.zoom = nil },
                onTileTap: { read(zoom.index) }
            ) {
                WordTile(
                    word: word,
                    count: counts.indices.contains(zoom.index) ? counts[zoom.index] : 0,
                    repeat: words.repeat,
                    showsTransliteration: (zoom.page == 0 && !settings.transliterationHidden) || hintPage == zoom.page,
                    isZoomed: true,
                    colors: colors
                )
            }
        }
    }

    private func read(_ index: Int) {
        progress.readWord(step: step.number, index: index, total: words.items.count, repeat: words.repeat)
    }

    /// Страницы по ширине ряда: столбцы слова - по его ширине шрифтом плитки (как Android).
    private static func pages(for words: ReadingWords, step: ReadingStep, rowWidth: CGFloat) -> [[WordRow]] {
        let seed = UInt64(step.number)
        if words.pairs {
            return ReadingLogic.pairPages(words: words.items, rowsPerPage: words.rowsPerPage, seed: seed)
        }
        let font = WordTile.measureFont
        return ReadingLogic.wordPages(
            words: words.items,
            columnsOf: { word in
                let width = (word.arabic as NSString).size(withAttributes: [.font: font]).width
                return ReadingLogic.wordColumns(wordWidth: width, rowWidth: rowWidth)
            },
            rowsPerPage: words.rowsPerPage,
            seed: seed
        )
    }
}

/// Увеличенное слово: номер в шаге, страница (транскрипция - по её правилу) и откуда летит.
private struct WordZoom: Equatable {
    let index: Int
    let page: Int
    let source: CGRect
}

/// «прочитано N из M · каждое слово - K раз» и полоса.
private struct WordsProgress: View {
    let step: ReadingStep
    let words: ReadingWords
    let counts: [Int]
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    var body: some View {
        let read = ReadingLogic.wordsReadCount(step: step, counts: counts)
        let total = words.items.count
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text("makharij.words.progress \(read) \(total) \(Text("zikr.repetitions \(words.repeat)"))")
                .font(.footnote)
                .foregroundStyle(theme.palette.onAccentSecondary)
            LinearProgressBar(fraction: total == 0 ? 0 : Double(read) / Double(total), track: theme.palette.track, fill: colors.tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// «Читайте каждое слово вслух…» с ⓘ - хадис о чтении Къурана.
private struct ReadAloudNote: View {
    let colors: ReadingColors
    @Binding var info: LessonInfo?
    let id: Int

    var body: some View {
        LessonTextBlock(
            block: ReadingBlock.Text(text: String(localized: "makharij.words.readAloud"), info: ""),
            id: id,
            colors: colors,
            info: $info
        )
    }
}

/// Хадис Муслим 798 в карточке ⓘ.
private struct WordsHadithCard: View {
    private static let verse = FeaturedVerse(
        arabic: "الْمَاهِرُ بِالْقُرْآنِ مَعَ السَّفَرَةِ الْكِرَامِ الْبَرَرَةِ، وَالَّذِي يَقْرَأُ الْقُرْآنَ وَيَتَتَعْتَعُ فِيهِ وَهُوَ عَلَيْهِ شَاقٌّ لَهُ أَجْرَانِ",
        translation: "makharij.words.hadith.translation",
        reference: "makharij.words.hadith.reference",
        isFramed: false
    )

    var body: some View {
        FeaturedVerseBox(verse: Self.verse)
            .padding(.vertical, Spacing.xl)
    }
}

/// Ряды страницы справа налево; в паре словосочетание вдвое шире слова.
private struct WordGrid: View {
    let rows: [WordRow]
    let words: ReadingWords
    let counts: [Int]
    let showsTransliteration: Bool
    let zoomedIndex: Int?
    let colors: ReadingColors
    let coordinateSpace: String
    let onRead: (Int) -> Void
    let onZoom: (Int, CGRect) -> Void

    private static let pairPhraseWeight: CGFloat = 2

    var body: some View {
        VStack(spacing: Spacing.s) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                WeightedRow(weights: weights(row), spacing: Spacing.s) {
                    ForEach(row.words, id: \.index) { item in
                        ReadableWordTile(
                            word: item.word,
                            index: item.index,
                            count: counts.indices.contains(item.index) ? counts[item.index] : 0,
                            repeat: words.repeat,
                            showsTransliteration: showsTransliteration,
                            colors: colors,
                            coordinateSpace: coordinateSpace,
                            onRead: { onRead(item.index) },
                            onZoom: { onZoom(item.index, $0) }
                        )
                        .opacity(zoomedIndex == item.index ? 0 : 1)
                    }
                    ForEach(row.words.count..<max(row.columns, row.words.count), id: \.self) { _ in
                        Color.clear
                    }
                }
            }
        }
    }

    private func weights(_ row: WordRow) -> [CGFloat] {
        guard row.isPair else { return [] }
        return row.words.map { $0.word.isPhrase ? Self.pairPhraseWeight : 1 }
    }
}

/// Плитка ленты: касание - прочитал, долгое нажатие - увеличение, на последнем прочтении - вспышка ✦.
private struct ReadableWordTile: View {
    let word: ReadingWord
    let index: Int
    let count: Int
    let `repeat`: Int
    let showsTransliteration: Bool
    let colors: ReadingColors
    let coordinateSpace: String
    let onRead: () -> Void
    let onZoom: (CGRect) -> Void

    @Environment(\.theme) private var theme
    @State private var frame: CGRect = .zero
    @State private var zoomCount = 0
    /// Прочитано ли слово, когда плитка появилась: вспышка - только если дочитали сейчас.
    @State private var wasDone: Bool?
    @State private var burst = 0

    private var isDone: Bool { count >= `repeat` }

    var body: some View {
        WordTile(word: word, count: count, repeat: `repeat`, showsTransliteration: showsTransliteration, isZoomed: false, colors: colors)
            .overlay {
                if burst > 0 {
                    SparkleBurst(color: theme.palette.gold).id(burst)
                }
            }
            .popOnChange(of: count)
            .contentShape(.rect(cornerRadius: Radius.box))
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(coordinateSpace)) } action: { frame = $0 }
            .onTapGesture { if !isDone { onRead() } }
            .onLongPressGesture {
                zoomCount += 1
                onZoom(frame)
            }
            .sensoryFeedback(trigger: count) { old, new in new > old ? (new >= `repeat` ? .success : .selection) : nil }
            .sensoryFeedback(.impact(weight: .medium), trigger: zoomCount)
            .onAppear { if wasDone == nil { wasDone = isDone } }
            .onChange(of: isDone) { _, done in
                if done, wasDone == false { burst += 1 }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel(Text(verbatim: [word.arabic, showsTransliteration ? word.transliteration : nil].compactMap { $0 }.joined(separator: ", ")))
            .accessibilityValue(accessibilityValue)
            .accessibilityAction { if !isDone { onRead() } }
            .accessibilityAction(named: Text("makharij.words.readAction")) { if !isDone { onRead() } }
            .accessibilityAction(named: Text("makharij.words.zoomAction")) { onZoom(frame) }
    }

    private var accessibilityValue: Text {
        if `repeat` > 1 { return Text("makharij.words.tileState \(min(count, `repeat`)) \(`repeat`)") }
        return Text(isDone ? "makharij.words.tileRead" : "makharij.words.tileUnread")
    }
}

/// Плитка слова: слово, транскрипция, счёт в углу, заливка снизу по прочтениям; прочитанное - зелёное с ✓.
struct WordTile: View {
    static let wordSize: Double = 28
    static let zoomedWordSize: Double = 60
    static let zoomedPhraseSize: Double = 44
    /// Шрифт замера ширины слова - как в плитке.
    static var measureFont: UIFont {
        AppFont.registerBundledFonts()
        return UIFont(name: AppFont.arabicBold, size: wordSize) ?? .systemFont(ofSize: wordSize, weight: .bold)
    }

    let word: ReadingWord
    let count: Int
    let `repeat`: Int
    let showsTransliteration: Bool
    let isZoomed: Bool
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    private enum Layout {
        static let doneFill = 0.22
        static let readingFill = 0.5
        static let check: CGFloat = 14
        static let phraseLines = 2
        static let minScale: CGFloat = 0.73
        static let zoomedMinScale: CGFloat = 0.5
    }

    private var isDone: Bool { count >= `repeat` }
    private var fill: CGFloat { `repeat` > 0 ? min(CGFloat(count) / CGFloat(`repeat`), 1) : 0 }

    var body: some View {
        VStack(spacing: 0) {
            Text(verbatim: word.arabic)
                .font(.arabic(size: isZoomed ? (word.isPhrase ? Self.zoomedPhraseSize : Self.zoomedWordSize) : Self.wordSize, bold: true))
                .foregroundStyle(theme.palette.parchmentText)
                .multilineTextAlignment(.center)
                .lineLimit(word.isPhrase ? Layout.phraseLines : 1)
                .minimumScaleFactor(Layout.zoomedMinScale)
                .environment(\.layoutDirection, .rightToLeft)
            if let transliteration = word.transliteration {
                Text(verbatim: transliteration)
                    .font(isZoomed ? .title2 : .subheadline)
                    .foregroundStyle(colors.parchmentCaption)
                    .multilineTextAlignment(.center)
                    .lineLimit(word.isPhrase ? Layout.phraseLines : 1)
                    .minimumScaleFactor(isZoomed ? Layout.zoomedMinScale : Layout.minScale)
                    .opacity(showsTransliteration ? 1 : 0)
                    .animation(.default, value: showsTransliteration)
            }
        }
        .padding(.horizontal, Spacing.xs)
        .padding(.top, Spacing.m)
        .padding(.bottom, Spacing.xs)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: isZoomed ? .center : .top)
        .background { background }
        .overlay(alignment: .topLeading) { corner }
    }

    private var background: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.box)
        return ZStack {
            if isZoomed {
                shape.fill(theme.gradients.parchment.linear)
            }
            if isDone {
                shape.fill(theme.palette.success.opacity(Layout.doneFill))
            } else {
                shape.fill(colors.parchmentCell)
                // Наливается снизу - масштабом отрисовки, не высотой.
                colors.tint.opacity(Layout.readingFill)
                    .scaleEffect(x: 1, y: fill, anchor: .bottom)
                    .clipShape(shape)
                    .animation(Motion.progress, value: fill)
            }
            shape.strokeBorder(isDone ? theme.palette.parchmentAccent : colors.parchmentCellBorder, lineWidth: Size.hairline)
        }
    }

    @ViewBuilder
    private var corner: some View {
        if isDone {
            Image(systemName: "checkmark")
                .font(.system(size: Layout.check, weight: .bold))
                .foregroundStyle(theme.palette.parchmentAccent)
                .padding([.leading, .top], Spacing.xxs)
        } else if `repeat` > 1 {
            Text("makharij.words.count \(count) \(`repeat`)")
                .font(.caption2.bold())
                .foregroundStyle(colors.parchmentCaption)
                .padding(.leading, Spacing.xs)
                .padding(.top, Spacing.xxs)
        }
    }
}

/// Кнопки групп внизу: место каждой не меняется (кнопки не прыгают).
private struct GroupNavigation: View {
    @Binding var page: Int?
    let count: Int
    let colors: ReadingColors

    var body: some View {
        let current = page ?? 0
        if count > 1 {
            HStack(spacing: Spacing.s) {
                if current > 0 {
                    Button("makharij.words.previous") { page = current - 1 }
                        .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
                } else {
                    Color.clear.frame(maxWidth: .infinity, minHeight: Size.primaryButtonHeight)
                }
                if current < count - 1 {
                    Button("makharij.words.next") { page = current + 1 }
                        .buttonStyle(ReadingButtonStyle(colors: colors))
                } else {
                    Color.clear.frame(maxWidth: .infinity, minHeight: Size.primaryButtonHeight)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// Все слова прочитаны: «Проверить себя» (урок не пройден) или «Следующий шаг», «К списку шагов».
private struct WordsFinished: View {
    let step: ReadingStep
    let isLessonDone: Bool
    let next: ReadingStep?
    let colors: ReadingColors

    @Environment(AppRouter.self) private var router

    var body: some View {
        VStack(spacing: Spacing.m) {
            NoticeCard(
                symbol: "checkmark.circle",
                title: "makharij.words.done.title",
                message: isLessonDone ? "makharij.words.done.message" : "makharij.words.done.quiz"
            )
            .padding(.top, Spacing.s)
            if !isLessonDone {
                Button("makharij.lesson.check") { router.open(MakharijRoute.quiz(step.number)) }
                    .buttonStyle(ReadingButtonStyle(colors: colors))
            } else if let next {
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
    }
}
