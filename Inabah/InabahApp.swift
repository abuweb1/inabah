import SwiftUI

@main
struct InabahApp: App {
    @State private var environment: AppEnvironment

    init() {
        AppFont.registerBundledFonts()
        TabBarAppearance.apply()
        let environment = AppEnvironment()
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
