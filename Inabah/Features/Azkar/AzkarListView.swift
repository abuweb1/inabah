import SwiftUI

/// Экран раздела: лента азкаров, шапка прогресса, оверлей завершения.
///
/// Тело экрана не читает ни плеер, ни счётчики зикров — это делают маленькие дочерние вьюхи
/// (`AzkarFeed`, `AzkarProgressHeader`, `AzkarCompletionWatcher`). Иначе нажатие на счётчик
/// или смена записи в плеере перестраивали бы весь экран вместе с 16 карточками.
struct AzkarListView: View {
    let section: AzkarSection

    @Environment(AzkarStore.self) private var store
    @Environment(ReadingSettings.self) private var settings
    @Environment(AppRouter.self) private var router
    @Environment(\.theme) private var theme

    @State private var showsCompletion = false

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background { section.background(in: theme).linear.ignoresSafeArea() }
            .overlay {
                if showsCompletion {
                    AzkarCompletionView(section: section) { router.popToRoot(.azkar) }
                        .transition(.opacity)
                        .accessibilityAddTraits(.isModal)
                }
            }
            .animation(Motion.overlay, value: showsCompletion)
            // После оверлея: мини-плеер остаётся над экраном завершения — последний зикр
            // плейлиста может ещё звучать.
            .audioPlayerInset()
            .background { AzkarCompletionWatcher(section: section, showsCompletion: $showsCompletion) }
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(section.headerColor(in: theme), for: .navigationBar)
            .toolbarBackgroundVisibility(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .principal) { titleView }
                FontSizeControls(settings: settings)
            }
            .task { await store.load(section) }
    }

    @ViewBuilder
    private var content: some View {
        switch store.state(of: section) {
        case .idle, .loading:
            ProgressView()
                .tint(theme.palette.onAccent)
        case .failed:
            ContentUnavailableView {
                Label("content.error.title", systemImage: "exclamationmark.triangle")
            } description: {
                Text("content.error.message")
            } actions: {
                Button("content.error.retry") {
                    Task { await store.load(section) }
                }
            }
            .foregroundStyle(theme.palette.onAccent)
        case .loaded(let sessions):
            AzkarFeed(section: section, sessions: sessions)
                .safeAreaInset(edge: .top, spacing: 0) {
                    AzkarProgressHeader(section: section)
                }
        }
    }

    private var titleView: some View {
        VStack(spacing: 0) {
            Text(section.title)
                .font(.headline)
            Text(section.subtitle)
                .font(.caption2)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .foregroundStyle(theme.palette.onAccent)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

/// Лента карточек. Единственная часть экрана, которая читает плеер: передаёт каждой карточке
/// готовое значение `ZikrAudioState` и прокручивается к звучащему зикру в режиме «Прослушать все».
private struct AzkarFeed: View {
    let section: AzkarSection
    let sessions: [ZikrSession]

    @Environment(AudioPlayerController.self) private var player

    /// Пользователь сейчас листает ленту — автопрокрутка не должна перехватывать прокрутку.
    @State private var isUserScrolling = false
    @State private var lastUserScroll: ContinuousClock.Instant?

    private static let autoScrollCooldown = Duration.seconds(2)

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                // Обычный VStack, не Lazy: карточки меняют высоту с анимацией, а LazyVStack
                // переставляет ленивые элементы без интерполяции. Зикров в разделе — десятки, это дёшево.
                VStack(spacing: Spacing.l) {
                    ForEach(sessions) { session in
                        ZikrCardView(session: session, audio: player.audioState(of: session.zikr))
                            .id(session.zikr.audioTrackID)
                    }
                    AzkarPlayAllCard(section: section, azkar: sessions.map(\.zikr))
                }
                .padding(Spacing.l)
            }
            .onScrollPhaseChange { _, phase in
                isUserScrolling = phase.isScrolling
                if phase.isScrolling { lastUserScroll = .now }
            }
            .onChange(of: player.currentTrack?.id) { _, trackID in
                guard let trackID, player.isPlaylistActive(section.playlistID), canAutoScroll else { return }
                withAnimation(Motion.collapse) {
                    proxy.scrollTo(trackID, anchor: .top)
                }
            }
        }
    }

    /// Автопрокрутка — только если пользователь не листает и не листал последние пару секунд.
    private var canAutoScroll: Bool {
        guard !isUserScrolling else { return false }
        guard let lastUserScroll else { return true }
        return ContinuousClock.now - lastUserScroll >= Self.autoScrollCooldown
    }
}

/// Показывает оверлей «Машаа Аллах!» с паузой после выполнения всех зикров раздела.
/// Отдельная невидимая вьюха: только она зависит от счётчиков всех зикров.
private struct AzkarCompletionWatcher: View {
    let section: AzkarSection
    @Binding var showsCompletion: Bool

    @Environment(AzkarStore.self) private var store

    var body: some View {
        let isFinished = store.progress(of: section).isFinished
        Color.clear
            .task(id: isFinished) {
                guard isFinished else {
                    store.resetCompletionAcknowledgement(of: section)
                    showsCompletion = false
                    return
                }
                // Один раз за прохождение: повторное открытие выполненного раздела оверлей не показывает.
                guard store.shouldPresentCompletion(of: section) else { return }
                // Пауза — чтобы последняя карточка успела показать «готово».
                try? await Task.sleep(for: Motion.completionDelay)
                guard !Task.isCancelled else { return }
                store.acknowledgeCompletion(of: section)
                showsCompletion = true
            }
            .accessibilityHidden(true)
    }
}

/// «Выполнено: N из M · P%» и полоса прогресса под навбаром.
private struct AzkarProgressHeader: View {
    let section: AzkarSection

    @Environment(AzkarStore.self) private var store
    @Environment(\.theme) private var theme

    var body: some View {
        let progress = store.progress(of: section)
        VStack(spacing: Spacing.xs) {
            HStack {
                Text("azkar.progress \(progress.completed) \(progress.total)")
                Spacer()
                // Половина — вверх, как Math.round в прототипе (2 из 16 → 13 %).
                Text(progress.fraction, format: .percent.precision(.fractionLength(0)).rounded(rule: .toNearestOrAwayFromZero))
                    .monospacedDigit()
            }
            .font(.caption)
            .foregroundStyle(theme.palette.onAccentSecondary)

            LinearProgressBar(
                fraction: progress.fraction,
                track: theme.palette.track,
                fill: theme.gradients.progressFill.linear
            )
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.top, Spacing.xs)
        .padding(.bottom, Spacing.m)
        .background(section.headerColor(in: theme))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("azkar.progress \(progress.completed) \(progress.total)"))
    }
}
