import SwiftUI

// Толчок, тряска и «дыхание» (Android `popOn`, `shakeOn`, `breathing` в `ReadingEffects.kt`): только масштаб
// и смещение отрисовки, без вёрстки; толчок и тряска на первом значении не играют.

extension View {
    /// Толчок ×1,08 и обратно (260 мс) при каждом изменении `value`: прочитанное слово, верный ответ.
    func popOnChange<Value: Equatable & Sendable>(of value: Value) -> some View {
        modifier(PopOnChange(value: value))
    }

    /// Тряска ±8 pt (380 мс) при каждом изменении `value`: неверный ответ.
    func shakeOnChange<Value: Equatable & Sendable>(of value: Value) -> some View {
        modifier(ShakeOnChange(value: value))
    }

    /// «Дыхание»: масштаб 1 ↔ `scale` (1,1 с в одну сторону), пока `isActive`.
    func breathing(isActive: Bool = true, scale: CGFloat = Breathing.defaultScale) -> some View {
        modifier(Breathing(isActive: isActive, scale: scale))
    }
}

private struct PopOnChange<Value: Equatable & Sendable>: ViewModifier {
    let value: Value

    func body(content: Content) -> some View {
        content.keyframeAnimator(initialValue: CGFloat(1), trigger: value) { view, scale in
            view.scaleEffect(scale)
        } keyframes: { _ in
            KeyframeTrack {
                LinearKeyframe(1.08, duration: 0.26 / 3)
                LinearKeyframe(1, duration: 0.26 * 2 / 3)
            }
        }
    }
}

/// Кадры Android: влево, вправо, затухая; 380 мс на 6 кадров, размах 8 pt.
private struct ShakeOnChange<Value: Equatable & Sendable>: ViewModifier {
    let value: Value

    func body(content: Content) -> some View {
        content.keyframeAnimator(initialValue: CGFloat(0), trigger: value) { view, offset in
            view.offset(x: offset)
        } keyframes: { _ in
            KeyframeTrack {
                LinearKeyframe(-8, duration: 0.38 / 6)
                LinearKeyframe(8, duration: 0.38 / 6)
                LinearKeyframe(-5.6, duration: 0.38 / 6)
                LinearKeyframe(4, duration: 0.38 / 6)
                LinearKeyframe(-1.6, duration: 0.38 / 6)
                LinearKeyframe(0, duration: 0.38 / 6)
            }
        }
    }
}

struct Breathing: ViewModifier {
    static let defaultScale: CGFloat = 1.08

    let isActive: Bool
    let scale: CGFloat

    @State private var expanded = false

    func body(content: Content) -> some View {
        content
            .scaleEffect(expanded ? scale : 1)
            .onChange(of: isActive, initial: true) { _, active in
                if active {
                    withAnimation(Motion.breathe) { expanded = true }
                } else {
                    withAnimation(Motion.press) { expanded = false }
                }
            }
    }
}
