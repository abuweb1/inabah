import SwiftUI

/// Строка списка: полоска статуса, номер арабскими цифрами, начало перевода, передатчик, значок.
/// Статус приходит готовым значением — строка не читает наблюдаемые объекты.
struct HadithRow: View {
    let hadith: Hadith
    let status: HadithStatus

    @Environment(\.theme) private var theme

    private enum Layout {
        static let accentMinHeight: CGFloat = 44
        static let badgeSize: CGFloat = 34
        static let badgeNumberSize: Double = 21
        static let previewLines = 2
    }

    var body: some View {
        HStack(spacing: Spacing.m) {
            Capsule()
                .fill(status.stripe(in: theme))
                .frame(width: Size.accentStripe)
                .frame(minHeight: Layout.accentMinHeight)

            numberBadge

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(hadith.previewText)
                    .font(.subheadline)
                    .foregroundStyle(theme.palette.onAccent)
                    .lineLimit(Layout.previewLines)
                    .multilineTextAlignment(.leading)
                if let narrator = hadith.translation?.narrator {
                    Text(narrator)
                        .font(.caption)
                        .foregroundStyle(theme.palette.goldMuted)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Image(systemName: status.rowSymbolName)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(status == .none ? theme.palette.onAccentTertiary : status.tint(in: theme))
        }
        .padding(.vertical, Spacing.m)
        .contentShape(.rect)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(theme.palette.divider)
                .frame(height: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityValue(status.accessibilityLabel.map { Text($0) } ?? Text(verbatim: ""))
    }

    private var numberBadge: some View {
        // `verbatim`: `Text(_:format:)` подставляет локаль окружения и вернул бы латинские цифры.
        Text(verbatim: hadith.number.formatted(.arabicIndic))
            .font(.arabic(size: Layout.badgeNumberSize))
            .foregroundStyle(status.tint(in: theme))
            .frame(width: Layout.badgeSize, height: Layout.badgeSize)
            .surface(status.badgeFill(in: theme), cornerRadius: Radius.control, border: status.badgeBorder(in: theme))
            .accessibilityLabel(Text("hadith.detail.number \(hadith.number)"))
    }
}
