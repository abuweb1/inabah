import SwiftUI

/// Главная раздела «Азкары»: бренд, аят Аз-Зумар 39:54 и переход к утренним/вечерним азкарам.
struct AzkarHomeView: View {
    @Environment(AzkarStore.self) private var store
    @Environment(AzkarWindowSettings.self) private var windowSettings
    @Environment(ReminderSettings.self) private var reminders
    @Environment(\.theme) private var theme

    private static let brand = SectionBrand(
        arabicName: "إنابة",
        latinName: "home.brand.latin",
        tagline: "home.brand.tagline"
    )

    private static let verse = FeaturedVerse(
        arabic: "وَأَنِيبُوا إِلَىٰ رَبِّكُمْ وَأَسْلِمُوا لَهُ",
        translation: "home.verse.translation",
        reference: "home.verse.reference"
    )

    var body: some View {
        SectionHomeLayout(
            background: theme.gradients.azkarBackground,
            brand: Self.brand,
            verse: Self.verse
        ) {
            // Серия дней — первой, под аятом, по центру (решение пользователя 2026-10-11).
            AzkarStreakCapsule()
            ForEach(AzkarSection.allCases, id: \.self) { section in
                NavigationLink(value: AzkarRoute.list(section)) {
                    SectionNavCard(
                        title: section.title,
                        meta: meta(for: section),
                        symbolName: section.symbolName,
                        iconColor: section.iconColor(in: theme),
                        gradient: section.cardGradient(in: theme),
                        shadow: section.cardShadow(in: theme),
                        ring: ring(for: section),
                        trailingNote: windowNote(for: section)
                    )
                }
                .buttonStyle(PressScaleButtonStyle())
            }
            // Одно ненавязчивое предложение (решение пользователя 2026-10-10): пока на него не ответили.
            if !reminders.offerAnswered {
                ReminderOfferCard()
            }
        } topAction: {
            QiblaEntryButton()
        } leadingAction: {
            UpdateButton()
        }
        .audioPlayerInset()
        .toolbarVisibility(.hidden, for: .navigationBar)
    }

    /// Выполнение раздела — только во время азкаров; до загрузки кольца нет.
    private func ring(for section: AzkarSection) -> NavCardRing? {
        let progress = store.progress(of: section)
        guard progress.total > 0, store.isInWindow(section) else { return nil }
        return NavCardRing(fraction: progress.fraction, style: section.ringStyle(in: theme))
    }

    /// Вне времени азкаров вместо кольца — само время («5:00–12:00»).
    private func windowNote(for section: AzkarSection) -> Text? {
        guard store.state(of: section).value != nil, !store.isInWindow(section) else { return nil }
        return windowSettings.window(for: section).rangeText(in: store.calendar)
    }

    /// Количество зикров известно после загрузки; до неё карточка без подписи.
    private func meta(for section: AzkarSection) -> LocalizedStringResource? {
        let count = store.sessions(in: section).count
        return count > 0 ? section.cardMeta(count: count) : nil
    }
}

/// «Напоминать об азкарах?» — «Включить» спрашивает разрешение и включает напоминания в начале
/// утреннего и вечернего времени; «Не сейчас» — просто скрывает. Ответ запоминается в любом случае.
private struct ReminderOfferCard: View {
    @Environment(ReminderSettings.self) private var reminders
    @Environment(ReminderScheduler.self) private var scheduler
    @Environment(\.theme) private var theme

    private static let laterMinHeight: CGFloat = 48

    /// Идёт запрос разрешения — кнопки неактивны: второе нажатие не запустит второй запрос (аудит iOS 1.1.1, А12).
    @State private var isRequesting = false

    var body: some View {
        NoticeCard(symbol: "clock", title: "reminders.offer.title", message: "reminders.offer.message") {
            VStack(spacing: Spacing.xs) {
                Button("reminders.offer.enable", action: enable)
                    .buttonStyle(PrimaryButtonStyle())
                Button(action: answer) {
                    Text("reminders.offer.later")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(theme.palette.onAccentSecondary)
                        .frame(maxWidth: .infinity, minHeight: Self.laterMinHeight)
                        .contentShape(.rect)
                }
                .buttonStyle(PressDimButtonStyle())
            }
            .padding(.top, Spacing.s)
            .disabled(isRequesting)
        }
    }

    private func enable() {
        isRequesting = true
        Task {
            if await scheduler.requestAuthorization() {
                reminders.setStart(true, for: .morning)
                reminders.setStart(true, for: .evening)
                scheduler.reschedule()
            }
            answer()
        }
    }

    /// Карточка уходит плавно — карточки главной не съезжают скачком.
    private func answer() {
        withAnimation(Motion.collapse) { reminders.answerOffer() }
    }
}

/// «Обновить» — слева вверху, только когда в App Store есть новая версия (как на Android, `10d`):
/// круглая кнопка как «Избранное» у хадисов и золотая точка. Источник — `AppStoreUpdateSource` (раз в сутки);
/// обновления нет — кнопки нет. Под кнопкой — подсказка «Вышло обновление», один раз на версию, через 0,5 с,
/// 4 с, после «Что нового» (решение пользователя 2026-10-27, как подсказка у входа в «Къиблу»). Ждала очереди
/// подсказка «Къиблы» — подсказка обновления в этот раз молчит: на узком экране пузыри накладывались (аудит).
private struct UpdateButton: View {
    @Environment(\.updateSource) private var updateSource
    @Environment(UpdateHints.self) private var hints
    @Environment(WhatsNew.self) private var whatsNew
    @Environment(\.qibla) private var qibla
    @Environment(\.openURL) private var openURL
    @Environment(\.theme) private var theme
    @Environment(\.chromeStyle) private var chromeStyle
    @Environment(\.scenePhase) private var scenePhase

    @State private var update: AvailableUpdate?
    @State private var hintVisible = false
    /// При появлении главной подсказка «Къиблы» ещё не показана — она и займёт этот показ.
    @State private var qiblaHintPending = false

    private static let dotSize: CGFloat = 10
    private static let hintDelay = Duration.milliseconds(500)
    private static let hintDuration = Duration.seconds(4)

    var body: some View {
        // ZStack, не Group: у пустой группы `.task` не запустился бы — вопрос не задавался бы вовсе.
        ZStack {
            if let update {
                styledButton
                    .overlay(alignment: .topTrailing) {
                        Circle()
                            .fill(theme.palette.gold)
                            .frame(width: Self.dotSize, height: Self.dotSize)
                            .accessibilityHidden(true)
                    }
                    .accessibilityLabel(Text("update.available \(update.version)"))
                    .hintBubble(
                        "update.hint",
                        isVisible: hintVisible,
                        tint: theme.palette.tabAzkar,
                        textColor: theme.palette.background,
                        below: true,
                        leading: true
                    )
            }
        }
        .animation(.easeInOut, value: hintVisible)
        .onAppear { qiblaHintPending = qibla.map { !$0.hints.entrySeen } ?? false }
        // И при возврате из фона: запросы ограничивает сам источник (раз в сутки).
        .task(id: scenePhase == .active) {
            guard scenePhase == .active else { return }
            update = await updateSource.availableUpdate()
        }
        .task(id: HintGate(
            version: update?.version,
            checked: whatsNew.isChecked,
            blocked: whatsNew.pending != nil,
            active: scenePhase == .active
        )) {
            await showHint()
        }
        .onDisappear { hintVisible = false }
    }

    private func showHint() async {
        guard let version = update?.version, hints.shouldHint(version), !qiblaHintPending,
              whatsNew.isChecked, whatsNew.pending == nil, scenePhase == .active else { return }
        try? await Task.sleep(for: Self.hintDelay)
        // Пока ждали, кнопку могли нажать или ушли с главной.
        guard !Task.isCancelled, hints.shouldHint(version) else { return }
        hints.markHinted(version)
        hintVisible = true
        try? await Task.sleep(for: Self.hintDuration)
        hintVisible = false
    }

    /// Когда подсказке можно появиться: есть обновление, «Что нового» проверено и закрыто, приложение активно.
    private struct HintGate: Equatable {
        let version: String?
        let checked: Bool
        let blocked: Bool
        let active: Bool
    }

    @ViewBuilder
    private var styledButton: some View {
        if #available(iOS 26, *), chromeStyle == .system {
            button
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
        } else {
            button
                .buttonStyle(GlassIconButtonStyle(tint: theme.palette.tabAzkar))
        }
    }

    private var button: some View {
        Button {
            hintVisible = false
            if let version = update?.version { hints.markHinted(version) }
            openURL(AboutLinks.appStore)
        } label: {
            Image(systemName: "square.and.arrow.down")
                .fontWeight(.medium)
                .foregroundStyle(theme.palette.tabAzkar)
        }
        .fixedTextSize()
    }
}

#Preview {
    NavigationStack {
        AzkarHomeView()
    }
    .appEnvironment(.preview)
}

#Preview("Есть обновление") {
    NavigationStack {
        AzkarHomeView()
    }
    .appEnvironment(.preview)
    .environment(\.updateSource, FixedUpdateSource(update: AvailableUpdate(version: "1.2.0")))
}
