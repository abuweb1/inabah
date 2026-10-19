import SwiftUI

/// Лист буквы (Android `LetterSheet.kt`): плитка-пергамент, имя и номер буквы, пояснение (у алифа) и
/// цепочка «название → слоги → закрытые слоги» справа налево; слоги с ещё не пройденным знаком - пунктиром.
/// Свой слой поверх экрана, не системный `.sheet`: навбар не затемняется, высота - по содержимому.
/// Закрытие - «×», касание по затемнению или свайп вниз.
///
/// Записи цепочки (только те, что есть в сборке, и только у пройденных знаков): открыли лист - звучат сами
/// по порядку; «Прослушать» / «Стоп» в шапке; фишка с записью - играет только её. Закрыли лист, ушли,
/// приложение в фон - запись листа замолкает.
struct LetterSheet: View {
    static let scrimOpacity = 0.45

    let course: ReadingCourse
    let letter: ReadingLetter
    let stepNumber: Int
    let colors: ReadingColors
    let onClose: () -> Void

    @Environment(ReadingSettings.self) private var settings
    @Environment(ClipPlayer.self) private var clips
    @Environment(\.theme) private var theme
    @Environment(\.scenePhase) private var scenePhase

    @State private var dragOffset: CGFloat = 0
    /// Что звучит: вся цепочка (`files == nil`) или одна фишка; `nil` - тишина.
    @State private var request: ChainRequest?

    private enum Layout {
        static let cornerRadius: CGFloat = 24
        static let grabber = CGSize(width: 40, height: 5)
        static let tileBox: CGFloat = 84
        static let tileLetter: Double = 48
        static let arabicName: Double = 18
        static let closeButton: CGFloat = 44
        /// Как Android (48).
        static let playButton: CGFloat = 48
        /// Свайп вниз дальше этого - закрыть.
        static let dismissDistance: CGFloat = 96
        /// Пауза между записями цепочки.
        static let chainGap = Duration.milliseconds(400)
    }

    private var chain: [LetterChainItem] {
        ReadingLogic.letterChain(course: course, letter: letter, stepNumber: stepNumber)
    }

    /// Записи цепочки по порядку, которые есть в сборке.
    private var files: [String] { chain.playableFiles(available: clips.available) }

    private var isChainPlaying: Bool { request != nil && request?.files == nil && !files.isEmpty }

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
                ForEach(chain, id: \.self) { item in
                    let file = item.audioFile.flatMap { clips.available.contains($0) ? $0 : nil }
                    ChainChip(
                        item: item,
                        isTransliterationHidden: settings.transliterationHidden,
                        isPlaying: file != nil && clips.playing == file,
                        colors: colors,
                        onPlay: file.map { file in { request = ChainRequest(files: [file]) } }
                    )
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
        // Открыли лист - цепочка звучит сама.
        .onAppear { if !files.isEmpty { request = ChainRequest(files: nil) } }
        .task(id: request) { await playRequest() }
        .onDisappear(perform: stopOwn)
        .onChange(of: scenePhase) { _, phase in
            guard phase != .active else { return }
            request = nil
            stopOwn()
        }
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
            if !files.isEmpty {
                Button {
                    if isChainPlaying {
                        request = nil
                        stopOwn()
                    } else {
                        request = ChainRequest(files: nil)
                    }
                } label: {
                    Image(systemName: isChainPlaying ? "stop.fill" : "play.fill")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(colors.onTint)
                        .frame(width: Layout.playButton, height: Layout.playButton)
                        .background(colors.tint, in: .circle)
                }
                .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
                .accessibilityLabel(Text(isChainPlaying ? "makharij.letter.stop" : "makharij.letter.play"))
            }
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

    /// Записи по очереди: следующая - когда прошлая доиграла сама, через паузу; прервали, зазвучало другое -
    /// дальше не играть.
    private func playRequest() async {
        guard let request else { return }
        let queue = request.files ?? files
        for (index, file) in queue.enumerated() {
            if index > 0 {
                try? await Task.sleep(for: Layout.chainGap)
                guard !Task.isCancelled, clips.playing == nil else { return }
            }
            guard !Task.isCancelled, await clips.playToEnd(file) else { return }
        }
        if !Task.isCancelled { self.request = nil }
    }

    /// Остановить только запись этого листа (не зикр и не другое).
    private func stopOwn() {
        if let playing = clips.playing, files.contains(playing) { clips.stop() }
    }
}

/// Что играть: вся цепочка (`files == nil`) или одна фишка. Новое нажатие - новый запрос, даже тот же.
private struct ChainRequest: Equatable {
    let id = UUID()
    let files: [String]?
}

/// Фишка цепочки: слог (или название) и транскрипция, подпись - на верхней рамке. С записью -
/// нажимается (играет только её), звучащая - в цвет раздела.
private struct ChainChip: View {
    let item: LetterChainItem
    let isTransliterationHidden: Bool
    let isPlaying: Bool
    let colors: ReadingColors
    let onPlay: (() -> Void)?

    @Environment(\.theme) private var theme
    @State private var taps = 0

    private enum Layout {
        static let minWidth: CGFloat = 60
        static let arabic: Double = 26
        /// Подпись заходит на рамку: тело фишки ниже на столько.
        static let labelOverlap: CGFloat = 8
        static let lockedOpacity = 0.45
        static let dash: [CGFloat] = [4, 4]
    }

    var body: some View {
        if let onPlay {
            Button {
                taps += 1
                onPlay()
            } label: {
                chip
            }
            .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
            .sensoryFeedback(.selection, trigger: taps)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: accessibilityLabel))
            .accessibilityAction(named: Text("makharij.word.listen"), onPlay)
        } else {
            chip
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(verbatim: accessibilityLabel))
        }
    }

    private var chip: some View {
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
    }

    @ViewBuilder
    private var border: some View {
        let shape = RoundedRectangle(cornerRadius: Radius.box)
        if item.isLearned {
            shape
                .fill(isPlaying ? colors.highlight : colors.tile)
                .strokeBorder(isPlaying ? colors.highlightBorder : colors.tileBorder, lineWidth: ReadingMetrics.tileBorder)
        } else {
            shape.strokeBorder(colors.tileBorder, style: StrokeStyle(lineWidth: ReadingMetrics.tileBorder, dash: Layout.dash))
        }
    }

    /// «название» или название знака слога («фатхьа»; у закрытого слога - «сукун»).
    private var label: String {
        switch item.kind {
        case .name: String(localized: "makharij.chain.name")
        case .syllable, .closed: item.markName ?? ""
        }
    }

    /// Транскрипцию VoiceOver читает и при скрытом глазе.
    private var accessibilityLabel: String {
        item.isLearned
            ? "\(label), \(item.transliteration)"
            : String(localized: "makharij.chain.locked \(label) \(item.transliteration)")
    }
}
