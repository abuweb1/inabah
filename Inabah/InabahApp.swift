import SwiftUI

@main
struct InabahApp: App {
    @State private var environment: AppEnvironment

    init() {
        AppFont.registerBundledFonts()
        TabBarAppearance.apply()
        NavigationBarAppearance.apply(.current)
        // Под тестами приложение — только хост: в App Store не ходит и `update.*` не пишет.
        let isTestHost = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
        let updateSource: any UpdateSource = isTestHost ? NoUpdateSource() : AppStoreUpdateSource()
        let environment = AppEnvironment(updateSource: updateSource)
        // До первого уведомления: нажатие на напоминание при холодном запуске тоже откроет нужный экран.
        environment.installNotificationDelegate()
        _environment = State(initialValue: environment)
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .appEnvironment(environment)
        }
    }
}
