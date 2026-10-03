import SwiftUI

/// Заглушка раздела, который ещё в разработке.
struct ComingSoonView: View {
    let symbolName: String
    let message: LocalizedStringResource
    let background: ThemeGradient

    @Environment(\.theme) private var theme

    var body: some View {
        ContentUnavailableView {
            Label("placeholder.soon", systemImage: symbolName)
        } description: {
            Text(message)
        }
        .foregroundStyle(theme.palette.onAccentSecondary)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        // Фоновый плейлист виден и управляется с любой вкладки.
        .audioPlayerInset()
        .background { background.linear.ignoresSafeArea() }
    }
}
