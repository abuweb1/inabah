import SwiftUI

/// Зикр итога «Читаем зикр» (Android `ReadingZikrScreen.kt`, docs/android/06-screens.md 6.7 п. 29): одна лента -
/// «По словам» (каждое слово касанием отметить прочитанным), «Слитно» (места слитного чтения), «Целиком»
/// и «Прочитал целиком». Прочитаны все зикры итога - курс пройден, окно «مَا شَاءَ اللَّهُ». Запись
/// «Послушать» - этап записей курса.
struct ReadingZikrView: View {
    let stepNumber: Int
    let zikrIndex: Int

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { course in
                let count = course?.step(stepNumber)?.zikrs.count ?? 0
                return Text("makharij.zikr.title \(zikrIndex + 1) \(count)")
            },
            subtitle: { course in
                zikr(in: course).map { Text("makharij.zikr.source \(String(localized: $0.zikr.section.title)) \($0.zikr.number)") }
            },
            intro: .zikr,
            actions: { EmptyView() },
            content: { course, colors, _ in
                if let step = course.step(stepNumber), let zikr = zikr(in: course) {
                    ZikrContent(step: step, zikr: zikr, colors: colors)
                } else {
                    ReadingLessonSoon()
                }
            }
        )
    }

    private func zikr(in course: ReadingCourse?) -> ReadingBlock.Zikr? {
        guard let zikrs = course?.step(stepNumber)?.zikrs, zikrs.indices.contains(zikrIndex) else { return nil }
        return zikrs[zikrIndex]
    }
}

private struct ZikrContent: View {
    let step: ReadingStep
    let zikr: ReadingBlock.Zikr
    let colors: ReadingColors

    @Environment(ReadingProgress.self) private var progress
    @Environment(ReadingSettings.self) private var settings
    @Environment(AppRouter.self) private var router
    @Environment(ClipPlayer.self) private var clips
    @Environment(\.scenePhase) private var scenePhase

    @State private var rows: [WordRow] = []
    @State private var isCelebrating = false

    private static let space = "readingZikr"

    private enum Layout {
        /// Поля экрана и пергамента: ширина ряда плиток (как в «Читаем слова»).
        static let rowInset: CGFloat = Spacing.xl * 2 + Spacing.xxl * 2
    }

    var body: some View {
        let state = progress.zikrs[zikr.zikr] ?? ZikrProgress()
        let isHidden = settings.transliterationHidden
        ScrollView {
            VStack(spacing: Spacing.m) {
                SectionTitle(text: Text("makharij.zikr.wordsProgress \(state.words.intersection(zikr.words.indices).count) \(zikr.words.count)"), colors: colors)
                ParchmentPanel(bottomCornerRadius: Radius.card) {
                    VStack(spacing: Spacing.s) {
                        ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                            WeightedRow(spacing: Spacing.s) {
                                ForEach(row.words, id: \.index) { item in
                                    ReadableWordTile(
                                        word: item.word,
                                        count: state.words.contains(item.index) ? 1 : 0,
                                        repeat: 1,
                                        showsTransliteration: !isHidden,
                                        colors: colors,
                                        coordinateSpace: Self.space,
                                        onRead: { progress.readZikrWord(zikr.zikr, word: item.index) }
                                    )
                                }
                            }
                        }
                    }
                }
                if !zikr.joins.isEmpty {
                    SectionTitle(text: Text("makharij.zikr.joins"), colors: colors)
                    ParchmentPanel(bottomCornerRadius: Radius.card) {
                        VStack(spacing: Spacing.s) {
                            ForEach(zikr.joins, id: \.self) { join in
                                JoinCell(join: join, isTransliterationHidden: isHidden, colors: colors)
                            }
                        }
                    }
                }
                SectionTitle(text: Text("makharij.zikr.whole"), colors: colors)
                WholeZikr(zikr: zikr, isTransliterationHidden: isHidden, colors: colors)
                if let audio = zikr.audio, clips.available.contains(audio) {
                    let isPlaying = clips.playing == audio
                    Button(isPlaying ? "makharij.zikr.stop" : "makharij.zikr.play") {
                        if isPlaying { clips.stop() } else { clips.play(audio) }
                    }
                    .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
                }
                if !state.isRead {
                    Button("makharij.zikr.read") { progress.markZikrRead(zikr.zikr) }
                        .buttonStyle(ReadingButtonStyle(colors: colors))
                }
                Button("makharij.zikr.openAzkar") { router.open(AzkarRoute.list(zikr.zikr.section)) }
                    .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.s)
            .padding(.bottom, Spacing.xl)
        }
        .coordinateSpace(.named(Self.space))
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width in
            rows = Self.rows(for: zikr, rowWidth: width - Layout.rowInset)
        }
        .overlay {
            if isCelebrating {
                ReadingStepCompletedView(step: step, next: nil, colors: colors)
                    .transition(.opacity)
            }
        }
        .animation(Motion.overlay, value: isCelebrating)
        // Прочитан последний зикр - итог пройден (один раз: урок отмечается здесь же).
        .onAppear(perform: completeIfRead)
        .onChange(of: ReadingLogic.allZikrsDone(step: step, progress: progress.zikrs)) { completeIfRead() }
        // Запись зикра длинная: уход с экрана и в фон - тишина (как Android).
        .onDisappear(perform: stopOwnClip)
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { stopOwnClip() }
        }
    }

    private func stopOwnClip() {
        if let audio = zikr.audio, clips.playing == audio { clips.stop() }
    }

    private func completeIfRead() {
        if ReadingLogic.completeFinalIfRead(step: step, progress: progress) {
            isCelebrating = true
        }
    }

    /// Ряды «По словам» - по порядку зикра, столбцы - по ширине слова шрифтом плитки.
    private static func rows(for zikr: ReadingBlock.Zikr, rowWidth: CGFloat) -> [WordRow] {
        let font = WordTile.measureFont
        return ReadingLogic.zikrRows(words: zikr.words) { word in
            let width = (word.arabic as NSString).size(withAttributes: [.font: font]).width
            return ReadingLogic.wordColumns(wordWidth: width, rowWidth: rowWidth)
        }
    }
}

/// Заголовок раздела ленты: цвет раздела, заглавными.
private struct SectionTitle: View {
    let text: Text
    let colors: ReadingColors

    var body: some View {
        text
            .textCase(.uppercase)
            .font(.caption.weight(.semibold))
            .foregroundStyle(colors.tint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding([.leading, .top], Spacing.xs)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Место слитного чтения: слова вместе, их транскрипция и почему так читается.
private struct JoinCell: View {
    let join: ReadingJoin
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    private enum Layout {
        static let arabic: Double = 30
        static let lines = 2
        /// Кегль `caption` - для арабских вставок в пояснении.
        static let noteSize: Double = 12
    }

    var body: some View {
        VStack(spacing: Spacing.xxs) {
            Text(verbatim: join.arabic)
                .font(.arabic(size: Layout.arabic, bold: true))
                .foregroundStyle(theme.palette.parchmentText)
                .multilineTextAlignment(.center)
                .lineLimit(Layout.lines)
                .environment(\.layoutDirection, .rightToLeft)
            Text(verbatim: join.transliteration)
                .font(.caption.weight(.semibold))
                .foregroundStyle(colors.parchmentCaption)
                .opacity(isTransliterationHidden ? 0 : 1)
            Text(ReadingText.withArabicRuns(join.note, baseSize: Layout.noteSize))
                .font(.caption)
                .foregroundStyle(colors.parchmentCaption)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.s)
        .padding(.vertical, Spacing.xs)
        .parchmentCell(colors)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: [join.arabic, isTransliterationHidden ? nil : join.transliteration, join.note].compactMap { $0 }.joined(separator: ", ")))
    }
}

/// Зикр целиком и его транскрипция.
private struct WholeZikr: View {
    let zikr: ReadingBlock.Zikr
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    private static let arabic: Double = 30

    var body: some View {
        ParchmentPanel(bottomCornerRadius: Radius.card) {
            VStack(spacing: Spacing.xs) {
                Text(verbatim: zikr.arabic)
                    .font(.arabic(size: Self.arabic, bold: true))
                    .foregroundStyle(theme.palette.parchmentText)
                    .multilineTextAlignment(.center)
                    .environment(\.layoutDirection, .rightToLeft)
                Text(verbatim: zikr.transliteration)
                    .font(.caption)
                    .foregroundStyle(colors.parchmentCaption)
                    .multilineTextAlignment(.center)
                    .opacity(isTransliterationHidden ? 0 : 1)
            }
            .frame(maxWidth: .infinity)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: [zikr.arabic, isTransliterationHidden ? nil : zikr.transliteration].compactMap { $0 }.joined(separator: ", ")))
    }
}
