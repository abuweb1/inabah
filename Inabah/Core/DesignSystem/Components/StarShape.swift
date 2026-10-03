import SwiftUI

/// Многолучевая звезда — декоративный орнамент на главных экранах разделов.
/// `nonisolated`: `Shape` вычисляет путь вне главного актора; тип — чистая геометрия без состояния.
nonisolated struct StarShape: Shape {
    private let points = 8
    /// Радиус внутренних вершин относительно внешнего.
    private let innerRatio: CGFloat = 0.35

    func path(in rect: CGRect) -> Path {
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let outer = min(rect.width, rect.height) / 2
        let inner = outer * innerRatio
        let vertexCount = points * 2
        var path = Path()
        for index in 0..<vertexCount {
            let radius = index.isMultiple(of: 2) ? outer : inner
            let angle = Double(index) / Double(vertexCount) * 2 * .pi - .pi / 2
            let point = CGPoint(x: center.x + radius * cos(angle), y: center.y + radius * sin(angle))
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}

/// Звезда с кругом в центре, как в шапке прототипа.
struct DecorativeStar: View {
    var body: some View {
        ZStack {
            StarShape()
            Circle().scale(0.16)
        }
        .accessibilityHidden(true)
    }
}
