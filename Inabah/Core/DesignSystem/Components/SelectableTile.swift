import SwiftUI

/// Плитка выбора одного варианта из нескольких (иконка приложения, палитра): превью,
/// подпись, отметка; выбранная — в рамке акцента.
struct SelectableTile<Preview: View>: View {
    let title: LocalizedStringResource
    var subtitle: LocalizedStringResource?
    let isSelected: Bool
    /// Скругление превью; рамка выбора повторяет его с зазором.
    let cornerRadius: CGFloat
    let action: () -> Void
    @ViewBuilder let preview: Preview

    @Environment(\.theme) private var theme

    private typealias Layout = SelectableTileMetrics

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.s) {
                preview
                    .clipShape(.rect(cornerRadius: cornerRadius, style: .continuous))
                    .padding(Layout.selectionInset)
                    .overlay {
                        RoundedRectangle(cornerRadius: cornerRadius + Layout.selectionInset, style: .continuous)
                            .strokeBorder(isSelected ? theme.palette.accentLight : .clear, lineWidth: Size.ringStroke)
                    }
                VStack(spacing: Spacing.xxxs) {
                    Text(title)
                        .font(.footnote)
                        .foregroundStyle(theme.palette.onAccent)
                    if let subtitle {
                        Text(subtitle)
                            .font(.caption2)
                            .foregroundStyle(theme.palette.onAccentSecondary)
                    }
                }
                .multilineTextAlignment(.center)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.body)
                    .foregroundStyle(isSelected ? theme.palette.accentLight : theme.palette.onAccentTertiary)
                    .accessibilityHidden(true)
            }
            .frame(maxWidth: .infinity)
            .contentShape(.rect)
        }
        .buttonStyle(PressScaleButtonStyle())
        .animation(Motion.highlight, value: isSelected)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Колонки сетки плиток выбора: две, а при размерах шрифта для доступности — одна,
/// чтобы подписи не переносились по слогам в узкой колонке.
enum SelectionGrid {
    static func columns(for dynamicTypeSize: DynamicTypeSize) -> [GridItem] {
        let count = dynamicTypeSize.isAccessibilitySize ? 1 : 2
        return Array(repeating: GridItem(.flexible(), spacing: Spacing.l), count: count)
    }
}

/// Размеры плитки выбора (вне дженерика: хранимые статические свойства в нём запрещены).
private enum SelectableTileMetrics {
    /// Зазор между превью и рамкой выбора.
    static let selectionInset: CGFloat = 4
}
