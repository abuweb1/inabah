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
                                }
                            }
                            .interfaceTextSize()
                        }
                }
                .tint(theme.palette.accentLight)
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
                                }
                            }
                            .interfaceTextSize()
                        }
                }
                .tint(theme.palette.accentLight)
            }
            // «ع» (айн) — гортанная буква, хрестоматийный пример махраджа.
            Tab("tab.makharij", image: "tabIconMakharij", value: AppTab.makharij) {
                NavigationStack {
                    MakharijHomeView()
                        .interfaceTextSize()
                }
                .tint(theme.palette.accentLight)
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
                                }
                            }
                            .interfaceTextSize()
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
