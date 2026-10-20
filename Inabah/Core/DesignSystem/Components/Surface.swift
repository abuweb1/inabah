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
            // Без тени - без `.shadow`: даже прозрачная нулевая тень - отдельный проход отрисовки на GPU, а
            // поверхностей на экране десятки (ячейки курса - до 260 проходов на кадр, рывки прокрутки).
            if let shadow {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(fill)
                    .shadow(color: shadow.color, radius: shadow.radius, y: shadow.y)
            } else {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(fill)
            }
        }
        .overlay {
            if let border {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .strokeBorder(border, lineWidth: lineWidth)
            }
        }
    }
}
