import SwiftUI
import UIKit

/// «‹» в шапке своим стеклом при `ChromeStyle.custom` (iOS 18–25): системная кнопка там — «‹ Назад»
/// текстом, без стекла iOS 26. Круг 40, как Android `GlassIconButton` («‹» в `TopBar.kt`). Над цветной
/// шапкой (`header`) — непрозрачное стекло в её тон; над прозрачной (настройки) — `nil`.
/// Только для экранов, открытых переходом. При `.system` ничего не меняет.
///
/// Без проверки `isPresented`: в первые кадры перехода она ложна, и на мгновение появлялась
/// системная «‹ Назад» (замечание пользователя 2026-10-15).
private struct GlassBackButtonModifier: ViewModifier {
    let tint: Color
    let header: Color?

    @Environment(\.chromeStyle) private var chromeStyle
    @Environment(\.dismiss) private var dismiss

    func body(content: Content) -> some View {
        if chromeStyle == .custom {
            content
                .background { NavigationBarShadowRemover().frame(width: 0, height: 0) }
                .navigationBarBackButtonHidden()
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "chevron.backward")
                                .font(.body.weight(.semibold))
                        }
                        .buttonStyle(GlassIconButtonStyle(
                            tint: tint,
                            header: header,
                            diameter: GlassIconButtonStyle.navigationBarDiameter
                        ))
                        .accessibilityLabel(Text("navigation.back"))
                        .fixedTextSize()
                    }
                }
        } else {
            content
        }
    }
}

/// Вид навбара экрана при своём стекле: SwiftUI для цветной шапки (`toolbarBackground`) ставит экрану
/// свой вид навбара — глобальный `UINavigationBar.appearance()` на него не действует. Помощник убирает
/// в нём линию-тень (её нет у стекла iOS 26 и на Android) и системную «‹ Назад» (`NavigationBarAppearance.strip`).
private struct NavigationBarShadowRemover: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller { Controller() }
    func updateUIViewController(_ controller: Controller, context: Context) { controller.removeShadow() }

    final class Controller: UIViewController {
        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            removeShadow()
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            removeShadow()
        }

        /// Экран стека, в котором стоит помощник, — его `navigationItem` хранит вид шапки.
        func removeShadow() {
            var screen: UIViewController? = self
            while let current = screen, current.parent != nil, current.parent !== current.navigationController {
                screen = current.parent
            }
            guard let item = screen?.navigationItem else { return }
            item.standardAppearance = Self.withoutShadow(item.standardAppearance)
            item.scrollEdgeAppearance = Self.withoutShadow(item.scrollEdgeAppearance)
            item.compactAppearance = Self.withoutShadow(item.compactAppearance)
        }

        private static func withoutShadow(_ appearance: UINavigationBarAppearance?) -> UINavigationBarAppearance? {
            guard let appearance, appearance.shadowColor != .clear else { return appearance }
            let copy = appearance.copy()
            NavigationBarAppearance.strip(copy)
            return copy
        }
    }
}

/// Круглая кнопка шапки («ⓘ», «Поделиться», «↺»): при `ChromeStyle.custom` — своё стекло, при `.system`
/// вид даёт система (на iOS 26 — стекло навбара).
private struct HeaderIconButtonModifier: ViewModifier {
    let tint: Color
    let header: Color?

    @Environment(\.chromeStyle) private var chromeStyle

    func body(content: Content) -> some View {
        if chromeStyle == .custom {
            content.buttonStyle(GlassIconButtonStyle(
                tint: tint,
                header: header,
                diameter: GlassIconButtonStyle.navigationBarDiameter
            ))
        } else {
            content
        }
    }
}

extension View {
    /// Кнопка-значок в шапке — своим стеклом при `ChromeStyle.custom`.
    func headerIconButtonStyle(tint: Color, header: Color?) -> some View {
        modifier(HeaderIconButtonModifier(tint: tint, header: header))
    }

    /// Своя стеклянная «‹» вместо системной (`ChromeStyle.custom`). Свайп от края назад работает
    /// по-прежнему (`UINavigationController` ниже).
    func glassBackButton(tint: Color, header: Color?) -> some View {
        modifier(GlassBackButtonModifier(tint: tint, header: header))
    }
}

/// Без системной «‹» (`navigationBarBackButtonHidden`) UIKit выключает свайп назад от края экрана.
/// Возвращаем его: жест разрешён, пока в стеке больше одного экрана.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}
