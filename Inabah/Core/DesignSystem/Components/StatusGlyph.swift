import SwiftUI

/// Значки «прочитан» / «выучен» в стиле приложения: открытая книга со строками текста и сердце
/// с восьмиконечной звездой (тот же орнамент, что на главных экранах) — «хранится в сердце».
///
/// Залитый вариант — отмечено, контурный — ещё нет. Цвет — из `foregroundStyle`, размер
/// масштабируется с Dynamic Type.
struct StatusGlyph: View {
    enum Kind: Sendable {
        case read
        case memorized
    }

    let kind: Kind
    var isFilled = true
    @ScaledMetric private var size: CGFloat

    init(_ kind: Kind, isFilled: Bool = true, size: CGFloat, relativeTo textStyle: Font.TextStyle = .body) {
        self.kind = kind
        self.isFilled = isFilled
        _size = ScaledMetric(wrappedValue: size, relativeTo: textStyle)
    }

    var body: some View {
        // Even-odd: строки книги и звезда в сердце — «вырезы» в залитой фигуре.
        StatusGlyphShape(kind: kind, isFilled: isFilled)
            .fill(style: FillStyle(eoFill: true))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// Геометрия значка в единичном квадрате. `nonisolated`: `Shape` строит путь вне главного актора.
nonisolated struct StatusGlyphShape: Shape {
    let kind: StatusGlyph.Kind
    let isFilled: Bool

    /// Толщина контура — доля размера значка.
    private static let strokeRatio: CGFloat = 0.075

    func path(in rect: CGRect) -> Path {
        let outline = outlinePath(in: rect)
        let detail = detailPath(in: rect)
        guard !isFilled else {
            var path = outline
            path.addPath(detail)
            return path
        }
        let lineWidth = min(rect.width, rect.height) * Self.strokeRatio
        var path = outline.strokedPath(StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
        path.addPath(detail)
        return path
    }

    private func outlinePath(in rect: CGRect) -> Path {
        switch kind {
        case .read: Self.book(in: rect)
        case .memorized: Self.heart(in: rect)
        }
    }

    private func detailPath(in rect: CGRect) -> Path {
        switch kind {
        case .read: Self.bookLines(in: rect)
        case .memorized: Self.heartStar(in: rect)
        }
    }

    // MARK: - Книга

    /// Две страницы раскрытой книги с изогнутым верхним и нижним краем и зазором-корешком.
    private static func book(in rect: CGRect) -> Path {
        let p = unitPoint(in: rect)
        var path = Path()
        for side in [-1.0, 1.0] {
            let spine = 0.5 + side * 0.035
            let edge = 0.5 + side * 0.46
            let middle = 0.5 + side * 0.24
            path.move(to: p(spine, 0.26))
            path.addQuadCurve(to: p(edge, 0.19), control: p(middle, 0.11))
            path.addLine(to: p(edge, 0.79))
            path.addQuadCurve(to: p(spine, 0.88), control: p(middle, 0.71))
            path.closeSubpath()
        }
        return path
    }

    /// Строки текста на страницах — по три на каждой, повторяют изгиб страницы.
    private static func bookLines(in rect: CGRect) -> Path {
        let p = unitPoint(in: rect)
        let thickness = min(rect.width, rect.height) * 0.05
        var path = Path()
        for side in [-1.0, 1.0] {
            for row in 0..<3 {
                let y = 0.38 + Double(row) * 0.14
                var line = Path()
                line.move(to: p(0.5 + side * 0.12, y))
                line.addQuadCurve(to: p(0.5 + side * 0.36, y - 0.04), control: p(0.5 + side * 0.24, y - 0.06))
                path.addPath(line.strokedPath(StrokeStyle(lineWidth: thickness, lineCap: .round)))
            }
        }
        return path
    }

    // MARK: - Сердце

    private static func heart(in rect: CGRect) -> Path {
        let p = unitPoint(in: rect)
        var path = Path()
        path.move(to: p(0.5, 0.90))
        path.addCurve(to: p(0.05, 0.38), control1: p(0.20, 0.68), control2: p(0.05, 0.55))
        path.addCurve(to: p(0.28, 0.12), control1: p(0.05, 0.22), control2: p(0.15, 0.12))
        path.addCurve(to: p(0.5, 0.25), control1: p(0.40, 0.12), control2: p(0.47, 0.18))
        path.addCurve(to: p(0.72, 0.12), control1: p(0.53, 0.18), control2: p(0.60, 0.12))
        path.addCurve(to: p(0.95, 0.38), control1: p(0.85, 0.12), control2: p(0.95, 0.22))
        path.addCurve(to: p(0.5, 0.90), control1: p(0.95, 0.55), control2: p(0.80, 0.68))
        path.closeSubpath()
        return path
    }

    /// Восьмиконечная звезда в центре сердца.
    private static func heartStar(in rect: CGRect) -> Path {
        let side = min(rect.width, rect.height) * 0.38
        let center = CGPoint(x: rect.minX + rect.width * 0.5, y: rect.minY + rect.height * 0.46)
        return StarShape().path(in: CGRect(
            x: center.x - side / 2,
            y: center.y - side / 2,
            width: side,
            height: side
        ))
    }

    private static func unitPoint(in rect: CGRect) -> (Double, Double) -> CGPoint {
        { x, y in CGPoint(x: rect.minX + x * rect.width, y: rect.minY + y * rect.height) }
    }
}

#Preview {
    let palette = Theme.inabah.palette
    HStack(spacing: 24) {
        VStack(spacing: 12) {
            StatusGlyph(.read, size: 44)
            StatusGlyph(.read, isFilled: false, size: 44)
        }
        .foregroundStyle(palette.statusRead)
        VStack(spacing: 12) {
            StatusGlyph(.memorized, size: 44)
            StatusGlyph(.memorized, isFilled: false, size: 44)
        }
        .foregroundStyle(palette.statusMemorized)
    }
    .padding(32)
    .background(palette.card)
}
