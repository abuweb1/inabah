import UIKit

/// Подписи вкладок — мелким фиксированным шрифтом: на них не влияют ни настройка «Размер
/// интерфейса», ни системный размер текста (решение пользователя).
enum TabBarAppearance {
    private static let titleSize: CGFloat = 10

    static func apply() {
        let font = UIFont.systemFont(ofSize: titleSize, weight: .medium)
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        for layout in [
            appearance.stackedLayoutAppearance,
            appearance.inlineLayoutAppearance,
            appearance.compactInlineLayoutAppearance,
        ] {
            // Только шрифт: цвет выбранной вкладки по-прежнему задаёт `.tint` раздела.
            layout.normal.titleTextAttributes[.font] = font
            layout.selected.titleTextAttributes[.font] = font
        }
        let tabBar = UITabBar.appearance()
        tabBar.standardAppearance = appearance
        tabBar.scrollEdgeAppearance = appearance
    }
}