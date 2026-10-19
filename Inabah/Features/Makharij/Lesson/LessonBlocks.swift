import SwiftUI

// Блоки урока «Учимся читать» (Android `ReadingLessonScreen.kt`): общие для урока, «Весь алфавит» и справки.
// Арабский - фиксированного кегля Scheherazade (`Font.arabic`), русский - стилями интерфейса и переводов.

/// Что умеют блоки урока: открыть лист буквы, карточку буквы, «Весь алфавит», тему.
struct LessonActions {
    let openSheet: LetterSheetOpener
    var openLetterForms: (ReadingLetter) -> Void = { _ in }
    var openAlphabet: () -> Void = {}
    var openTopic: (Int) -> Void = { _ in }
    var openZikr: (Int) -> Void = { _ in }
}

// MARK: - Текст с ⓘ

/// Короткий текст урока; ⓘ - полный текст во всплывающей карточке (`lessonInfoPopover`).
struct LessonTextBlock: View {
    let block: ReadingBlock.Text
    let id: Int
    let colors: ReadingColors
    @Binding var info: LessonInfo?

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    private enum Layout {
        static let infoIcon: CGFloat = 24
        static let infoTap: CGFloat = 44
        static let trailingWithInfo: CGFloat = 6
    }

    var body: some View {
        HStack(spacing: 0) {
            LessonParagraph(text: block.text, color: theme.palette.onAccent)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let text = block.info {
                Button {
                    withAnimation(Motion.dialog) { info = LessonInfo(id: id, text: text) }
                } label: {
                    Image(systemName: "info.circle")
                        .font(.system(size: Layout.infoIcon))
                        .foregroundStyle(colors.tint)
                        .frame(width: Layout.infoTap, height: Layout.infoTap)
                        .contentShape(.rect)
                }
                .buttonStyle(PressDimButtonStyle())
                .accessibilityLabel(Text("makharij.info.more"))
                .lessonInfoAnchor(id)
            }
        }
        .padding(.leading, Spacing.xl)
        .padding(.trailing, block.info == nil ? Spacing.xl : Layout.trailingWithInfo)
        .padding(.vertical, Spacing.m)
        .readingTile(colors)
    }
}

/// Абзац урока: шрифт переводов (растёт с «Размером текста»), арабские вставки - Scheherazade.
struct LessonParagraph: View {
    let text: String
    let color: Color

    @Environment(\.contentTextScale) private var scale

    var body: some View {
        Text(ReadingText.withArabicRuns(text, baseSize: ContentTextStyle.translation.baseSize * scale))
            .font(.content(.translation, scale: scale))
            .lineSpacing(ContentTextStyle.translationLineSpacing * scale)
            .foregroundStyle(color)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// Открытая ⓘ: какой текст и у какой кнопки.
struct LessonInfo: Equatable {
    let id: Int
    let text: String
}

/// Рамки кнопок ⓘ - по номеру блока.
private struct LessonInfoAnchorKey: PreferenceKey {
    static let defaultValue: [Int: Anchor<CGRect>] = [:]

    static func reduce(value: inout [Int: Anchor<CGRect>], nextValue: () -> [Int: Anchor<CGRect>]) {
        value.merge(nextValue()) { $1 }
    }
}

/// Ширина карточки ⓘ (Android `InfoMaxWidth`).
private enum LessonInfoMetrics {
    static let width: CGFloat = 300
}

private struct LessonInfoPopover<Card: View>: ViewModifier {
    @Binding var info: LessonInfo?
    let colors: ReadingColors
    @ViewBuilder let card: (LessonInfo) -> Card

    @State private var cardHeight: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlayPreferenceValue(LessonInfoAnchorKey.self) { anchors in
                GeometryReader { proxy in
                    AnchoredPopover(
                        anchor: info.flatMap { anchors[$0.id] }.map { proxy[$0] },
                        containerWidth: proxy.size.width,
                        width: LessonInfoMetrics.width,
                        tint: colors.tint,
                        belowEnd: .init(containerHeight: proxy.size.height, cardHeight: cardHeight),
                        onDismiss: { withAnimation(Motion.popoverClose) { info = nil } }
                    ) {
                        if let info {
                            card(info)
                                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { cardHeight = $0 }
                        }
                    }
                }
            }
    }
}

private struct InfoCard: View {
    let text: String

    @Environment(\.theme) private var theme

    var body: some View {
        LessonParagraph(text: text, color: theme.palette.onAccent)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Spacing.xl)
    }
}

extension View {
    /// Кнопка ⓘ блока `id` - над ней открывается карточка `lessonInfoPopover`.
    func lessonInfoAnchor(_ id: Int) -> some View {
        anchorPreference(key: LessonInfoAnchorKey.self, value: .bounds) { [id: $0] }
    }

    /// Всплывающая карточка полного текста ⓘ - под кнопкой, правым краем к ней (Android `BelowEnd`).
    func lessonInfoPopover(_ info: Binding<LessonInfo?>, colors: ReadingColors) -> some View {
        modifier(LessonInfoPopover(info: info, colors: colors) { InfoCard(text: $0.text) })
    }

    /// То же со своим содержимым карточки (хадис в «Читаем слова»).
    func lessonInfoPopover(_ info: Binding<LessonInfo?>, colors: ReadingColors, @ViewBuilder card: @escaping (LessonInfo) -> some View) -> some View {
        modifier(LessonInfoPopover(info: info, colors: colors, card: card))
    }
}

// MARK: - Знаки

/// Знаки шага на примере буквы: слог, название, «где · «звук»».
struct LessonMarksBlock: View {
    let block: ReadingBlock.Marks
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    private static let syllableSize: Double = 36

    var body: some View {
        WeightedRow(spacing: Spacing.s) {
            ForEach(block.marks, id: \.id) { mark in
                VStack(spacing: Spacing.xxs) {
                    Text(verbatim: block.example.syllable(mark))
                        .font(.arabic(size: Self.syllableSize))
                        .foregroundStyle(theme.palette.onAccent)
                        .fixedSize()
                    Text(verbatim: mark.name)
                        .font(.footnote.bold())
                        .foregroundStyle(theme.palette.onAccent)
                    Text("makharij.mark.caption \(mark.where) \(mark.sound)")
                        .font(.caption2)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, Spacing.xxs)
                .padding(.vertical, Spacing.s)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .readingTile(colors, cornerRadius: Radius.box, lineWidth: ReadingMetrics.tileBorder)
                .accessibilityElement(children: .combine)
            }
        }
    }
}

// MARK: - Сетка букв

/// Буквы справа налево по `columns` в ряд; неполный ряд - с пустыми местами. Нажатие - лист буквы.
struct LessonLettersGrid: View {
    let letters: [ReadingLetter]
    var columns = Self.defaultColumns
    let colors: ReadingColors
    /// Номер буквы, звучащей в «Послушать алфавит»: увеличена поверх соседей, над ней - название.
    var playingLetter: Int? = nil
    let onTap: (ReadingLetter) -> Void

    static let defaultColumns = 5

    @Environment(\.theme) private var theme
    @Environment(ClipPlayer.self) private var clips
    @ScaledMetric(relativeTo: .body) private var tileHeight: CGFloat = 64
    @ScaledMetric(relativeTo: .body) private var speakerSize: CGFloat = 12

    private enum Layout {
        static let letterSize: Double = 30
        static let playingScale: CGFloat = 1.2
    }

    var body: some View {
        VStack(spacing: Spacing.s) {
            ForEach(Array(ReadingLogic.paddedRows(letters, columns: columns).enumerated()), id: \.offset) { _, row in
                WeightedRow(spacing: Spacing.s) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, letter in
                        if let letter {
                            tile(letter, isPlaying: letter.number == playingLetter)
                        } else {
                            Color.clear.frame(height: tileHeight)
                        }
                    }
                }
                // Увеличенная буква и название над ней - поверх соседнего ряда.
                .zIndex(row.contains { $0?.number == playingLetter && playingLetter != nil } ? 1 : 0)
            }
        }
        .animation(Motion.collapse, value: playingLetter)
    }

    private func tile(_ letter: ReadingLetter, isPlaying: Bool) -> some View {
        Button { onTap(letter) } label: {
            Text(verbatim: letter.letter)
                .font(.arabic(size: Layout.letterSize))
                .foregroundStyle(theme.palette.onAccent)
                .fixedSize()
                .frame(maxWidth: .infinity, minHeight: tileHeight)
                .readingTile(colors, cornerRadius: Radius.box, lineWidth: ReadingMetrics.tileBorder, isHighlighted: isPlaying)
                // Есть запись буквы - значок в левом нижнем углу (и в сетке справа налево - слева).
                .overlay(alignment: .bottomLeading) {
                    if letter.audio.values.contains(where: clips.available.contains) {
                        Image(systemName: "speaker.wave.2.fill")
                            .font(.system(size: speakerSize))
                            .foregroundStyle(theme.palette.onAccentTertiary)
                            .padding(Spacing.xs)
                            .accessibilityHidden(true)
                    }
                }
                .contentShape(.rect(cornerRadius: Radius.box))
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
        .scaleEffect(isPlaying ? Layout.playingScale : 1)
        .overlay(alignment: .top) {
            if isPlaying {
                PlayingNameBubble(name: letter.name, colors: colors)
                    .transition(.opacity.animation(Motion.nameBubble))
            }
        }
        .zIndex(isPlaying ? 1 : 0)
        .accessibilityLabel(Text(verbatim: letter.name))
    }
}

/// Название звучащей буквы над ней (Android `PlayingNameBubble`): рамка цвета раздела, сдвиг отрисовки вверх.
private struct PlayingNameBubble: View {
    let name: String
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    /// Название выше верха плитки на столько: плитка увеличена (×1,2), её рамка выше на 0,1 высоты -
    /// название заходит на рамку, не на букву.
    private static let lift: CGFloat = 2

    var body: some View {
        Text(verbatim: name)
            .font(.headline)
            .foregroundStyle(theme.palette.onAccent)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, Spacing.m)
            .padding(.vertical, Spacing.xxs)
            .background {
                Capsule()
                    .fill(colors.sheet)
                    .strokeBorder(colors.tint, lineWidth: ReadingMetrics.tileBorder)
            }
            // Нижний край названия - у верха плитки: рамка нулевой высоты, название выходит вверх.
            .frame(height: 0, alignment: .bottom)
            .offset(y: -Self.lift)
            .accessibilityHidden(true)
    }
}

// MARK: - Слоги

/// Ячейка слога на пергаменте: слог и транскрипция (место под ней остаётся и при скрытом глазе).
struct SyllableCell: View {
    let letter: ReadingLetter
    let mark: ReadingMark
    let isTransliterationHidden: Bool
    let colors: ReadingColors
    let onTap: (ReadingLetter) -> Void

    @Environment(\.theme) private var theme

    private static let syllableSize: Double = 32

    var body: some View {
        let transliteration = letter.transliteration(mark) ?? ""
        Button { onTap(letter) } label: {
            VStack(spacing: 0) {
                Text(verbatim: letter.syllable(mark))
                    .font(.arabic(size: Self.syllableSize, bold: true))
                    .foregroundStyle(theme.palette.parchmentText)
                    .fixedSize()
                Text(verbatim: transliteration)
                    .font(.caption)
                    .foregroundStyle(colors.parchmentCaption)
                    .padding(.top, Spacing.xs)
                    .opacity(isTransliterationHidden ? 0 : 1)
            }
            .padding(.vertical, Spacing.xs)
            .frame(maxWidth: .infinity)
            .parchmentCell(colors)
            .contentShape(.rect(cornerRadius: Radius.box))
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
        .accessibilityLabel(Text(verbatim: "\(letter.name), \(transliteration)"))
    }
}

/// Случайные слоги урока: пергамент по 3 в ряд и «Посмотреть весь алфавит».
struct LessonSampleSyllables: View {
    let samples: [SyllableSample]
    let isTransliterationHidden: Bool
    let colors: ReadingColors
    let actions: LessonActions

    private static let columns = 3

    var body: some View {
        VStack(spacing: Spacing.m) {
            ParchmentPanel(bottomCornerRadius: Radius.card) {
                VStack(spacing: Spacing.s) {
                    ForEach(Array(ReadingLogic.paddedRows(samples, columns: Self.columns).enumerated()), id: \.offset) { _, row in
                        WeightedRow(spacing: Spacing.s) {
                            ForEach(Array(row.enumerated()), id: \.offset) { _, sample in
                                if let sample {
                                    SyllableCell(
                                        letter: sample.letter, mark: sample.mark,
                                        isTransliterationHidden: isTransliterationHidden, colors: colors,
                                        onTap: actions.openSheet.open
                                    )
                                } else {
                                    Color.clear
                                }
                            }
                        }
                    }
                }
            }
            Button("makharij.alphabet.table.open", action: actions.openAlphabet)
                .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
        }
    }
}

/// Все слоги: карточки по 4 буквы, в ряду - буква со знаками справа налево.
struct SyllablesTable: View {
    let block: ReadingBlock.Syllables
    let isTransliterationHidden: Bool
    let colors: ReadingColors
    let onTap: (ReadingLetter) -> Void

    private static let lettersPerCard = 4

    var body: some View {
        VStack(spacing: Spacing.m) {
            ForEach(Array(stride(from: 0, to: block.letters.count, by: Self.lettersPerCard)), id: \.self) { start in
                ParchmentPanel(bottomCornerRadius: Radius.card) {
                    VStack(spacing: Spacing.s) {
                        ForEach(block.letters[start..<min(start + Self.lettersPerCard, block.letters.count)], id: \.number) { letter in
                            WeightedRow(spacing: Spacing.s) {
                                ForEach(block.marks, id: \.id) { mark in
                                    SyllableCell(
                                        letter: letter, mark: mark,
                                        isTransliterationHidden: isTransliterationHidden, colors: colors, onTap: onTap
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Закрытые слоги

/// Случайные закрытые слоги урока (шаг «Сукун», без формул - просто слог): пергамент по 3 в ряд и
/// «Прочтите весь алфавит».
struct LessonSampleClosedSyllables: View {
    let samples: [ReadingClosedSyllable]
    let isTransliterationHidden: Bool
    let colors: ReadingColors
    let onOpenAlphabet: () -> Void

    private static let columns = 3

    var body: some View {
        VStack(spacing: Spacing.m) {
            ParchmentPanel(bottomCornerRadius: Radius.card) {
                VStack(spacing: Spacing.s) {
                    ForEach(Array(ReadingLogic.paddedRows(samples, columns: Self.columns).enumerated()), id: \.offset) { _, row in
                        ClosedSyllablesRow(cells: row, isTransliterationHidden: isTransliterationHidden, colors: colors)
                    }
                }
            }
            Button("makharij.alphabet.formulas.open", action: onOpenAlphabet)
                .buttonStyle(ReadingButtonStyle(colors: colors, isProminent: false))
        }
    }
}

/// Все закрытые слоги («Весь алфавит» шага «Сукун»): карточки по 4 буквы, ряд - буква, столбцы - фатхьа,
/// касра, домма справа налево; нет слога (мадд у و ي) - пустое место.
struct ClosedSyllablesTable: View {
    let block: ReadingBlock.ClosedSyllables
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    private static let lettersPerCard = 4

    var body: some View {
        VStack(spacing: Spacing.m) {
            ForEach(Array(stride(from: 0, to: block.rows.count, by: Self.lettersPerCard)), id: \.self) { start in
                ParchmentPanel(bottomCornerRadius: Radius.card) {
                    VStack(spacing: Spacing.s) {
                        ForEach(start..<min(start + Self.lettersPerCard, block.rows.count), id: \.self) { index in
                            ClosedSyllablesRow(cells: block.rows[index], isTransliterationHidden: isTransliterationHidden, colors: colors)
                        }
                    }
                }
            }
        }
    }
}

/// Ряд закрытых слогов; `nil` - пустое место той же ширины.
private struct ClosedSyllablesRow: View {
    let cells: [ReadingClosedSyllable?]
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    var body: some View {
        WeightedRow(spacing: Spacing.s) {
            ForEach(Array(cells.enumerated()), id: \.offset) { _, cell in
                if let cell {
                    ClosedSyllableCell(cell: cell, isTransliterationHidden: isTransliterationHidden, colors: colors)
                } else {
                    Color.clear
                }
            }
        }
    }
}

/// Ячейка закрытого слога: слог и транскрипция под ним. Есть запись в сборке - 🔈 в углу, нажатие играет её
/// (как слова-примеры форм); нет - не нажимается.
private struct ClosedSyllableCell: View {
    let cell: ReadingClosedSyllable
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    @Environment(\.theme) private var theme
    @Environment(ClipPlayer.self) private var clips
    @State private var playCount = 0

    private static let syllableSize: Double = 32

    var body: some View {
        let audio = cell.audio.flatMap { clips.available.contains($0) ? $0 : nil }
        Group {
            if let audio {
                Button {
                    playCount += 1
                    clips.play(audio)
                } label: {
                    content
                        .overlay(alignment: .topLeading) { ExampleWordSpeaker(colors: colors) }
                        .contentShape(.rect(cornerRadius: Radius.box))
                }
                .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
                .sensoryFeedback(.selection, trigger: playCount)
            } else {
                content
                    .accessibilityElement(children: .ignore)
            }
        }
        .accessibilityLabel(Text(verbatim: "\(cell.letter.name), \(cell.transliteration)"))
        .modifier(ListenAction(isEnabled: audio != nil) { if let audio { clips.play(audio) } })
    }

    private var content: some View {
        VStack(spacing: 0) {
            Text(verbatim: cell.arabic)
                .font(.arabic(size: Self.syllableSize, bold: true))
                .foregroundStyle(theme.palette.parchmentText)
                .fixedSize()
            Text(verbatim: cell.transliteration)
                .font(.caption)
                .foregroundStyle(colors.parchmentCaption)
                .padding(.top, Spacing.xs)
                .opacity(isTransliterationHidden ? 0 : 1)
        }
        .padding(.vertical, Spacing.xs)
        .frame(maxWidth: .infinity)
        .parchmentCell(colors)
    }
}

// MARK: - Правило

/// Правило: арабское название, транскрипция, пояснение и буквы правила (одна - рядом с текстом).
struct LessonRuleBlock: View {
    let block: ReadingBlock.Rule
    let colors: ReadingColors
    let onTap: (ReadingLetter) -> Void

    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .body) private var singleTile: CGFloat = 64

    private enum Layout {
        static let arabic: Double = 26
        static let minColumns = 5
        static let maxColumns = 7
    }

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text(verbatim: block.arabic)
                .font(.arabic(size: Layout.arabic))
                .foregroundStyle(theme.palette.onAccent)
                .multilineTextAlignment(.center)
                .environment(\.layoutDirection, .rightToLeft)
            Text(verbatim: block.transliteration)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(colors.tint)
                .multilineTextAlignment(.center)
            if block.letters.count == 1 {
                HStack(spacing: Spacing.m) {
                    ruleText
                    LessonLettersGrid(letters: block.letters, columns: 1, colors: colors, onTap: onTap)
                        .frame(width: singleTile)
                }
                .padding(.top, Spacing.xxs)
            } else {
                ruleText
                    .padding(.top, Spacing.xxs)
                if !block.letters.isEmpty {
                    LessonLettersGrid(
                        letters: block.letters,
                        columns: min(max(block.letters.count, Layout.minColumns), Layout.maxColumns),
                        colors: colors,
                        onTap: onTap
                    )
                    .padding(.top, Spacing.xs)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.m)
        .readingTile(colors)
    }

    private var ruleText: some View {
        LessonParagraph(text: block.text, color: theme.palette.onAccentStrong)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

// MARK: - Таблица форм

/// Формы букв по группам похожего написания (заголовок форм - у первой карточки) и третья карточка
/// «в словах». Нажатие - карточка буквы.
struct LessonFormsTable: View {
    let block: ReadingBlock.Forms
    /// Шаг - для имени записи слова-примера.
    let step: Int
    let colors: ReadingColors
    let onOpen: (ReadingLetter) -> Void

    @Environment(\.theme) private var theme
    @Environment(ClipPlayer.self) private var clips
    @State private var playCount = 0

    private enum Layout {
        static let form: Double = 28
        static let word: Double = 26
        static let letterWeight: CGFloat = 0.6
        static let wordWeight: CGFloat = 1.15
    }

    var body: some View {
        VStack(spacing: Spacing.m) {
            ForEach(Array(block.groups.enumerated()), id: \.offset) { index, group in
                ParchmentPanel(bottomCornerRadius: Radius.card) {
                    VStack(spacing: Spacing.xs) {
                        if index == 0 { header }
                        ForEach(group, id: \.number) { letter in
                            WeightedRow(spacing: Spacing.xs) {
                                ForEach(LetterForm.allCases, id: \.self) { form in
                                    formCell(letter, form)
                                }
                            }
                        }
                    }
                }
            }
            let card = block.exampleCard.compactMap { letter in block.examples[letter].map { (letter, $0) } }
            if !card.isEmpty {
                ParchmentPanel(bottomCornerRadius: Radius.card) {
                    VStack(spacing: Spacing.xs) {
                        ForEach(card, id: \.0.number) { letter, examples in
                            exampleRow(letter, examples: examples)
                        }
                    }
                }
            }
        }
    }

    private var header: some View {
        WeightedRow(spacing: Spacing.xs) {
            ForEach(LetterForm.allCases, id: \.self) { form in
                Text(form.caption)
                    .font(.caption2)
                    .foregroundStyle(colors.parchmentCaption)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
            }
        }
        .accessibilityHidden(true)
    }

    private func formCell(_ letter: ReadingLetter, _ form: LetterForm) -> some View {
        Button { onOpen(letter) } label: {
            Text(verbatim: letter.form(form))
                .font(.arabic(size: Layout.form, bold: true))
                .foregroundStyle(theme.palette.parchmentText)
                .fixedSize()
                .padding(.top, Spacing.xs)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .parchmentCell(colors)
                .contentShape(.rect(cornerRadius: Radius.box))
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
        .accessibilityLabel(Text(verbatim: "\(letter.name), \(String(localized: form.place))"))
    }

    private func exampleRow(_ letter: ReadingLetter, examples: FormExamples) -> some View {
        WeightedRow(weights: [Layout.letterWeight] + Array(repeating: Layout.wordWeight, count: 3), spacing: Spacing.xs) {
            Button { onOpen(letter) } label: {
                Text(verbatim: letter.letter)
                    .font(.arabic(size: Layout.form, bold: true))
                    .foregroundStyle(theme.palette.parchmentText)
                    .fixedSize()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .parchmentCell(colors)
                    .contentShape(.rect(cornerRadius: Radius.box))
            }
            .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
            .accessibilityLabel(Text(verbatim: letter.name))
            ForEach(LetterForm.placed, id: \.self) { form in
                if let word = examples.word(form) {
                    // Есть запись - нажатие играет её; нет - карточка буквы.
                    let file = ReadingLogic.formExampleAudio(step: step, letter: letter, form: form)
                    let audio = clips.available.contains(file) ? file : nil
                    Button {
                        if let audio {
                            playCount += 1
                            clips.play(audio)
                        } else {
                            onOpen(letter)
                        }
                    } label: {
                        ExampleWordText(word: word, letter: letter, form: form, size: Layout.word, colors: colors)
                            .padding(.top, Spacing.xs)
                            .padding(.bottom, Spacing.xxs)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .parchmentCell(colors)
                            .overlay(alignment: .topLeading) {
                                if audio != nil { ExampleWordSpeaker(colors: colors) }
                            }
                            .contentShape(.rect(cornerRadius: Radius.box))
                    }
                    .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
                    .accessibilityLabel(Text(verbatim: word))
                    .modifier(ListenAction(isEnabled: audio != nil) { if let audio { clips.play(audio) } })
                }
            }
        }
        .sensoryFeedback(.selection, trigger: playCount)
    }
}

/// Слово-пример с выделенной буквой формы - цвет `parchmentMark` своего фона арабского текста.
struct ExampleWordText: View {
    let word: String
    let letter: ReadingLetter
    let form: LetterForm
    let size: Double
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    var body: some View {
        Text(ReadingText.highlighted(
            word,
            range: ReadingLogic.letterRange(word: word, letter: letter.letter, form: form),
            color: colors.letterHighlight
        ))
        .font(.arabic(size: size, bold: true))
        .foregroundStyle(theme.palette.parchmentText)
        .lineLimit(1)
        .minimumScaleFactor(0.5)
        .environment(\.layoutDirection, .rightToLeft)
    }
}

extension LetterForm {
    /// Формы со словами-примерами: в начале, в середине, в конце.
    static let placed: [LetterForm] = [.initial, .medial, .final]

    var caption: LocalizedStringResource {
        switch self {
        case .isolated: "makharij.form.isolated"
        case .initial: "makharij.form.initial"
        case .medial: "makharij.form.medial"
        case .final: "makharij.form.final"
        }
    }

    var place: LocalizedStringResource {
        switch self {
        case .isolated: "makharij.form.isolated.place"
        case .initial: "makharij.form.initial.place"
        case .medial: "makharij.form.medial.place"
        case .final: "makharij.form.final.place"
        }
    }
}

// MARK: - Формулы

/// Формулы на пергаменте: части через «+» и итог после «=», справа налево; в одну колонку - одной строкой.
struct LessonFormulasBlock: View {
    let items: [ReadingFormula]
    let columns: Int
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    var body: some View {
        ParchmentPanel(bottomCornerRadius: Radius.card) {
            FormulaRows(items: items, columns: columns, isTransliterationHidden: isTransliterationHidden, colors: colors)
        }
    }
}

private struct FormulaRows: View {
    let items: [ReadingFormula]
    let columns: Int
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    var body: some View {
        VStack(spacing: Spacing.s) {
            ForEach(Array(ReadingLogic.formulaRows(items, columns: columns).enumerated()), id: \.offset) { _, row in
                WeightedRow(spacing: Spacing.s) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, formula in
                        if let formula {
                            FormulaCell(formula: formula, isWide: columns == 1, isTransliterationHidden: isTransliterationHidden, colors: colors)
                        } else {
                            Color.clear
                        }
                    }
                }
            }
        }
    }
}

private struct FormulaCell: View {
    let formula: ReadingFormula
    /// Одна колонка - части и итог одной строкой (до двух строк).
    let isWide: Bool
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    @Environment(\.theme) private var theme

    private enum Layout {
        static let result: Double = 32
        static let parts: Double = 22
        static let wideLines = 2
    }

    var body: some View {
        VStack(spacing: 0) {
            if isWide {
                arabic(parts + resultText, size: Layout.result)
                    .lineLimit(Layout.wideLines)
                    .multilineTextAlignment(.center)
            } else {
                if !formula.parts.isEmpty {
                    arabic(parts, size: Layout.parts).lineLimit(1)
                }
                arabic(resultText, size: Layout.result).lineLimit(1)
            }
            if let transliteration = formula.transliteration {
                Text(verbatim: transliteration)
                    .font(.caption)
                    .foregroundStyle(colors.parchmentCaption)
                    .opacity(isTransliterationHidden ? 0 : 1)
            }
        }
        .padding(.horizontal, Spacing.xxs)
        .padding(.vertical, Spacing.xs)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .parchmentCell(colors)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: [fullText, formula.transliteration].compactMap { $0 }.joined(separator: ", ")))
    }

    private func arabic(_ text: AttributedString, size: Double) -> some View {
        Text(text)
            .font(.arabic(size: size, bold: true))
            .foregroundStyle(theme.palette.parchmentText)
            .minimumScaleFactor(0.5)
            .environment(\.layoutDirection, .rightToLeft)
    }

    /// Части: первая - как есть, следующие - через «+», цветом выделения.
    private var parts: AttributedString {
        formula.parts.enumerated().reduce(into: AttributedString()) { result, part in
            if part.offset == 0 {
                result += AttributedString(part.element)
            } else {
                var added = AttributedString(part.element)
                added.foregroundColor = colors.letterHighlight
                result += AttributedString(" + ") + added
            }
        }
    }

    private var resultText: AttributedString {
        AttributedString(formula.parts.isEmpty ? formula.result : (isWide ? " = " : "= ") + formula.result)
    }

    private var fullText: String {
        formula.parts.isEmpty ? formula.result : formula.parts.joined(separator: " + ") + " = " + formula.result
    }
}

// MARK: - Тема урока

/// Тема «Особых случаев»: название, кратко и пример; нажатие - экран темы.
struct LessonTopicCard: View {
    let topic: ReadingBlock.Topic
    let colors: ReadingColors
    let onOpen: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    private static let exampleSize: Double = 28

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: Spacing.m) {
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(ReadingText.withArabicRuns(topic.extra.title, baseSize: ContentTextStyle.translation.baseSize))
                        .font(.body.weight(.semibold))
                        .foregroundStyle(theme.palette.onAccent)
                    LessonParagraph(text: topic.summary, color: theme.palette.onAccentSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if let example = topic.example {
                    Text(verbatim: example)
                        .font(.arabic(size: Self.exampleSize, bold: true))
                        .foregroundStyle(colors.tint)
                        .lineLimit(1)
                        .fixedSize()
                }
                Image(systemName: "chevron.forward")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(colors.tint)
                    .accessibilityHidden(true)
            }
            .multilineTextAlignment(.leading)
            .padding(.leading, Spacing.xl)
            .padding(.trailing, Spacing.m)
            .padding(.vertical, Spacing.m)
            .readingTile(colors)
            .contentShape(.rect(cornerRadius: Radius.card))
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.wideScale))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Зикр итога

/// Зикр итога «Читаем зикр» в уроке: номер (или ✓, когда прочитан), зикр, «N слов · что в нём из курса».
struct LessonZikrCard: View {
    let zikr: ReadingBlock.Zikr
    let number: Int
    let colors: ReadingColors
    let onOpen: () -> Void

    @Environment(ReadingProgress.self) private var progress
    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    @ScaledMetric(relativeTo: .body) private var circle: CGFloat = 36

    private enum Layout {
        static let numberSize: Double = 22
        static let check: CGFloat = 18
        static let arabicSize: Double = 28
    }

    var body: some View {
        let isDone = ReadingLogic.isZikrDone(zikr, progress: progress.zikrs[zikr.zikr])
        Button(action: onOpen) {
            HStack(spacing: Spacing.m) {
                badge(isDone: isDone)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(verbatim: zikr.arabic)
                        .font(.arabic(size: Layout.arabicSize, bold: true))
                        .foregroundStyle(theme.palette.onAccent)
                        .lineLimit(1)
                        .environment(\.layoutDirection, .rightToLeft)
                    Text(summary)
                        .font(.content(.translation, scale: scale))
                        .lineSpacing(ContentTextStyle.translationLineSpacing * scale)
                        .foregroundStyle(theme.palette.onAccentSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.forward")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(colors.tint)
                    .accessibilityHidden(true)
            }
            .multilineTextAlignment(.leading)
            .padding(Spacing.m)
            .readingTile(colors)
            .contentShape(.rect(cornerRadius: Radius.card))
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.wideScale))
        .accessibilityElement(children: .combine)
        .accessibilityValue(isDone ? Text("makharij.zikr.done") : Text(verbatim: ""))
    }

    /// «6 слов · …» - число слов и что в зикре встретится из курса (с арабскими вставками).
    private var summary: AttributedString {
        AttributedString(localized: "makharij.zikr.wordCount \(zikr.words.count)")
            + AttributedString(" · ")
            + ReadingText.withArabicRuns(zikr.summary, baseSize: ContentTextStyle.translation.baseSize * scale)
    }

    private func badge(isDone: Bool) -> some View {
        ZStack {
            if isDone {
                Circle().fill(theme.palette.success)
                Image(systemName: "checkmark")
                    .font(.system(size: Layout.check, weight: .bold))
                    .foregroundStyle(theme.palette.onAccent)
            } else {
                Circle()
                    .fill(colors.tile)
                    .strokeBorder(colors.tint, lineWidth: Size.hairline)
                Text(verbatim: number.formatted(.arabicIndic))
                    .font(.arabic(size: Layout.numberSize, bold: true))
                    .foregroundStyle(colors.tint)
            }
        }
        .frame(width: circle, height: circle)
        .accessibilityHidden(true)
    }
}
