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

/// Экраны внутри вкладки «Хадисы».
nonisolated enum HadithRoute: Hashable, Codable, Sendable {
    case list(HadithCollection)
    case detail(HadithID)
}

/// Навигация приложения: выбранная вкладка и типизированный стек каждой вкладки.
///
/// Типизированный путь вместо `NavigationPath` позволяет открыть любой экран из кода
/// (`open(_:)`) и проверить навигацию в тестах.
@Observable
final class AppRouter {
    var selectedTab: AppTab = .azkar
    var azkarPath: [AzkarRoute] = []
    var hadithPath: [HadithRoute] = []

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
        case .hadith:
            if !hadithPath.isEmpty { hadithPath.removeAll() }
        case .settings:
            break
        }
    }

    func open(_ route: AzkarRoute) {
        selectedTab = .azkar
        azkarPath = [route]
    }

    /// Хадис открывается поверх списка своего сборника — «назад» ведёт в список, как в приложении.
    func open(_ route: HadithRoute) {
        selectedTab = .hadith
        switch route {
        case .list:
            hadithPath = [route]
        case .detail(let id):
            hadithPath = [.list(id.collection), route]
        }
    }
}
