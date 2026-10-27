import SwiftUI

/// Панель снизу поверх экрана (Android `SlideUpSheet`; справка «Как повысить точность»): затемнение, выезд
/// снизу, не выше 85 % экрана, содержимое прокручивается. Закрытие — смахиванием за полоску, касанием по
/// затемнению или жестом VoiceOver «назад». Свой слой, не системный `.sheet`: вид и поведение задаём мы.
/// Слой — во весь экран поверх содержимого; низ панели уходит под панель вкладок, содержимое — над ней.
struct SlideUpSheet<Content: View>: View {
    let isPresented: Bool
    let onDismiss: () -> Void
    @ViewBuilder let content: Content

    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private typealias Layout = SlideUpSheetLayout

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .bottom) {
                if isPresented {
                    theme.palette.shadow.opacity(Layout.scrimOpacity)
                        .ignoresSafeArea()
                        .contentShape(.rect)
                        .onTapGesture(perform: onDismiss)
                        .accessibilityHidden(true)
                        .transition(.opacity)
                    SlideUpSheetPanel(maxHeight: proxy.size.height * Layout.maxHeightShare, onDismiss: onDismiss) {
                        content
                    }
                    // «Уменьшение движения» - проявляется на месте, без выезда.
                    .transition(reduceMotion ? .opacity : .move(edge: .bottom))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            // На самом слое, не на `GeometryReader`: снаружи выезд не анимировался - панель появлялась резко.
            .animation(Motion.collapse, value: isPresented)
        }
    }
}

/// Константы панели - вне обобщённых типов (в них нельзя хранимые статические свойства).
private enum SlideUpSheetLayout {
    static let scrimOpacity = 0.45
    static let maxHeightShare: CGFloat = 0.85
    static let grabberStrip: CGFloat = 24
    /// Смахнули дальше этого - закрыть (как Android).
    static let dismissDistance: CGFloat = 96
}

private struct SlideUpSheetPanel<Content: View>: View {
    let maxHeight: CGFloat
    let onDismiss: () -> Void
    @ViewBuilder let content: Content

    @Environment(\.theme) private var theme
    @State private var dragOffset: CGFloat = 0

    private typealias Layout = SlideUpSheetLayout

    var body: some View {
        VStack(spacing: 0) {
            grabber
            // Высота известна сразу, до выезда: содержимое целиком, если помещается, иначе прокрутка. Замер
            // высоты после появления давал выезд пустой полоски и рывок содержимого следом.
            ViewThatFits(in: .vertical) {
                column
                ScrollView { column }
                    .scrollBounceBehavior(.basedOnSize)
            }
            .frame(maxHeight: max(maxHeight - Layout.grabberStrip, 0))
        }
        .frame(maxWidth: .infinity)
        .background {
            let shape = UnevenRoundedRectangle(topLeadingRadius: Radius.panel, topTrailingRadius: Radius.panel)
            shape
                .fill(theme.palette.background)
                .overlay { shape.stroke(theme.palette.hairline, lineWidth: Size.hairline) }
                .shadow(
                    color: ShadowToken.floating(theme.palette).color,
                    radius: ShadowToken.floating(theme.palette).radius,
                    y: ShadowToken.floating(theme.palette).y
                )
                .ignoresSafeArea(edges: .bottom)
        }
        // Касания по панели не доходят до затемнения.
        .contentShape(.rect)
        .offset(y: dragOffset)
        // Контейнер: иначе признак модального окна расходится по отдельным текстам, а панель им не становится.
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, onDismiss)
    }

    private var column: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.xxl)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.xl)
    }

    /// Полоска сверху - за неё панель смахивается вниз (содержимое под ней прокручивается своим жестом).
    private var grabber: some View {
        Capsule()
            .fill(theme.palette.track)
            .frame(width: Size.grabber.width, height: Size.grabber.height)
            .frame(maxWidth: .infinity, minHeight: Layout.grabberStrip)
            .contentShape(.rect)
            .gesture(
                DragGesture()
                    .onChanged { dragOffset = max($0.translation.height, 0) }
                    .onEnded { value in
                        if value.translation.height > Layout.dismissDistance {
                            onDismiss()
                        } else {
                            withAnimation(Motion.collapse) { dragOffset = 0 }
                        }
                    }
            )
            .accessibilityHidden(true)
    }
}
