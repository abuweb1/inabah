import SwiftUI

/// Оформление экранов настроек (системный `Form`/`List`) в стиле приложения: градиент раздела
/// вместо системного фона, полупрозрачные строки, светлый текст, акцент темы.
private struct SettingsFormModifier: ViewModifier {
    let background: ThemeGradient

    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .foregroundStyle(theme.palette.onAccent)
            .tint(theme.palette.accentLight)
            .background { background.linear.ignoresSafeArea() }
            .toolbarColorScheme(.dark, for: .navigationBar)
    }
}

extension View {
    /// Экран настроек на фоне `background` (фон раздела, к которому относятся настройки).
    func settingsForm(background: ThemeGradient) -> some View {
        modifier(SettingsFormModifier(background: background))
    }

    /// Подложка строки экрана настроек.
    func settingsRow() -> some View {
        modifier(SettingsRowModifier())
    }
}

private struct SettingsRowModifier: ViewModifier {
    @Environment(\.theme) private var theme

    func body(content: Content) -> some View {
        content.listRowBackground(theme.palette.subtleFill)
    }
}
