import Foundation
import Observation

enum AppTab: Hashable {
    case azkar
    case hadith
    case settings
}

/// Экраны внутри вкладки «Азкары». `Codable` — для будущих deep links (виджет, уведомления).
nonisolated enum AzkarRoute: Hashable, Codable, Sendable {
    case list(AzkarSection)
}

/// Навигация приложения: выбранная вкладка и типизированный стек каждой вкладки.
///
/// Типизированный путь вместо `NavigationPath` позволяет открыть любой экран из кода
/// (`open(_:)`) и проверить навигацию в тестах.
@Observable
final class AppRouter {
    var selectedTab: AppTab = .azkar
    var azkarPath: [AzkarRoute] = []

    /// Выбор вкладки из таб-бара. Повторный выбор активной вкладки возвращает к её корню.
    /// `TabView` вызывает установку выбора и во время отрисовки (при запуске) — поэтому
    /// состояние меняется, только если действительно есть что менять: иначе SwiftUI
    /// предупреждает «Modifying state during view update».
    func select(_ tab: AppTab) {
        if tab == selectedTab {
            popToRoot(tab)
        } else {
            selectedTab = tab
        }
    }

    func popToRoot(_ tab: AppTab) {
        switch tab {
        case .azkar:
            if !azkarPath.isEmpty { azkarPath.removeAll() }
        case .hadith, .settings:
            break
        }
    }

    func open(_ route: AzkarRoute) {
        selectedTab = .azkar
        azkarPath = [route]
    }
}
