import SwiftUI

/// Полоса прогресса с перетаскиваемым бегунком.
///
/// Своя реализация вместо `Slider`: системный слайдер иногда не сообщал об окончании
/// перетаскивания, и бегунок «застревал». Позиция пальца живёт в `@GestureState` —
/// SwiftUI сам сбрасывает её при окончании или отмене жеста, зависнуть бегунок не может.
///
/// Производительность: позиция меняется несколько раз в секунду всё время воспроизведения,
/// поэтому движение — только трансформации отрисовки (`scaleEffect` заливки, `offset` бегунка),
/// без изменения размеров и без `GeometryReader`: вёрстка не пересчитывается на каждом кадре
/// и не конкурирует с прокруткой ленты. Ширина полосы измеряется только при её изменении.
struct ScrubBar: View {
    /// Текущая позиция воспроизведения, 0…1.
    let fraction: Double
    let trackColor: Color
    let fillColor: Color
    let thumbColor: Color
    /// Позиция пальца во время перетаскивания (0…1) или `nil` — чтобы показать время под полосой.
    var onScrubChange: (Double?) -> Void = { _ in }
    /// Палец отпущен — перемотать в эту позицию (0…1).
    let onSeek: (Double) -> Void

    @GestureState private var dragFraction: Double?
    @State private var width: CGFloat = 1

    private static let thumbSize: CGFloat = 16
    private static let activeThumbScale: CGFloat = 1.5

    var body: some View {
        let shown = (dragFraction ?? fraction).clamped(to: 0...1)
        let isDragging = dragFraction != nil

        ZStack(alignment: .leading) {
            Capsule()
                .fill(trackColor)
                .frame(height: Size.progressBarHeight)
            Capsule()
                .fill(fillColor)
                .frame(height: Size.progressBarHeight)
                .scaleEffect(x: shown, anchor: .leading)
            Circle()
                .fill(thumbColor)
                .frame(width: Self.thumbSize, height: Self.thumbSize)
                .scaleEffect(isDragging ? Self.activeThumbScale : 1)
                .offset(x: width * shown - Self.thumbSize / 2)
        }
        // Зона касания выше самой полосы — по бегунку легко попасть пальцем.
        .frame(maxWidth: .infinity, minHeight: Size.minTapTarget)
        .contentShape(.rect)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = max($0, 1) }
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($dragFraction) { value, state, _ in
                    state = (value.location.x / width).clamped(to: 0...1)
                }
                .onEnded { value in
                    onSeek((value.location.x / width).clamped(to: 0...1))
                }
        )
        .animation(.snappy(duration: 0.15), value: isDragging)
        // Между отсчётами позиции бегунок едет линейно; при перетаскивании — строго за пальцем.
        .animation(isDragging ? nil : .linear(duration: Motion.progressTickSeconds), value: fraction)
        .onChange(of: dragFraction) { _, value in onScrubChange(value) }
    }
}
