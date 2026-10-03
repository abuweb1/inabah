import SwiftUI

/// Вкладки приложения: Азкары, Хадисы, Махрадж, Настройки — у каждой свой стек навигации.
struct RootTabView: View {
    @Environment(AppRouter.self) private var router
    @Environment(AzkarStore.self) private var azkarStore
    @Environment(HadithStore.self) private var hadithStore
    @Environment(AudioPlayerController.self) private var audioPlayer
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.theme) private var theme

    var body: some View {
        @Bindable var router = router

        TabView(selection: tabSelection) {
            Tab("tab.azkar", systemImage: "hands.and.sparkles.fill", value: AppTab.azkar) {
                NavigationStack(path: $router.azkarPath) {
                    AzkarHomeView()
                        .navigationDestination(for: AzkarRoute.self) { route in
                            switch route {
                            case .list(let section): AzkarListView(section: section)
                            }
                        }
                }
                .tint(theme.palette.accentLight)
            }
            Tab("tab.hadith", systemImage: "book.closed.fill", value: AppTab.hadith) {
                NavigationStack(path: $router.hadithPath) {
                    HadithHomeView()
                        .navigationDestination(for: HadithRoute.self) { route in
                            switch route {
                            case .list(let collection): HadithListView(collection: collection)
                            case .detail(let id): HadithDetailView(id: id)
                            }
                        }
                }
                .tint(theme.palette.accentLight)
            }
            // «ع» (айн) — гортанная буква, хрестоматийный пример махраджа.
            Tab("tab.makharij", systemImage: "character.ar", value: AppTab.makharij) {
                NavigationStack {
                    MakharijHomeView()
                }
                .tint(theme.palette.accentLight)
            }
            Tab("tab.settings", systemImage: "gearshape.fill", value: AppTab.settings) {
                NavigationStack(path: $router.settingsPath) {
                    SettingsView()
                        .navigationDestination(for: SettingsRoute.self) { route in
                            switch route {
                            case .azkar: AzkarSettingsView()
                            case .hadith: HadithSettingsView()
                            case .hadithOrder: HadithOrderSettingsView()
                            case .appIcon: AppIconSettingsView()
                            }
                        }
                }
                .tint(theme.palette.accentLight)
            }
        }
        // Подсветка выбранной вкладки — в тон раздела; содержимому вкладок выше возвращён
        // общий акцент, чтобы оттенок таб-бара не перекрашивал кнопки и переключатели.
        .tint(router.selectedTab.tint(in: theme))
        .task {
            await azkarStore.loadAll()
            await hadithStore.loadAll()
        }
        .onChange(of: scenePhase) { oldPhase, phase in
            if phase == .background {
                audioPlayer.applicationDidEnterBackground()
            } else if oldPhase == .background {
                audioPlayer.applicationWillEnterForeground()
            }
            // Пока приложение было в фоне, время обнуления азкаров могло наступить.
            if phase == .active { azkarStore.refreshPeriods() }
        }
    }

    /// Выбор вкладки проходит через роутер — повторный тап по активной вкладке возвращает к её корню.
    private var tabSelection: Binding<AppTab> {
        Binding(
            get: { router.selectedTab },
            set: { router.select($0) }
        )
    }
}

#Preview {
    let environment = AppEnvironment.preview
    RootTabView()
        .appEnvironment(environment)
}
