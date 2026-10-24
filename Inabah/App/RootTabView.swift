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
                                case .composition(let section): AzkarCompositionView(section: section)
                                }
                            }
                            .interfaceTextSize()
                            .floatingTabBarDestinationSpace(safeAreaBottom: safeAreaBottom)
                        }
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(safeAreaBottom: safeAreaBottom)
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
                            .floatingTabBarDestinationSpace(safeAreaBottom: safeAreaBottom)
                        }
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(safeAreaBottom: safeAreaBottom)
            }
            // «ع» (айн) — гортанная буква, хрестоматийный пример махраджа.
            Tab("tab.makharij", image: "tabIconMakharij", value: AppTab.makharij) {
                NavigationStack(path: $router.makharijPath) {
                    MakharijHomeView()
                        .interfaceTextSize()
                        .navigationDestination(for: MakharijRoute.self) { route in
                            Group {
                                switch route {
                                case .steps: ReadingStepsView()
                                case .lesson(let number): ReadingLessonView(stepNumber: number)
                                case .alphabet(let number): ReadingAlphabetView(stepNumber: number)
                                case .extra(let number, let topic): ReadingExtraView(stepNumber: number, topic: topic)
                                case .letterForms(let number, let letter): ReadingLetterFormsView(stepNumber: number, letter: letter)
                                case .quiz(let number): ReadingQuizView(stepNumber: number)
                                case .words(let number): ReadingWordsView(stepNumber: number)
                                case .zikr(let number, let zikr): ReadingZikrView(stepNumber: number, zikrIndex: zikr)
                                case .guide(let id): ReadingGuideView(guideID: id)
                                }
                            }
                            .interfaceTextSize()
                            .floatingTabBarDestinationSpace(safeAreaBottom: safeAreaBottom)
                        }
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(safeAreaBottom: safeAreaBottom)
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
                                case .azkarComposition(let section): AzkarCompositionView(section: section)
                                case .hadith: HadithSettingsView()
                                case .hadithOrder: HadithOrderSettingsView()
                                case .makharij: MakharijSettingsView()
                                case .appIcon: AppIconSettingsView()
                                case .palette: PaletteSettingsView()
                                case .textSize: TextSizeSettingsView()
                                case .about: AboutView()
                                case .licenses: LicensesView()
                                case .license(let document): LicenseView(document: document)
                                case .versionHistory: VersionHistoryView()
                                }
                            }
                            .interfaceTextSize()
                            .floatingTabBarDestinationSpace(safeAreaBottom: safeAreaBottom)
                        }
                }
                .tint(theme.palette.accentLight)
                .floatingTabBarSpace(safeAreaBottom: safeAreaBottom)
            }
        }
        // Подсветка выбранной вкладки — в тон раздела; содержимому вкладок выше возвращён
        // общий акцент, чтобы оттенок таб-бара не перекрашивал кнопки и переключатели.
        .tint(router.selectedTab.tint(in: theme))
        // Своя панель на всех iOS (на iOS 26–27 вместо системной — решение пользователя 2026-10-25);
        // на внутренних экранах вкладки — компактная.
        .overlay(alignment: .bottom) {
            FloatingTabBar(selection: router.selectedTab, compact: !router.isOnTabRoot) { router.select($0) }
                .padding(.horizontal, FloatingTabBar.horizontalMargin)
                .padding(.bottom, FloatingTabBar.bottomOffset(safeAreaBottom: safeAreaBottom))
                .ignoresSafeArea(.keyboard)
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
        .background { ReadingReminderWatcher() }
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

/// «Пора на урок»: шаг пройден (сегодняшние снимаются, шаг в тексте - следующий), уроки сброшены или пройдены -
/// план пересобирается. Не следит за прочитанными словами: они меняются на каждое касание, а шаг со словами
/// пройден - это и есть новый день пройденного шага.
private struct ReadingReminderWatcher: View {
    @Environment(ReadingProgress.self) private var readingProgress
    @Environment(ReminderScheduler.self) private var reminderScheduler

    var body: some View {
        Color.clear
            .onChange(of: readingProgress.stepFinishedDay) { reminderScheduler.reschedule() }
            .onChange(of: readingProgress.lessonsDone) { reminderScheduler.reschedule() }
    }
}

#Preview {
    let environment = AppEnvironment.preview
    RootTabView()
        .appEnvironment(environment)
}
