import SwiftUI

/// Полоса фигуры у верхнего или нижнего края: `points` + доля `fraction` высоты, по контуру фигуры (скругления
/// углов сохраняются). Вместо `clipShape` поверх заливки: обрезка формой - маска, а маска - закадровый проход
/// на каждом кадре любой анимации экрана (рывки при десятках ячеек, замер 2026-10-20). `fraction` анимируется.
nonisolated struct ShapeBand<Base: Shape>: Shape {
    let base: Base
    let edge: VerticalEdge
    var points: CGFloat = 0
    var fraction: CGFloat = 0

    var animatableData: CGFloat {
        get { fraction }
        set { fraction = newValue }
    }

    func path(in rect: CGRect) -> Path {
        let height = min(max(points + fraction * rect.height, 0), rect.height)
        guard height > 0 else { return Path() }
        let band = CGRect(
            x: rect.minX,
            y: edge == .top ? rect.minY : rect.maxY - height,
            width: rect.width,
            height: height
        )
        return base.path(in: rect).intersection(Path(band))
    }
}
