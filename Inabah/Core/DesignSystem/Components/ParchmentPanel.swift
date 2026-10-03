import SwiftUI

/// «Пергамент» под арабским текстом: тёплый градиент, зелёная рамка, полоска сверху,
/// орнаменты ✦ по углам. Цвета не зависят от темы интерфейса — текст одинаково читается в любой теме.
struct ParchmentPanel<Content: View>: View {
    /// Скругление верхних углов: совпадает с карточкой, когда пергамент — её первый блок,
    /// и 0, когда над ним заголовок выполненной карточки.
    var topCornerRadius: CGFloat = Radius.card
    /// Нижние углы: прямые, когда под пергаментом продолжается карточка (зикр), скруглённые —
    /// когда пергамент отдельный блок (экран хадиса).
    var bottomCornerRadius: CGFloat = 0
    @ViewBuilder let content: Content

    @Environment(\.theme) private var theme

    private var shape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(
            topLeadingRadius: topCornerRadius,
            bottomLeadingRadius: bottomCornerRadius,
            bottomTrailingRadius: bottomCornerRadius,
            topTrailingRadius: topCornerRadius
        )
    }

    var body: some View {
        content
            .padding(.top, Spacing.xxl)
            .padding(.bottom, Spacing.l)
            .padding(.horizontal, Spacing.xxl)
            .frame(maxWidth: .infinity)
            .background { background }
            .overlay { ornaments }
            .clipShape(shape)
            .overlay { shape.strokeBorder(theme.palette.successDeep, lineWidth: 2) }
    }

    private var background: some View {
        ZStack(alignment: .top) {
            theme.gradients.parchment.linear
            // Блик в левом верхнем углу, как в прототипе.
            RadialGradient(
                colors: [theme.palette.parchmentLight.opacity(0.7), .clear],
                center: .init(x: 0.3, y: 0.2),
                startRadius: 0,
                endRadius: ParchmentMetrics.highlightRadius
            )
            LinearGradient(
                colors: [theme.palette.successDeep, theme.palette.success, theme.palette.successDeep],
                startPoint: .leading, endPoint: .trailing
            )
            .frame(height: Size.accentStripe)
            .opacity(0.6)
        }
    }

    private var ornaments: some View {
        let corners: [Alignment] = [.topLeading, .topTrailing, .bottomLeading, .bottomTrailing]
        return ZStack {
            ForEach(corners.indices, id: \.self) { index in
                Text(verbatim: "✦")
                    .font(.system(size: Size.ornament))
                    .foregroundStyle(theme.palette.successDeep)
                    .padding(Spacing.s)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: corners[index])
            }
        }
        .accessibilityHidden(true)
    }
}

/// Размеры пергамента (вне дженерика: хранимые статические свойства в нём запрещены).
private enum ParchmentMetrics {
    static let highlightRadius: CGFloat = 180
}
