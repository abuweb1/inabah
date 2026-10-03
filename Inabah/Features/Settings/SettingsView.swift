import SwiftUI

/// Настройки. Содержание экрана ещё обсуждается — пока заглушка.
struct SettingsView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        ComingSoonView(
            symbolName: "gearshape",
            message: "settings.placeholder.message",
            background: theme.gradients.azkarBackground
        )
    }
}

#Preview {
    SettingsView()
}
