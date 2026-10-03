import SwiftUI

@main
struct InabahApp: App {
    @State private var environment = AppEnvironment()

    init() {
        AppFont.registerBundledFonts()
    }

    var body: some Scene {
        WindowGroup {
            RootTabView()
                .appEnvironment(environment)
        }
    }
}
