import SwiftUI

/// Вкладки приложения: Азкары, Хадисы, Настройки — у каждой свой стек навигации.
struct RootTabView: View {
    @Environment(AppRouter.self) private var router
    @Environment(AzkarStore.self) private var azkarStore
    @Environment(AudioPlayerController.self) private var audioPlayer
    @Environment(\.scenePhase) private var scenePhase

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
            }
            Tab("tab.hadith", systemImage: "book.closed.fill", value: AppTab.hadith) {
                NavigationStack {
                    HadithHomeView()
                }
            }
            Tab("tab.settings", systemImage: "gearshape.fill", value: AppTab.settings) {
                NavigationStack {
                    SettingsView()
                }
            }
        }
        .task { await azkarStore.loadAll() }
        .onChange(of: scenePhase) { oldPhase, phase in
            if phase == .background {
                audioPlayer.applicationDidEnterBackground()
            } else if oldPhase == .background {
                audioPlayer.applicationWillEnterForeground()
            }
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
