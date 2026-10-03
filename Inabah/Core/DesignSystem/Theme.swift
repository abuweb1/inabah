import SwiftUI

/// Тема оформления: палитра + градиенты.
///
/// Передаётся через окружение (`@Environment(\.theme)`), поэтому новая тема
/// подключается заменой одного значения в корне приложения.
nonisolated struct Theme: Hashable, Sendable {
    var palette: Palette
    var gradients: ThemeGradients

    static let inabah = Theme(palette: .inabah, gradients: .inabah)
}

extension EnvironmentValues {
    @Entry var theme: Theme = .inabah
}
