import SwiftUI

extension View {
    /// Скруглённая поверхность: заливка, рамка и тень.
    ///
    /// Тень — у фоновой фигуры, а не у содержимого: у фигуры есть контур, и тень не пересчитывается,
    /// когда внутри что-то анимируется (иначе — покадровая перерисовка тени, рывки прокрутки).
    func surface<Fill: ShapeStyle>(
        _ fill: Fill,
        cornerRadius: CGFloat,
        border: Color? = nil,
        lineWidth: CGFloat = 1,
        shadow: ShadowToken? = nil
    ) -> some View {
        background {
            RoundedRectangle(cornerRadius: cornerRadius)
                .fill(fill)
                .shadow(
                    color: shadow?.color ?? .clear,
                    radius: shadow?.radius ?? 0,
                    y: shadow?.y ?? 0
                )
        }
        .overlay {
            if let border {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .strokeBorder(border, lineWidth: lineWidth)
            }
        }
    }
}
