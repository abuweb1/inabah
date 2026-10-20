import SwiftUI

/// Карточка буквы (Android `ReadingLetterFormsScreen.kt`): буквы группы - полосой и страницами справа
/// налево; на странице - буква, её формы и слова-примеры с выделенной буквой. Лампочка - транскрипция слов
/// на 0,7 с; долгое нажатие на слово - увеличение; есть запись слова - 🔈, нажатие играет её.
struct ReadingLetterFormsView: View {
    let stepNumber: Int
    let letter: String

    @Environment(ReadingStore.self) private var store

    /// Буква открытой страницы.
    @State private var current: String?
    @State private var hintRequest = 0
    @State private var isHintShown = false

    var body: some View {
        ReadingScreen(
            stepNumber: stepNumber,
            title: { course in
                currentLetter(course).map { Text(verbatim: $0.name) } ?? Text("makharij.forms.title")
            },
            subtitle: { course in
                let letters = Self.forms(course?.step(stepNumber))?.letters ?? []
                guard let index = letters.firstIndex(where: { $0.letter == (current ?? letter) }) else { return nil }
                return Text("makharij.forms.position \(index + 1) \(letters.count)")
            },
            showsTransliterationToggle: false,
            intro: .letterCard,
            actions: {
                if hasTransliterations {
                    ReadingHeaderButton(symbol: "lightbulb", label: "makharij.words.hint") { hintRequest += 1 }
                }
            },
            content: { course, colors, _ in
                if let forms = Self.forms(course.step(stepNumber)) {
                    LetterFormsContent(
                        forms: forms, stepNumber: stepNumber, stepLetter: letter, colors: colors,
                        isHintShown: isHintShown, current: $current
                    )
                } else {
                    ReadingLessonSoon()
                }
            }
        )
        // Повторное нажатие на лампочку - снова 0,7 с.
        .task(id: hintRequest) {
            guard hintRequest > 0 else { return }
            withAnimation { isHintShown = true }
            try? await Task.sleep(for: Motion.transliterationHint)
            guard !Task.isCancelled else { return }
            withAnimation { isHintShown = false }
        }
    }

    private var hasTransliterations: Bool {
        Self.forms(store.course.value?.step(stepNumber))?.examples.values.contains { !$0.transliterations.isEmpty } ?? false
    }

    private func currentLetter(_ course: ReadingCourse?) -> ReadingLetter? {
        Self.forms(course?.step(stepNumber))?.letters.first { $0.letter == (current ?? letter) }
    }

    /// Таблица форм шага - первая.
    private static func forms(_ step: ReadingStep?) -> ReadingBlock.Forms? {
        step?.blocks.lazy.compactMap { if case .forms(let forms) = $0 { forms } else { nil } }.first
    }
}

private struct LetterFormsContent: View {
    let forms: ReadingBlock.Forms
    let stepNumber: Int
    /// Буква, с которой открыли карточку.
    let stepLetter: String
    let colors: ReadingColors
    let isHintShown: Bool
    @Binding var current: String?

    @Environment(ClipPlayer.self) private var clips
    @Environment(ReadingSettings.self) private var settings
    @State private var zoom: ExampleZoom?

    private static let space = "letterForms"

    var body: some View {
        VStack(spacing: 0) {
            GroupStrip(letters: forms.letters, current: current ?? stepLetter, colors: colors) { letter in
                withAnimation(Motion.collapse) { current = letter.letter }
            }
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 0) {
                        ForEach(forms.letters, id: \.letter) { letter in
                            LetterFormsPage(
                                letter: letter,
                                examples: forms.examples[letter],
                                colors: colors,
                                isHintShown: isHintShown,
                                zoomedForm: zoom?.letter == letter ? zoom?.form : nil,
                                coordinateSpace: Self.space,
                                audio: { audio(letter, $0) }
                            ) { form, frame in
                                settings.markZoomLearned()
                                zoom = ExampleZoom(letter: letter, form: form, source: frame)
                            }
                            .environment(\.layoutDirection, .leftToRight)
                            .containerRelativeFrame(.horizontal)
                            .id(letter.letter)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.paging)
                .scrollPosition(id: $current)
                .scrollIndicators(.hidden)
                // Следующая буква - слева, как в арабском письме и полосе букв.
                .environment(\.layoutDirection, .rightToLeft)
                .onAppear {
                    // iOS 18 не применяет начальную `scrollPosition` - листаем сами.
                    if current == nil { current = stepLetter }
                    proxy.scrollTo(current ?? stepLetter)
                }
            }
        }
        .coordinateSpace(.named(Self.space))
        .overlay {
            if let zoom, let examples = forms.examples[zoom.letter], let word = examples.word(zoom.form) {
                let audio = audio(zoom.letter, zoom.form)
                TileZoomOverlay(
                    source: zoom.source,
                    sourceTextSize: ExampleWordCell.wordSize,
                    zoomedTextSize: ExampleWordCell.zoomedWordSize,
                    onDismissed: { self.zoom = nil },
                    // Увеличенное слово с записью - нажатие играет её.
                    onTileTap: audio.map { file in { clips.play(file) } }
                ) {
                    ExampleWordCell(
                        word: word, letter: zoom.letter, form: zoom.form,
                        transliteration: examples.transliteration(zoom.form),
                        isHintShown: isHintShown, isZoomed: true, colors: colors, hasAudio: audio != nil,
                        isPlaying: audio != nil && clips.playing == audio
                    )
                }
            }
        }
    }

    /// Запись слова-примера, если она есть в сборке.
    private func audio(_ letter: ReadingLetter, _ form: LetterForm) -> String? {
        let file = ReadingLogic.formExampleAudio(step: stepNumber, letter: letter, form: form)
        return clips.available.contains(file) ? file : nil
    }
}

/// Увеличенное слово-пример: какая буква, форма и откуда летит.
private struct ExampleZoom: Equatable {
    let letter: ReadingLetter
    let form: LetterForm
    let source: CGRect
}

/// Полоса букв группы справа налево; текущая - выделена, нажатие - к её странице.
private struct GroupStrip: View {
    let letters: [ReadingLetter]
    let current: String
    let colors: ReadingColors
    let onSelect: (ReadingLetter) -> Void

    @Environment(\.theme) private var theme

    private enum Layout {
        /// Как Android: не меньше пальца (48).
        static let chip: CGFloat = 48
        static let letter: Double = 20
    }

    var body: some View {
        ViewThatFits(in: .horizontal) {
            chips
            ScrollView(.horizontal) { chips }
                .scrollIndicators(.hidden)
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.s)
        .environment(\.layoutDirection, .rightToLeft)
    }

    private var chips: some View {
        HStack(spacing: Spacing.xs) {
            ForEach(letters, id: \.letter) { letter in
                let isCurrent = letter.letter == current
                Button { onSelect(letter) } label: {
                    Text(verbatim: letter.letter)
                        .font(.arabic(size: Layout.letter))
                        .foregroundStyle(theme.palette.onAccent)
                        .frame(width: Layout.chip, height: Layout.chip)
                        .readingTile(colors, cornerRadius: Radius.control, lineWidth: ReadingMetrics.tileBorder, isHighlighted: isCurrent)
                        .contentShape(.rect(cornerRadius: Radius.control))
                }
                .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
                .accessibilityLabel(Text(verbatim: letter.name))
                .accessibilityAddTraits(isCurrent ? .isSelected : [])
            }
        }
    }
}

/// Страница буквы: пергамент с буквой, тремя формами и словами-примерами.
private struct LetterFormsPage: View {
    let letter: ReadingLetter
    let examples: FormExamples?
    let colors: ReadingColors
    let isHintShown: Bool
    /// Форма слова, которое сейчас увеличено - его ячейка «вылетела» и пуста.
    let zoomedForm: LetterForm?
    let coordinateSpace: String
    /// Запись слова формы; нет - `nil`.
    let audio: (LetterForm) -> String?
    let onZoom: (LetterForm, CGRect) -> Void

    @Environment(\.theme) private var theme

    private enum Layout {
        static let bigLetter: Double = 84
        static let form: Double = 34
    }

    var body: some View {
        ScrollView {
            ParchmentPanel(bottomCornerRadius: Radius.card) {
                VStack(spacing: Spacing.s) {
                    Text(verbatim: letter.letter)
                        .font(.arabic(size: Layout.bigLetter, bold: true))
                        .foregroundStyle(theme.palette.parchmentText)
                        .fixedSize()
                        .frame(maxWidth: .infinity)
                        .accessibilityLabel(Text(verbatim: letter.name))
                    WeightedRow(spacing: Spacing.xs) {
                        ForEach(LetterForm.placed, id: \.self) { form in
                            VStack(spacing: 0) {
                                Text(verbatim: letter.form(form))
                                    .font(.arabic(size: Layout.form, bold: true))
                                    .foregroundStyle(theme.palette.parchmentText)
                                    .fixedSize()
                                Text(form.caption)
                                    .font(.caption2)
                                    .foregroundStyle(colors.parchmentCaption)
                            }
                            .padding(.vertical, Spacing.xs)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .parchmentCell(colors)
                            .accessibilityElement(children: .combine)
                        }
                    }
                    if let examples {
                        WeightedRow(spacing: Spacing.xs) {
                            ForEach(LetterForm.placed, id: \.self) { form in
                                if let word = examples.word(form) {
                                    ZoomableExampleWord(
                                        word: word, letter: letter, form: form,
                                        transliteration: examples.transliteration(form),
                                        isHintShown: isHintShown, colors: colors,
                                        coordinateSpace: coordinateSpace, audio: audio(form)
                                    ) { frame in onZoom(form, frame) }
                                    .opacity(zoomedForm == form ? 0 : 1)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.s)
            .padding(.bottom, Spacing.xl)
        }
    }
}

/// Слово-пример страницы: долгое нажатие - увеличение (рамка ячейки - в координатах карточки).
private struct ZoomableExampleWord: View {
    let word: String
    let letter: ReadingLetter
    let form: LetterForm
    let transliteration: String?
    let isHintShown: Bool
    let colors: ReadingColors
    let coordinateSpace: String
    /// Запись слова: есть - нажатие играет её (без записи ячейка не нажимается).
    let audio: String?
    let onZoom: (CGRect) -> Void

    @Environment(ClipPlayer.self) private var clips
    @Environment(\.tapIntro) private var intro
    @State private var frame: CGRect = .zero
    @State private var zoomCount = 0
    @State private var playCount = 0
    @State private var flash: TapFlash?
    /// Палец держит слово - идёт кольцо удержания.
    @GestureState private var holding = false

    var body: some View {
        ExampleWordCell(
            word: word, letter: letter, form: form, transliteration: transliteration,
            isHintShown: isHintShown, isZoomed: false, colors: colors, hasAudio: audio != nil,
            isPlaying: audio != nil && clips.playing == audio
        )
        .tapFlash(flash, tint: colors.tint)
        .holdRing(pressing: holding, tint: colors.tint)
        .liveCell(hint: "makharij.hint.zoom", tint: colors.tint, zoomable: true, observesTaps: false)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(coordinateSpace)) } action: { frame = $0 }
        .contentShape(.rect(cornerRadius: Radius.box))
        // Один жест: удержание - увеличение, отпустили раньше - касание (отдельные спорили, и увеличение
        // срабатывало только при отпускании пальца).
        .gesture(
            LongPressGesture(minimumDuration: PressFeedback.zoomHold)
                .updating($holding) { _, state, _ in state = true }
                .onEnded { _ in
                    zoomCount += 1
                    intro?.tappedCell()
                    onZoom(frame)
                }
                .exclusively(before: SpatialTapGesture().onEnded { tap in
                    flash = flash?.next(at: tap.location) ?? .first(at: tap.location)
                    intro?.tappedCell()
                    play()
                })
        )
        .sensoryFeedback(.impact(weight: .medium), trigger: zoomCount)
        .sensoryFeedback(.selection, trigger: playCount)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: isHintShown ? [word, transliteration].compactMap { $0 }.joined(separator: ", ") : word))
        .accessibilityAction(named: Text("makharij.words.zoomAction")) { onZoom(frame) }
        .modifier(ListenAction(isEnabled: audio != nil, action: play))
    }

    private func play() {
        guard let audio else { return }
        playCount += 1
        clips.play(audio)
    }
}

/// VoiceOver-действие «Послушать» - только у слова с записью.
struct ListenAction: ViewModifier {
    let isEnabled: Bool
    let action: () -> Void

    func body(content: Content) -> some View {
        if isEnabled {
            content.accessibilityAction(named: Text("makharij.word.listen"), action)
        } else {
            content
        }
    }
}

/// Ячейка слова-примера: слово с выделенной буквой и транскрипция (место есть всегда, видна по лампочке).
struct ExampleWordCell: View {
    static let wordSize: Double = 26
    static let zoomedWordSize: Double = 60

    let word: String
    let letter: ReadingLetter
    let form: LetterForm
    let transliteration: String?
    let isHintShown: Bool
    let isZoomed: Bool
    let colors: ReadingColors
    /// Есть запись слова - 🔈 в углу.
    var hasAudio = false
    /// Звучит запись слова - подсветка цветом раздела.
    var isPlaying = false

    @Environment(\.theme) private var theme

    private enum Layout {
        /// Не влезает - мельче, до caption2 (11 из 15 и из 22).
        static let minScale: CGFloat = 0.73
        static let zoomedMinScale: CGFloat = 0.5
    }

    var body: some View {
        VStack(spacing: 0) {
            ExampleWordText(
                word: word, letter: letter, form: form,
                size: isZoomed ? Self.zoomedWordSize : Self.wordSize, colors: colors
            )
            Text(verbatim: transliteration ?? " ")
                .font(isZoomed ? .title2 : .subheadline)
                .foregroundStyle(colors.parchmentCaption)
                .lineLimit(1)
                .minimumScaleFactor(isZoomed ? Layout.zoomedMinScale : Layout.minScale)
                .opacity(isHintShown && transliteration != nil ? 1 : 0)
        }
        .padding(.top, Spacing.xs)
        .padding(.bottom, Spacing.xxs)
        .padding(.horizontal, Spacing.xxs)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            if isZoomed {
                RoundedRectangle(cornerRadius: Radius.box).fill(theme.gradients.parchment.linear)
            }
        }
        .parchmentCell(colors, isHighlighted: isPlaying)
        .overlay(alignment: .topLeading) {
            if hasAudio { ExampleWordSpeaker(colors: colors) }
        }
    }
}

/// 🔈 слова-примера с записью (Android `ExampleWordCell`): сверху слева, при звучании не меняется.
struct ExampleWordSpeaker: View {
    let colors: ReadingColors

    @ScaledMetric(relativeTo: .body) private var size: CGFloat = 12

    var body: some View {
        Image(systemName: "speaker.wave.2.fill")
            .font(.system(size: size))
            .foregroundStyle(colors.parchmentCaption)
            .padding(Spacing.xxs)
            .accessibilityHidden(true)
    }
}
