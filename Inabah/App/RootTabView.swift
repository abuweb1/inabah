import SwiftUI

/// Вкладки приложения: Азкары, Хадисы, Махрадж, Настройки — у каждой свой стек навигации.
struct RootTabView: View {
    @Environment(AppRouter.self) private var router
    @Environment(AzkarStore.self) private var azkarStore
    @Environment(HadithStore.self) private var hadithStore
    @Environment(AudioPlayerController.self) private var audioPlayer
    @Environment(ReminderScheduler.self) private var reminderScheduler
    @Environment(WhatsNew.self) private var whatsNew
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.theme) private var theme
    @Environment(\.chromeStyle) private var chromeStyle

    /// Нижняя безопасная зона (полоска «домой») — от неё стоит своя панель вкладок.
    @State private var safeAreaBottom: CGFloat = 0

    var body: some View {
        @Bindable var router = router

        TabView(selection: tabSelection) {
            // Значки вкладок — общие с Android (Material Symbols и «ع» из Scheherazade New),
            // `Assets.xcassets/TabIcons`: одинаковый вид на обеих платформах.
            Tab("tab.azkar", image: "tabIconAzkar", value: AppTab.azkar) {
                // Шаг «Размер интерфейса» — на каждом экране стека, а не на `NavigationStack`:
                // навбары остаются стандартного размера.
                NavigationStack(path: $router.azkarPath) {
                    AzkarHomeView()
                        .interfaceTextSize()
                        .navigationDestination(for: AzkarRoute.self) { route in
                            Group {
                                switch route {
                                case .list(let section): AzkarListView(section: section)
                                case .calendar: AzkarCalendarView()
                                }
                            }
                            .interfaceTextSize()
                        }
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(chromeStyle, safeAreaBottom: safeAreaBottom)
            }
            Tab("tab.hadith", image: "tabIconHadith", value: AppTab.hadith) {
                NavigationStack(path: $router.hadithPath) {
                    HadithHomeView()
                        .interfaceTextSize()
                        .navigationDestination(for: HadithRoute.self) { route in
                            Group {
                                switch route {
                                case .list(let collection): HadithListView(collection: collection)
                                case .detail(let id): HadithDetailView(id: id)
                                case .about(let collection): CollectionAboutView(collection: collection)
                                case .favorites: FavoritesView()
                                }
                            }
                            .interfaceTextSize()
                        }
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(chromeStyle, safeAreaBottom: safeAreaBottom)
            }
            // «ع» (айн) — гортанная буква, хрестоматийный пример махраджа.
            Tab("tab.makharij", image: "tabIconMakharij", value: AppTab.makharij) {
                NavigationStack {
                    MakharijHomeView()
                        .interfaceTextSize()
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(chromeStyle, safeAreaBottom: safeAreaBottom)
            }
            Tab("tab.settings", image: "tabIconSettings", value: AppTab.settings) {
                NavigationStack(path: $router.settingsPath) {
                    // Шаг интерфейса — внутри `SettingsView`, у строк: крупный заголовок
                    // «Настройки» UIKit масштабирует по размеру всего списка.
                    SettingsView()
                        .navigationDestination(for: SettingsRoute.self) { route in
                            Group {
                                switch route {
                                case .azkar: AzkarSettingsView()
                                case .hadith: HadithSettingsView()
                                case .hadithOrder: HadithOrderSettingsView()
                                case .appIcon: AppIconSettingsView()
                                case .palette: PaletteSettingsView()
                                case .textSize: TextSizeSettingsView()
                                case .about: AboutView()
                                case .license(let document): LicenseView(document: document)
                                case .versionHistory: VersionHistoryView()
                                }
                            }
                            .interfaceTextSize()
                        }
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(chromeStyle, safeAreaBottom: safeAreaBottom)
            }
        }
        // Подсветка выбранной вкладки — в тон раздела; содержимому вкладок выше возвращён
        // общий акцент, чтобы оттенок таб-бара не перекрашивал кнопки и переключатели.
        .tint(router.selectedTab.tint(in: theme))
        .overlay(alignment: .bottom) {
            if chromeStyle == .custom {
                FloatingTabBar(selection: router.selectedTab) { router.select($0) }
                    .padding(.horizontal, FloatingTabBar.horizontalMargin)
                    .padding(.bottom, FloatingTabBar.bottomOffset(safeAreaBottom: safeAreaBottom))
                    .ignoresSafeArea(.keyboard)
            }
        }
        .onGeometryChange(for: CGFloat.self) { $0.safeAreaInsets.bottom } action: { safeAreaBottom = $0 }
        // Под окном «Что нового» вкладки скрыты от VoiceOver; окно — поверх всех вкладок.
        .accessibilityHidden(whatsNew.pending != nil)
        .overlay { WhatsNewLayer() }
        .task {
            await whatsNew.check()
            await azkarStore.loadAll()
            await hadithStore.loadAll()
        }
        .onChange(of: scenePhase) { oldPhase, phase in
            if phase == .background {
                audioPlayer.applicationDidEnterBackground()
            } else if oldPhase == .background {
                audioPlayer.applicationWillEnterForeground()
            }
            // Пока приложение было в фоне, время азкаров могло начаться или закончиться.
            if phase == .active { azkarStore.reconcile() }
            // Напоминания: приложение не запускается в момент уведомления — план на неделю
            // пересобирается при возврате (разрешение могли поменять) и при уходе в фон.
            if phase == .active || phase == .background { reminderScheduler.reschedule() }
        }
        .background { ReminderCompletionWatcher() }
    }

    /// Выбор вкладки проходит через роутер — повторный тап по активной вкладке возвращает к её корню.
    private var tabSelection: Binding<AppTab> {
        Binding(
            get: { router.selectedTab },
            set: { router.select($0) }
        )
    }
}

/// Раздел дочитан или снова не дочитан (сброс, новое окно) — «ещё не прочитаны» на это окно снимается
/// или возвращается. Следит только за выполненностью, а не за каждым зикром (аудит iOS 1.1.1, А3), и
/// отдельной вьюхой — счёт зикров не перестраивает корень с вкладками.
private struct ReminderCompletionWatcher: View {
    @Environment(AzkarStore.self) private var azkarStore
    @Environment(ReminderScheduler.self) private var reminderScheduler

    var body: some View {
        Color.clear
            .onChange(of: AzkarSection.allCases.map { ReminderPlan.isCompleted(azkarStore.progress(of: $0)) }) {
                reminderScheduler.reschedule()
            }
    }
}

#Preview {
    let environment = AppEnvironment.preview
    RootTabView()
        .appEnvironment(environment)
}
