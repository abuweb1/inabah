import SwiftUI

/// Чьё стекло рисует кнопки шапок и прочие элементы: системное (Liquid Glass) или своё — копия того же вида
/// (как на Android). Решение пользователя 2026-10-15: системное — только на iOS 26–27, где оно есть
/// и нравится; на iOS 18–25 и на будущих версиях, пока их дизайн не проверен, — своё. Панель вкладок
/// с 2026-10-25 своя на всех iOS (`FloatingTabBar`), шапка списка азкаров — тоже. Версии iOS
/// проверяются только здесь: экраны спрашивают `chromeStyle` из окружения.
nonisolated enum ChromeStyle: Hashable, Sendable {
    case system
    case custom

    /// Версии iOS с системным стеклом. Новая iOS сменит дизайн — поправить диапазон здесь.
    static let systemGlassVersions = 26...27

    static func resolve(osMajor: Int) -> ChromeStyle {
        systemGlassVersions.contains(osMajor) ? .system : .custom
    }

    static let current = resolve(osMajor: ProcessInfo.processInfo.operatingSystemVersion.majorVersion)
}

extension EnvironmentValues {
    @Entry var chromeStyle: ChromeStyle = .current
}
