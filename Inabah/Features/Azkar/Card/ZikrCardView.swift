import SwiftUI

/// Карточка зикра: пергамент с арабским текстом, перевод, действия и счётчик.
///
/// После выполнения сверху раскрывается заголовок с ✓, а остальная карточка складывается
/// под него; кнопка ⌄ раскладывает её обратно из-под заголовка. Оба блока всегда в иерархии
/// и меняют только высоту (`collapsible`) — лента не прыгает и ничего не «уезжает» поверх.
struct ZikrCardView: View {
    let session: ZikrSession
    /// Состояние записи этого зикра — вычисляет лента. Карточка не читает плеер сама:
    /// иначе любое изменение плеера (старт, загрузка, повтор) перерисовывало бы все карточки
    /// с тяжёлой арабской вёрсткой, а так — только ту, у которой состояние поменялось.
    let audio: ZikrAudioState

    @Environment(ReadingSettings.self) private var settings
    /// Только для команды ▶ — в `body` свойства плеера не читаются.
    @Environment(AudioPlayerController.self) private var player
    @Environment(\.theme) private var theme

    /// Свёрнута ли выполненная карточка. Отстаёт от `session.isCompleted` на `Motion.collapseDelay`,
    /// чтобы была видна вспышка «готово». Начальное значение — одноразовое: при повторном появлении
    /// (прокрутка, возврат на экран) выполненная карточка сразу свёрнута.
    @State private var isCollapsed: Bool

    init(session: ZikrSession, audio: ZikrAudioState = .idle) {
        self.session = session
        self.audio = audio
        _isCollapsed = State(initialValue: session.isCompleted)
    }

    private var showsFullContent: Bool { !isCollapsed || session.isExpanded }
    /// Эта запись сейчас звучит (или на паузе, но не доиграла).
    private var isAudioActive: Bool { audio != .idle }
    private var isPlaylistCurrent: Bool { audio.isInPlaylist }

    var body: some View {
        VStack(spacing: 0) {
            ZikrMiniRow(
                arabic: Self.miniRowPreview(of: session.zikr.arabic),
                isExpanded: session.isExpanded,
                onReset: reset,
                onToggleExpanded: {
                    withAnimation(Motion.collapse) { session.isExpanded.toggle() }
                }
            )
            .overlay(alignment: .bottom) {
                separator.opacity(session.isExpanded ? 1 : 0)
            }
            .collapsible(isExpanded: isCollapsed)

            fullContent
                .collapsible(isExpanded: showsFullContent)
        }
        .clipShape(.rect(cornerRadius: Radius.card))
        // Звучащая в плейлисте карточка подсвечена золотой рамкой. Фон и тень — у фигуры-подложки
        // (`surface`), а не у содержимого: иначе тень пересчитывалась бы на каждом кадре анимации.
        .surface(
            theme.palette.card,
            cornerRadius: Radius.card,
            border: isPlaylistCurrent ? theme.palette.gold : theme.palette.hairline,
            lineWidth: isPlaylistCurrent ? 2 : 1,
            shadow: .card(theme.palette)
        )
        .animation(Motion.highlight, value: isPlaylistCurrent)
        .sensoryFeedback(trigger: session.count) { old, new in
            guard new > old else { return nil }
            return session.isCompleted ? .success : .impact(weight: .light)
        }
        .task(id: session.isCompleted) {
            guard session.isCompleted else {
                if isCollapsed {
                    withAnimation(Motion.collapse) { isCollapsed = false }
                }
                return
            }
            guard !isCollapsed else { return }
            try? await Task.sleep(for: Motion.collapseDelay)
            guard !Task.isCancelled else { return }
            withAnimation(Motion.collapse) { isCollapsed = true }
        }
    }

    // Высота карточки меняется только внутри `withAnimation`: тогда в одну транзакцию попадает
    // и сворачивание карточки, и сдвиг соседних карточек в ленте. Модификатор `.animation(_:value:)`
    // на карточке анимировал бы только её содержимое — соседи прыгали бы на новое место сразу.

    private func reset() {
        withAnimation(Motion.collapse) { session.reset() }
    }

    /// Мини-строка показывает одну строку текста — достаточно первых слов: вёрстка и обрезка
    /// длинного арабского текста ради одной строки заметно дороже.
    private static func miniRowPreview(of arabic: String, words: Int = 8) -> String {
        let parts = arabic.split(whereSeparator: \.isWhitespace)
        guard parts.count > words else { return arabic }
        return parts.prefix(words).joined(separator: " ") + "…"
    }

    private var fullContent: some View {
        VStack(spacing: 0) {
            ParchmentPanel(topCornerRadius: isCollapsed ? 0 : Radius.card) {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    ArabicText(
                        text: session.zikr.arabic,
                        size: settings.arabicFontSize,
                        color: theme.palette.parchmentInk
                    )
                    repetitionsBadge
                }
            }

            if let translation = session.zikr.translation {
                ZikrTranslationView(translation: translation)
                    .padding(.horizontal, Spacing.xl)
                    .padding(.top, Spacing.l)
                    .collapsible(isExpanded: session.isTranslationVisible)
            }

            actions
                .padding(.vertical, Spacing.l)
            separator
            ZikrCounterButton(
                count: session.count,
                total: session.zikr.repetitions,
                action: { session.increment() }
            )
            .padding(.vertical, Spacing.xlPlus)
        }
    }

    private var separator: some View {
        Rectangle()
            .fill(theme.palette.divider)
            .frame(height: Size.hairline)
            .accessibilityHidden(true)
    }

    private var repetitionsBadge: some View {
        HStack(spacing: Spacing.xxs) {
            Text(verbatim: "✦").accessibilityHidden(true)
            Text("zikr.repetitions \(session.zikr.repetitions)")
        }
        .font(.caption2.weight(.semibold))
        .foregroundStyle(theme.palette.successDeep)
        .padding(.vertical, Spacing.xxxs)
        .padding(.horizontal, Spacing.s)
        .surface(theme.palette.successTint, cornerRadius: Radius.small, border: theme.palette.successBorder)
    }

    private var actions: some View {
        HStack(spacing: Spacing.l) {
            // Пока запись звучит — волна вместо ▶: повторное нажатие открывает плеер, а не ставит паузу.
            // Волна статичная: непрерывная анимация была единственной покадровой работой в ленте.
            Button {
                if let track = session.zikr.audioTrack() { player.play(track) }
            } label: {
                Image(systemName: isAudioActive ? "waveform" : "play.fill")
            }
            .buttonStyle(audioStyle)
            .accessibilityLabel(Text(isAudioActive ? "audio.zikr.openPlayer" : "audio.zikr.listen"))

            Button {
                withAnimation(Motion.collapse) { session.isTranslationVisible.toggle() }
            } label: {
                Text("zikr.translation.toggle.short")
                    .font(.subheadline.weight(.bold))
            }
            .buttonStyle(actionStyle(isActive: session.isTranslationVisible))
            .accessibilityLabel(Text("zikr.translation.toggle"))
            .accessibilityAddTraits(session.isTranslationVisible ? .isSelected : [])
            .disabled(session.zikr.translation == nil)

            Button("zikr.reset", systemImage: "arrow.counterclockwise", action: reset)
                .labelStyle(.iconOnly)
                .buttonStyle(actionStyle(isActive: false))
                .disabled(session.count == 0)

            ShareButton(iconSize: Self.shareIconSize) { session.zikr.shareText }
                .buttonStyle(actionStyle(isActive: false))
        }
    }

    /// Свой значок «Поделиться» — картинка, а не символ: размер задаётся рамкой, на глаз
    /// вровень с символами ряда (они — шрифтом `IconButtonStyle`).
    private static let shareIconSize: CGFloat = 21

    /// Как в прототипе: звучащая запись — зелёная кнопка.
    private var audioStyle: IconButtonStyle {
        IconButtonStyle(
            foreground: isAudioActive ? theme.palette.onAccent : theme.palette.textSecondary,
            background: isAudioActive ? theme.palette.success : theme.palette.actionBackground,
            border: isAudioActive ? theme.palette.successLight : theme.palette.hairline
        )
    }

    private func actionStyle(isActive: Bool) -> IconButtonStyle {
        IconButtonStyle(
            foreground: isActive ? theme.palette.onAccent : theme.palette.textSecondary,
            background: isActive ? theme.palette.accent : theme.palette.actionBackground,
            border: isActive ? theme.palette.accentLight : theme.palette.hairline
        )
    }
}
