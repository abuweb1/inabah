import SwiftUI

/// Карточка-сообщение: значок, заголовок, пояснение и, если нужно, кнопки под ними —
/// «Скоро» в «Махрадже», пустое «Избранное», предложение включить напоминания.
/// Общая с Android (`NoticeCard`): отступы 22 × 20, фон и рамка — как у заглушек.
struct NoticeCard<Actions: View>: View {
    let symbol: String
    let title: LocalizedStringResource
    let message: LocalizedStringResource
    @ViewBuilder let actions: Actions

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: Spacing.s) {
            VStack(spacing: Spacing.s) {
                // Размеры — как у карточек главных (на ступень меньше прежних).
                Image(systemName: symbol)
                    .font(.title3)
                    .foregroundStyle(theme.palette.gold)
                    .accessibilityHidden(true)
                // Заголовок — отдельным элементом-заголовком, пояснение — обычным текстом (аудит iOS 1.1.1, А10).
                Text(title)
                    .font(.headline)
                    .foregroundStyle(theme.palette.onAccent)
                    .accessibilityAddTraits(.isHeader)
                Text(message)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
            actions
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.xxl)
        .padding(.horizontal, Spacing.xlPlus)
        .surface(theme.palette.subtleFill, cornerRadius: Radius.navCard, border: theme.palette.hairline)
    }
}

extension NoticeCard where Actions == EmptyView {
    init(symbol: String, title: LocalizedStringResource, message: LocalizedStringResource) {
        self.init(symbol: symbol, title: title, message: message) { EmptyView() }
    }
}
