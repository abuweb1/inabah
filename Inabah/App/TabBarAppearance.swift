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

/// Навбар при своём стекле (`ChromeStyle.custom`, iOS 18–25): без линии-тени под шапкой — у системного
/// стекла iOS 26 и на Android её нет. Фон шапок по-прежнему задают экраны (`toolbarBackground`).
enum NavigationBarAppearance {
    static func apply(_ style: ChromeStyle) {
        guard style == .custom else { return }
        let standard = UINavigationBarAppearance()
        standard.configureWithDefaultBackground()
        strip(standard)
        // У края прокрутки — прозрачная, как по умолчанию (шапки настроек).
        let scrollEdge = UINavigationBarAppearance()
        scrollEdge.configureWithTransparentBackground()
        strip(scrollEdge)
        let bar = UINavigationBar.appearance()
        bar.standardAppearance = standard
        bar.compactAppearance = standard
        bar.scrollEdgeAppearance = scrollEdge
    }

    /// Без линии-тени и без видимой системной «‹ Назад»: стрелка пустая, подпись прозрачная. Системная
    /// кнопка скрыта (`glassBackButton`), но в кадрах перехода UIKit успевал её показать — мелькала.
    static func strip(_ appearance: UINavigationBarAppearance) {
        appearance.shadowColor = .clear
        appearance.setBackIndicatorImage(emptyImage, transitionMaskImage: emptyImage)
        let back = UIBarButtonItemAppearance()
        back.normal.titleTextAttributes = [.foregroundColor: UIColor.clear]
        back.highlighted.titleTextAttributes = [.foregroundColor: UIColor.clear]
        appearance.backButtonAppearance = back
    }

    private static let emptyImage = UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { _ in }
}