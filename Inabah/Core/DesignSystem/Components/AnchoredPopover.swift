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
    /// Сколько места нужно над элементом; меньше — карточка открывается под ним, уголком вверх
    /// (Android: переворот, если не помещается). `nil` — всегда над элементом.
    var minSpaceAbove: CGFloat?
    /// Android `BelowEnd`: под элементом, правым краем к его правому краю, без уголка; не помещается
    /// снизу — над элементом. `nil` — `AboveCenter` с уголком.
    var belowEnd: BelowEnd?
    let onDismiss: () -> Void
    @ViewBuilder let content: Content

    @Environment(\.theme) private var theme

    /// Высота контейнера и карточки — чтобы решить, помещается ли карточка под элементом.
    struct BelowEnd {
        let containerHeight: CGFloat
        let cardHeight: CGFloat
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            if let anchor {
                // Касание мимо — закрыть; для VoiceOver слой не элемент (закрытие — жестом «назад»).
                Color.clear
                    .contentShape(.rect)
                    .onTapGesture(perform: onDismiss)
                    .accessibilityHidden(true)
                if let belowEnd {
                    endAligned(anchor: anchor, belowEnd: belowEnd)
                } else {
                    centered(anchor: anchor)
                }
            }
        }
    }

    /// `AboveCenter`: над элементом по центру, уголком к нему; сверху мало места — под ним, уголком вверх.
    @ViewBuilder
    private func centered(anchor: CGRect) -> some View {
        let x = Self.leading(anchor: anchor, width: width, containerWidth: containerWidth)
        let below = minSpaceAbove.map { anchor.minY < $0 } ?? false
        // Переход — последним: SwiftUI берёт переход у самого внешнего вида ветки.
        if below {
            card(arrowX: anchor.midX - x, pointsUp: true)
                .frame(width: width)
                .padding(.leading, x)
                .padding(.top, anchor.maxY)
                .frame(width: containerWidth, alignment: .leading)
                .transition(Self.transition(
                    at: CGPoint(x: anchor.midX, y: anchor.maxY),
                    in: CGSize(width: containerWidth, height: anchor.maxY + (minSpaceAbove ?? 0))
                ))
        } else {
            card(arrowX: anchor.midX - x, pointsUp: false)
                .frame(width: width)
                .padding(.leading, x)
                .frame(width: containerWidth, height: max(anchor.minY, 0), alignment: .bottomLeading)
                .transition(Self.transition(
                    at: CGPoint(x: anchor.midX, y: max(anchor.minY, 0)),
                    in: CGSize(width: containerWidth, height: max(anchor.minY, 0))
                ))
        }
    }

    /// `BelowEnd`: под элементом, правые края совпадают; снизу не помещается — над элементом.
    @ViewBuilder
    private func endAligned(anchor: CGRect, belowEnd: BelowEnd) -> some View {
        let x = min(
            max(anchor.maxX - width, PopoverMetrics.screenMargin),
            max(containerWidth - PopoverMetrics.screenMargin - width, PopoverMetrics.screenMargin)
        )
        let top = anchor.maxY + PopoverMetrics.gap
        if top + belowEnd.cardHeight <= belowEnd.containerHeight {
            card(arrowX: 0, pointsUp: false, showsArrow: false)
                .frame(width: width)
                .padding(.leading, x)
                .padding(.top, top)
                .frame(width: containerWidth, alignment: .leading)
                .transition(Self.transition(
                    at: CGPoint(x: anchor.maxX, y: top),
                    in: CGSize(width: containerWidth, height: top + belowEnd.cardHeight)
                ))
        } else {
            let bottom = max(anchor.minY - PopoverMetrics.gap, 0)
            card(arrowX: 0, pointsUp: false, showsArrow: false)
                .frame(width: width)
                .padding(.leading, x)
                .frame(width: containerWidth, height: bottom, alignment: .bottomLeading)
                .transition(Self.transition(at: CGPoint(x: anchor.maxX, y: bottom), in: CGSize(width: containerWidth, height: bottom)))
        }
    }

    private func card(arrowX: CGFloat, pointsUp: Bool, showsArrow: Bool = true) -> some View {
        let arrow = showsArrow ? PopoverMetrics.arrow : .zero
        let shape = PopoverShape(radius: PopoverMetrics.radius, arrowX: arrowX, arrowSize: arrow, pointsUp: pointsUp)
        return content
            .padding(pointsUp ? .top : .bottom, arrow.height)
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

    /// Вырастает из края элемента (остриё уголка или угол капсулы) и сжимается туда же. `point` — точка
    /// роста в координатах контейнера, `size` — рамка слоя карточки (от верха контейнера).
    static func transition(at point: CGPoint, in size: CGSize) -> AnyTransition {
        let unit = UnitPoint(
            x: size.width > 0 ? point.x / size.width : 0.5,
            y: size.height > 0 ? point.y / size.height : 1
        )
        return .scale(scale: PopoverMetrics.startScale, anchor: unit).combined(with: .opacity)
    }
}

/// Размеры и цвета всплывающей карточки — как на Android (`AnchoredPopover.kt`).
private enum PopoverMetrics {
    static let radius: CGFloat = 34
    static let arrow = CGSize(width: 22, height: 9)
    static let screenMargin: CGFloat = 16
    /// Зазор между элементом и карточкой без уголка (`BelowEnd`).
    static let gap: CGFloat = 8
    static let fillOpacity = 0.94
    static let rimOpacity = 0.45
    /// Доля цвета раздела в подложке.
    static let tintShare = 0.18
    /// Масштаб в начале появления — почти размер элемента, из которого карточка растёт.
    static let startScale: CGFloat = 0.2
}

/// Скруглённая карточка с уголком снизу (`pointsUp` — сверху); уголок напротив `arrowX`, но не заходит
/// на скругления углов.
nonisolated struct PopoverShape: Shape {
    let radius: CGFloat
    let arrowX: CGFloat
    let arrowSize: CGSize
    var pointsUp = false

    func path(in rect: CGRect) -> Path {
        let bodyRect = CGRect(
            x: rect.minX,
            y: pointsUp ? rect.minY + arrowSize.height : rect.minY,
            width: rect.width,
            height: rect.height - arrowSize.height
        )
        let body = Path(roundedRect: bodyRect, cornerRadius: min(radius, bodyRect.height / 2), style: .continuous)
        let half = arrowSize.width / 2
        let lower = rect.minX + radius + half
        let upper = rect.maxX - radius - half
        let center = lower <= upper ? min(max(rect.minX + arrowX, lower), upper) : rect.midX
        // Основание уголка заходит в карточку на 1 — без шва между ними.
        let base = pointsUp ? bodyRect.minY + 1 : bodyRect.maxY - 1
        let tip = pointsUp ? rect.minY : rect.maxY
        var arrow = Path()
        arrow.move(to: CGPoint(x: center - half, y: base))
        arrow.addLine(to: CGPoint(x: center, y: tip))
        arrow.addLine(to: CGPoint(x: center + half, y: base))
        arrow.closeSubpath()
        return body.union(arrow)
    }
}
