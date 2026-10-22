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

    private static let reminderIconSize: CGFloat = 20
    private static let titleSize: CGFloat = 16

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
                // Заголовок — слева, сразу за «назад» (решение пользователя 2026-10-04, как в Android):
                // по центру между «назад» и А−/А+ ему тесно. Без стеклянной подложки элемента.
                if #available(iOS 26, *) {
                    ToolbarItem(placement: .topBarLeading) { titleView }
                        .sharedBackgroundVisibility(.hidden)
                } else {
                    ToolbarItem(placement: .topBarLeading) { titleView }
                }
                // «Сделать напоминание» — отдельной круглой кнопкой слева от «А− А+» (решение
                // пользователя 2026-10-06). Чтобы на узких iPhone (390 pt) «А− А+» не уходили
                // в меню «…», их капсула компактная, а заголовок — чуть меньше `headline`.
                ToolbarItem(placement: .topBarTrailing) {
                    ShareButton(title: "reminder.action", iconSize: Self.reminderIconSize) {
                        section.reminderText
                    }
                    .headerIconButtonStyle(tint: theme.palette.tabAzkar, header: section.headerColor(in: theme))
                    .fixedTextSize()
                }
                if #available(iOS 26, *) {
                    ToolbarSpacer(.fixed, placement: .topBarTrailing)
                }
                FontSizeControls(
                    settings: settings,
                    compact: true,
                    tint: theme.palette.tabAzkar,
                    header: section.headerColor(in: theme)
                )
            }
            .glassBackButton(tint: theme.palette.tabAzkar, header: section.headerColor(in: theme))
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
                    AzkarTopPanel(section: section)
                }
        }
    }

    private var titleView: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Чуть меньше `headline` (17): рядом с «Сделать напоминание» и «А− А+» на узких
            // iPhone (390 pt) иначе «А− А+» уходят в меню «…» (решение пользователя 2026-10-06).
            Text(section.title)
                .font(.system(size: Self.titleSize, weight: .semibold))
            Text(section.subtitle)
                .font(.caption2)
                .foregroundStyle(theme.palette.onAccentSecondary)
        }
        .foregroundStyle(theme.palette.onAccent)
        // Элемент слева навбар сжимает до многоточия — заголовку нужна его натуральная ширина.
        .fixedSize()
        // Заголовок экрана не меняется с размером интерфейса.
        .fixedTextSize()
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
    /// Виден ли на экране верх каждой карточки. Не `@Observable`: меняется при прокрутке,
    /// а читается только в момент сворачивания — лента из-за него не перерисовывается.
    @State private var cardTops = CardTopVisibility()
    /// Верх видимой части ленты в её координатах — тоже обычный класс: пишется на каждом кадре прокрутки.
    @State private var visibleTop = FeedVisibleTop()
    @State private var scrollPosition = ScrollPosition()

    private static let autoScrollCooldown = Duration.seconds(2)

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                // Обычный VStack, не Lazy: карточки меняют высоту с анимацией, а LazyVStack
                // переставляет ленивые элементы без интерполяции. Зикров в разделе — десятки, это дёшево.
                // Промежуток над каждой карточкой — `CardTopMarker` (цель прокрутки при сворачивании),
                // поэтому у стека нет своего интервала и отступа сверху.
                VStack(spacing: 0) {
                    ForEach(sessions) { session in
                        let trackID = session.zikr.audioTrackID
                        CardTopMarker(id: trackID, tops: cardTops)
                        ZikrCardView(
                            session: session,
                            audio: player.audioState(of: session.zikr),
                            onAutoCollapse: { revealCollapsed(trackID, proxy: proxy) },
                            onHideTranslation: keepActionsInPlace
                        )
                        .id(trackID)
                    }
                    // Записей нет — вместо «Прослушать все» заглушка «Аудио скоро».
                    Group {
                        if sessions.contains(where: { $0.zikr.hasAudio }) {
                            AzkarPlayAllCard(section: section, azkar: sessions.map(\.zikr))
                        } else {
                            AudioSoonPlaceholder(title: Text("audio.playAll.title"))
                        }
                    }
                    .padding(.top, Spacing.l)
                }
                .padding([.horizontal, .bottom], Spacing.l)
                .coordinateSpace(.azkarFeed)
            }
            .scrollPosition($scrollPosition)
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                geometry.contentOffset.y + geometry.contentInsets.top
            } action: { _, top in
                visibleTop.y = top
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

    /// Дочитан длинный зикр, а его верх уже за краем экрана — лента в той же анимации подъезжает
    /// к свёрнутой строке (на отступ ленты ниже края), сразу под ней начало следующего. Иначе
    /// следующий уезжал вверх и приходилось листать назад (как на Android, `12a/12b`).
    /// Вызывается внутри `withAnimation` карточки; палец прерывает прокрутку.
    private func revealCollapsed(_ trackID: AudioTrack.ID, proxy: ScrollViewProxy) {
        guard cardTops.isHidden(trackID) else { return }
        proxy.scrollTo(CardTopMarker.Anchor(trackID: trackID), anchor: .top)
    }

    /// Скрыт перевод, начало которого уже за верхним краем экрана: без прокрутки кнопка «Аа» уезжала
    /// вверх на высоту перевода, и под пальцем оказывались следующие карточки. Лента в той же
    /// анимации поднимается на ту же высоту — «Аа» остаётся на месте. Перевод виден целиком — не трогаем.
    private func keepActionsInPlace(_ translation: CGRect) {
        let top = visibleTop.y
        guard translation.minY < top else { return }
        scrollPosition.scrollTo(y: max(0, top - translation.height))
    }
}

/// Верх видимой части ленты (смещение прокрутки с учётом отступа под навбаром).
private final class FeedVisibleTop {
    var y: CGFloat = 0
}

extension CoordinateSpaceProtocol where Self == NamedCoordinateSpace {
    /// Координаты содержимого ленты азкаров: не меняются при прокрутке.
    nonisolated static var azkarFeed: NamedCoordinateSpace { .named("azkarFeed") }
}

/// Видимость верха карточек ленты — обычный класс: запись при прокрутке не перерисовывает ленту.
private final class CardTopVisibility {
    private var hidden: Set<AudioTrack.ID> = []

    func set(_ trackID: AudioTrack.ID, visible: Bool) {
        if visible { hidden.remove(trackID) } else { hidden.insert(trackID) }
    }

    func isHidden(_ trackID: AudioTrack.ID) -> Bool { hidden.contains(trackID) }
}

/// Промежуток ленты над карточкой: цель прокрутки (карточка встаёт на отступ ниже края,
/// как на Android) и признак «верх карточки на экране».
private struct CardTopMarker: View {
    struct Anchor: Hashable {
        let trackID: AudioTrack.ID
    }

    let id: AudioTrack.ID
    let tops: CardTopVisibility

    var body: some View {
        Color.clear
            .frame(height: Spacing.l)
            .id(Anchor(trackID: id))
            .onScrollVisibilityChange(threshold: Self.visibleShare) { tops.set(id, visible: $0) }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    /// Верх «на экране», только если видна полоса над ним почти целиком.
    private static let visibleShare = 0.9
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

/// Под навбаром: во время азкаров — прогресс, вне его — когда время азкаров.
/// Меняется только на границах времени, счётчики не читает.
private struct AzkarTopPanel: View {
    let section: AzkarSection

    @Environment(AzkarStore.self) private var store

    var body: some View {
        if store.isInWindow(section) {
            AzkarProgressHeader(section: section)
        } else {
            AzkarWindowNotice(section: section)
        }
    }
}

/// «Время утренних азкаров — с 5:00 до 12:00»: счёт работает, но в прогресс не идёт.
private struct AzkarWindowNotice: View {
    let section: AzkarSection

    @Environment(AzkarStore.self) private var store
    @Environment(AzkarWindowSettings.self) private var windowSettings
    @Environment(\.theme) private var theme

    var body: some View {
        Label {
            section.windowNotice(windowSettings.window(for: section), in: store.calendar)
        } icon: {
            Image(systemName: "clock")
        }
        .font(.caption)
        .foregroundStyle(theme.palette.onAccentSecondary)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.xl)
        .padding(.top, Spacing.xs)
        .padding(.bottom, Spacing.m)
        .background(section.headerColor(in: theme))
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
