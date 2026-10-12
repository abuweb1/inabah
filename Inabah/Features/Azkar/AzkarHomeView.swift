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
/// круглая кнопка как «Избранное» у хадисов и золотая точка. В 1.1.1 источник без сети — значка нет.
private struct UpdateButton: View {
    @Environment(\.updateSource) private var updateSource
    @Environment(\.openURL) private var openURL
    @Environment(\.theme) private var theme

    @State private var update: AvailableUpdate?

    private static let dotSize: CGFloat = 10

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
            }
        }
        .task { update = await updateSource.availableUpdate() }
    }

    @ViewBuilder
    private var styledButton: some View {
        if #available(iOS 26, *) {
            button
                .buttonStyle(.glass)
                .buttonBorderShape(.circle)
                .controlSize(.large)
        } else {
            button
                .buttonStyle(IconButtonStyle(
                    foreground: theme.palette.tabAzkar,
                    background: theme.palette.subtleFill,
                    border: theme.palette.hairline
                ))
        }
    }

    private var button: some View {
        Button {
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
