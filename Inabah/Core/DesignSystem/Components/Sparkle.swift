import SwiftUI

/// Четырёхконечная звёздочка «✦» с вогнутыми сторонами - вектором, как Android `Sparkle.kt`: одинаковая
/// на обеих платформах и не зависит от шрифта. `nonisolated`: `Shape` - чистая геометрия.
nonisolated struct SparkleShape: Shape {
    /// «Талия» - контрольные точки кривых на этой доле полуразмера от центра: меньше - тоньше лучи.
    var waist: CGFloat = 0.2

    func path(in rect: CGRect) -> Path {
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let kx = rect.width / 2 * waist
        let ky = rect.height / 2 * waist
        var path = Path()
        path.move(to: CGPoint(x: center.x, y: rect.minY))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: center.y), control: CGPoint(x: center.x + kx, y: center.y - ky))
        path.addQuadCurve(to: CGPoint(x: center.x, y: rect.maxY), control: CGPoint(x: center.x + kx, y: center.y + ky))
        path.addQuadCurve(to: CGPoint(x: rect.minX, y: center.y), control: CGPoint(x: center.x - kx, y: center.y + ky))
        path.addQuadCurve(to: CGPoint(x: center.x, y: rect.minY), control: CGPoint(x: center.x - kx, y: center.y - ky))
        path.closeSubpath()
        return path
    }
}

/// Вспышка звёздочек: разлетаются по кругу от центра, поворачиваясь, уменьшаются и гаснут (Android
/// `SparkleBurst`, `ReadingEffects.kt`). Играет один раз при появлении; кадры - отрисовкой, без вёрстки.
struct SparkleBurst: View {
    let color: Color
    var count = Layout.count
    /// Длительность вспышки в секундах; повтор - новым экземпляром (`.id`).
    var duration = Layout.duration

    @State private var progress = 0.0

    fileprivate enum Layout {
        static let count = 8
        static let duration = 0.7
        /// Звёздочка в начале вспышки; к концу - вдвое меньше.
        static let starSize: CGFloat = 10
        static let waist: CGFloat = 0.22
        /// Радиус разлёта: от этой доли полуразмера до края.
        static let startRadius = 0.35
        /// Поворот всего круга за вспышку, в радианах.
        static let spin = 0.6
    }

    var body: some View {
        SparkleBurstCanvas(progress: progress, color: color, count: count)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            .onAppear {
                withAnimation(Motion.sparkleBurst(duration: duration)) { progress = 1 }
            }
    }
}

/// Кадр вспышки при доле `progress`: `Animatable` - SwiftUI рисует промежуточные кадры.
private struct SparkleBurstCanvas: View, Animatable {
    var progress: Double
    let color: Color
    let count: Int

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        Canvas { context, size in
            guard progress > 0, progress < 1 else { return }
            typealias Layout = SparkleBurst.Layout
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) / 2 * (Layout.startRadius + (1 - Layout.startRadius) * progress)
            let star = Layout.starSize * (1 - progress / 2)
            let shading = GraphicsContext.Shading.color(color.opacity(1 - progress))
            for index in 0..<count {
                let angle = 2 * .pi * Double(index) / Double(count) + Layout.spin * progress
                let point = CGPoint(x: center.x + radius * cos(angle), y: center.y + radius * sin(angle))
                let rect = CGRect(x: point.x - star / 2, y: point.y - star / 2, width: star, height: star)
                context.fill(SparkleShape(waist: Layout.waist).path(in: rect), with: shading)
            }
        }
    }
}

#Preview {
    HStack(spacing: Spacing.xxxl) {
        SparkleShape().fill(.orange).frame(width: 18, height: 18)
        SparkleBurst(color: .orange).frame(width: 60, height: 60)
    }
    .padding()
}
