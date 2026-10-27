import SwiftUI

/// Числа окна-стекла: у окна «Къиблы» и «Добавьте ещё азкаров» они свои.
nonisolated struct GlassDialogStyle {
    var maxWidth: CGFloat
    /// Поле от краёв экрана.
    var screenMargin: CGFloat
    var cornerRadius: CGFloat
    /// Затемнение вокруг окна (сквозь стекло оно тоже видно).
    var scrimOpacity: Double
    /// Размытие копии экрана под окном.
    var blurRadius: CGFloat = 16
    /// Доля цвета раздела в стекле и прозрачность заливки поверх размытия.
    var tintShare: Double
    var fillOpacity: Double = 0.4
    /// Ободок — цвет `rim` с этой прозрачностью.
    var rimOpacity: Double
}

/// Окно по центру поверх экрана — стекло, как Android `GlassDialogOverlay` / `GlassDialogCard`.
///
/// Размытие своё, не системное (решение пользователя 2026-10-25: системное делало окно плотным и серым):
/// под окном лежит размытая копия экрана `backdrop`, вырезанная по форме окна. Копия должна стоять в слое
/// там же, где сам экран, — тогда она с ним совпадает. Без тени: под стеклом она просвечивала бы пятном.
struct GlassDialog<Card: View, Backdrop: View>: View {
    let style: GlassDialogStyle
    /// Цвет раздела в стекле.
    let tint: Color
    let rim: Color
    /// Касание мимо окна; по умолчанию ничего не делает.
    var onTapOutside: () -> Void = {}
    @ViewBuilder let card: Card
    @ViewBuilder let backdrop: Backdrop

    @Environment(\.theme) private var theme
    /// Рамка окна в слое — по ней вырезается размытая копия.
    @State private var cardFrame: CGRect = .zero

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: style.cornerRadius)
        ZStack {
            theme.palette.shadow.opacity(style.scrimOpacity)
                .ignoresSafeArea()
                .contentShape(.rect)
                .onTapGesture(perform: onTapOutside)
                .accessibilityHidden(true)
            backdrop
                .blur(radius: style.blurRadius)
                .mask {
                    shape
                        .frame(width: cardFrame.width, height: cardFrame.height)
                        .position(x: cardFrame.midX, y: cardFrame.midY)
                }
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            card
                .background { glass(shape) }
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(GlassDialogSpace.name)) } action: { cardFrame = $0 }
                .frame(maxWidth: style.maxWidth)
                .padding(style.screenMargin)
        }
        .coordinateSpace(.named(GlassDialogSpace.name))
    }

    /// Заливка в тон раздела поверх размытой копии и ободок.
    private func glass(_ shape: RoundedRectangle) -> some View {
        let fill = SRGBColor(theme.palette.background).mixed(with: SRGBColor(tint), by: style.tintShare).color
        return shape
            .fill(fill.opacity(style.fillOpacity))
            .overlay { shape.strokeBorder(rim.opacity(style.rimOpacity), lineWidth: Size.hairline) }
    }
}

/// Имя слоя — вне обобщённого типа (в нём нельзя хранимые статические свойства).
nonisolated private enum GlassDialogSpace {
    static let name = "glassDialog"
}
