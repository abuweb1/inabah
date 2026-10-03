import SwiftUI

/// Сворачивание блока по высоте с обрезкой — как `max-height` + `overflow: hidden` в прототипе.
///
/// Блок всегда остаётся в иерархии: меняется только высота рамки (натуральная ↔ 0),
/// содержимое прижато к верху и обрезается снизу. Поэтому при сворачивании оно «уходит»
/// под элемент над ним, а не уезжает поверх соседних карточек, и лента не прыгает.
/// `fixedSize` держит содержимое в натуральной высоте, чтобы текст не переверстывался
/// во время анимации.
private struct CollapsibleModifier: ViewModifier {
    let isExpanded: Bool

    func body(content: Content) -> some View {
        content
            .fixedSize(horizontal: false, vertical: true)
            .frame(height: isExpanded ? nil : 0, alignment: .top)
            .clipped()
            .allowsHitTesting(isExpanded)
            .accessibilityHidden(!isExpanded)
    }
}

extension View {
    /// Анимацию задаёт вызывающий код (`withAnimation` или `.animation(_:value:)` выше по иерархии).
    func collapsible(isExpanded: Bool) -> some View {
        modifier(CollapsibleModifier(isExpanded: isExpanded))
    }
}
