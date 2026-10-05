import SwiftUI

@main
struct InabahApp: App {
    @State private var environment = AppEnvironment()

    init() {
        AppFont.registerBundledFonts()
        TabBarAppearance.apply()
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .appEnvironment(environment)
        }
    }
}
