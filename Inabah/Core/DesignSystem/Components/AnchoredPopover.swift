import SwiftUI

/// Всплывающая карточка над элементом с уголком-указателем — копия Android `AnchoredPopover`
/// (`AboveCenter`, `showsArrow`): своя, не системный `.popover` (решение пользователя 2026-10-11 —
/// не привязываться к виду системы). Подложка — фон палитры с оттенком раздела, обводка и тень,
/// как у popover iOS 26 на Android (`popoverSurface`).
///
/// Ставится слоем поверх контейнера, в координатах которого задан `anchor` (обычно
/// `overlayPreferenceValue` + `GeometryReader`), и стоит там всегда: `anchor == nil` — карточки нет.
/// Прозрачный слой под карточкой закрывает её касанием мимо. Появление и закрытие анимирует вызывающий
/// (`withAnimation(Motion.dialog)` / `Motion.popoverClose`): карточка вырастает из центра элемента
/// и сжимается в него обратно.
struct AnchoredPopover<Content: View>: View {
    /// Рамка элемента в координатах контейнера; `nil` — карточка скрыта.
    let anchor: CGRect?
    let containerWidth: CGFloat
    /// Ширина карточки (вместе с полями содержимого).
    let width: CGFloat
    let tint: Color
    let onDismiss: () -> Void
    @ViewBuilder let content: Content

    @Environment(\.theme) private var theme

    var body: some View {
        ZStack(alignment: .topLeading) {
            if let anchor {
                // Касание мимо — закрыть; для VoiceOver слой не элемент (закрытие — жестом «назад»).
                Color.clear
                    .contentShape(.rect)
                    .onTapGesture(perform: onDismiss)
                    .accessibilityHidden(true)
                let x = Self.leading(anchor: anchor, width: width, containerWidth: containerWidth)
                // Переход — последним: SwiftUI берёт переход у самого внешнего вида ветки.
                card(arrowX: anchor.midX - x)
                    .frame(width: width)
                    .padding(.leading, x)
                    .frame(width: containerWidth, height: max(anchor.minY, 0), alignment: .bottomLeading)
                    .transition(Self.transition(anchor: anchor, containerWidth: containerWidth))
            }
        }
    }

    private func card(arrowX: CGFloat) -> some View {
        let shape = PopoverShape(radius: PopoverMetrics.radius, arrowX: arrowX, arrowSize: PopoverMetrics.arrow)
        return content
            .padding(.bottom, PopoverMetrics.arrow.height)
            .fixedTextSize()
            .background {
                shape
                    .fill(fillColor)
                    .shadow(
                        color: ShadowToken.floating(theme.palette).color,
                        radius: ShadowToken.floating(theme.palette).radius,
                        y: ShadowToken.floating(theme.palette).y
                    )
            }
            .overlay {
                shape.stroke(tint.opacity(PopoverMetrics.rimOpacity), lineWidth: Size.hairline)
            }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityAction(.escape, onDismiss)
    }

    private var fillColor: Color {
        SRGBColor(theme.palette.background)
            .mixed(with: SRGBColor(tint), by: PopoverMetrics.tintShare)
            .opacity(PopoverMetrics.fillOpacity)
            .color
    }

    /// Левый край карточки: по центру элемента, но не ближе 16 к краям контейнера.
    static func leading(anchor: CGRect, width: CGFloat, containerWidth: CGFloat) -> CGFloat {
        let centered = anchor.midX - width / 2
        return min(max(centered, PopoverMetrics.screenMargin), max(containerWidth - PopoverMetrics.screenMargin - width, PopoverMetrics.screenMargin))
    }

    /// Вырастает из верха элемента (остриё уголка) и сжимается туда же. Слой карточки кончается
    /// у верха элемента — точка роста: середина элемента по ширине, низ слоя.
    static func transition(anchor: CGRect, containerWidth: CGFloat) -> AnyTransition {
        let point = UnitPoint(x: containerWidth > 0 ? anchor.midX / containerWidth : 0.5, y: 1)
        return .scale(scale: PopoverMetrics.startScale, anchor: point).combined(with: .opacity)
    }
}

/// Размеры и цвета всплывающей карточки — как на Android (`AnchoredPopover.kt`).
private enum PopoverMetrics {
    static let radius: CGFloat = 34
    static let arrow = CGSize(width: 22, height: 9)
    static let screenMargin: CGFloat = 16
    static let fillOpacity = 0.94
    static let rimOpacity = 0.45
    /// Доля цвета раздела в подложке.
    static let tintShare = 0.18
    /// Масштаб в начале появления — почти размер элемента, из которого карточка растёт.
    static let startScale: CGFloat = 0.2
}

/// Скруглённая карточка с уголком снизу; уголок напротив `arrowX`, но не заходит на скругления углов.
nonisolated struct PopoverShape: Shape {
    let radius: CGFloat
    let arrowX: CGFloat
    let arrowSize: CGSize

    func path(in rect: CGRect) -> Path {
        let bodyRect = CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: rect.height - arrowSize.height)
        let body = Path(roundedRect: bodyRect, cornerRadius: min(radius, bodyRect.height / 2), style: .continuous)
        let half = arrowSize.width / 2
        let lower = rect.minX + radius + half
        let upper = rect.maxX - radius - half
        let center = lower <= upper ? min(max(rect.minX + arrowX, lower), upper) : rect.midX
        // Основание уголка заходит в карточку на 1 — без шва между ними.
        let base = bodyRect.maxY - 1
        var arrow = Path()
        arrow.move(to: CGPoint(x: center - half, y: base))
        arrow.addLine(to: CGPoint(x: center, y: rect.maxY))
        arrow.addLine(to: CGPoint(x: center + half, y: base))
        arrow.closeSubpath()
        return body.union(arrow)
    }
}
