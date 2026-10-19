import SwiftUI

// Блоки урока «Учимся читать» (Android `ReadingLessonScreen.kt`): общие для урока, «Весь алфавит» и справки.
// Арабский - фиксированного кегля Scheherazade (`Font.arabic`), русский - стилями интерфейса и переводов.

/// Что умеют блоки урока: открыть лист буквы, карточку буквы, «Весь алфавит», тему.
struct LessonActions {
    let openSheet: LetterSheetOpener
    var openLetterForms: (ReadingLetter) -> Void = { _ in }
    var openAlphabet: () -> Void = {}
    var openTopic: (Int) -> Void = { _ in }
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
    let onTap: (ReadingLetter) -> Void

    static let defaultColumns = 5

    @Environment(\.theme) private var theme
    @ScaledMetric(relativeTo: .body) private var tileHeight: CGFloat = 64

    private static let letterSize: Double = 30

    var body: some View {
        VStack(spacing: Spacing.s) {
            ForEach(Array(ReadingLogic.paddedRows(letters, columns: columns).enumerated()), id: \.offset) { _, row in
                WeightedRow(spacing: Spacing.s) {
                    ForEach(Array(row.enumerated()), id: \.offset) { _, letter in
                        if let letter {
                            Button { onTap(letter) } label: {
                                Text(verbatim: letter.letter)
                                    .font(.arabic(size: Self.letterSize))
                                    .foregroundStyle(theme.palette.onAccent)
                                    .fixedSize()
                                    .frame(maxWidth: .infinity, minHeight: tileHeight)
                                    .readingTile(colors, cornerRadius: Radius.box, lineWidth: ReadingMetrics.tileBorder)
                                    .contentShape(.rect(cornerRadius: Radius.box))
                            }
                            .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
                            .accessibilityLabel(Text(verbatim: letter.name))
                        } else {
                            Color.clear.frame(height: tileHeight)
                        }
                    }
                }
            }
        }
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
    let colors: ReadingColors
    let onOpen: (ReadingLetter) -> Void

    @Environment(\.theme) private var theme

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
                    Button { onOpen(letter) } label: {
                        ExampleWordText(word: word, letter: letter, form: form, size: Layout.word, colors: colors)
                            .padding(.top, Spacing.xs)
                            .padding(.bottom, Spacing.xxs)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .parchmentCell(colors)
                            .contentShape(.rect(cornerRadius: Radius.box))
                    }
                    .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
                    .accessibilityLabel(Text(verbatim: word))
                }
            }
        }
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

/// Все формулы - карточками (в одну колонку - по 3, иначе по 4 ряда): «Весь алфавит» закрытых слогов.
struct FormulasCards: View {
    let block: ReadingBlock.Formulas
    let isTransliterationHidden: Bool
    let colors: ReadingColors

    private enum Layout {
        static let wideFormulasPerCard = 3
        static let rowsPerCard = 4
    }

    var body: some View {
        let perCard = block.columns == 1 ? Layout.wideFormulasPerCard : block.columns * Layout.rowsPerCard
        VStack(spacing: Spacing.m) {
            ForEach(Array(stride(from: 0, to: block.items.count, by: perCard)), id: \.self) { start in
                LessonFormulasBlock(
                    items: Array(block.items[start..<min(start + perCard, block.items.count)]),
                    columns: block.columns,
                    isTransliterationHidden: isTransliterationHidden,
                    colors: colors
                )
            }
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
