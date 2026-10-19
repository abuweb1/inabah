import SwiftUI

/// Ряд ячеек с долями ширины и одной высотой - по самой высокой (Android `Row` с `weight` и
/// `IntrinsicSize.Min`). `rightToLeft` - первая ячейка справа: арабские таблицы читаются справа налево,
/// а текст внутри ячеек остаётся как есть.
struct WeightedRow: Layout {
    /// Доли ширины по порядку ячеек; не задано - поровну.
    var weights: [CGFloat] = []
    /// `Layout` - вне главного актора, токены `Spacing` по умолчанию недоступны: передаётся вызывающим.
    var spacing: CGFloat
    var rightToLeft = true

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let widths = widths(total: proposal.width ?? fallbackWidth(subviews), count: subviews.count)
        let height = zip(subviews, widths).map { $0.sizeThatFits(ProposedViewSize(width: $1, height: nil)).height }.max() ?? 0
        return CGSize(width: proposal.width ?? widths.reduce(0, +) + spacing * CGFloat(max(subviews.count - 1, 0)), height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let widths = widths(total: bounds.width, count: subviews.count)
        var x = rightToLeft ? bounds.maxX : bounds.minX
        for (subview, width) in zip(subviews, widths) {
            if rightToLeft { x -= width }
            subview.place(
                at: CGPoint(x: x, y: bounds.minY),
                proposal: ProposedViewSize(width: width, height: bounds.height)
            )
            x += rightToLeft ? -spacing : width + spacing
        }
    }

    private func widths(total: CGFloat, count: Int) -> [CGFloat] {
        guard count > 0 else { return [] }
        let shares = (0..<count).map { $0 < weights.count ? weights[$0] : 1 }
        let available = max(total - spacing * CGFloat(count - 1), 0)
        let sum = shares.reduce(0, +)
        return shares.map { available * $0 / sum }
    }

    private func fallbackWidth(_ subviews: Subviews) -> CGFloat {
        subviews.map { $0.sizeThatFits(.unspecified).width }.reduce(0, +)
    }
}
