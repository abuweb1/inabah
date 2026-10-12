import SwiftUI

/// Серия дней под аятом на главной «Азкаров» (решение пользователя 2026-10-11, Android
/// `AzkarStreakCapsule`, снимки `13a`, `13b`): «✦ 12 дней подряд ›», без серии — «✦ Начните серию
/// сегодня ›»; нажатие открывает «Календарь азкаров». Пока вечерние не загружены («сегодня» неизвестно),
/// капсулы нет — ноль не мелькает.
///
/// Отдельная вьюха: историю и «сегодня» читает сама — главная не перерисовывается при каждой записи истории.
struct AzkarStreakCapsule: View {
    @Environment(AzkarHistory.self) private var history
    @Environment(AzkarStore.self) private var store
    @Environment(\.theme) private var theme

    /// Значки рядом с текстом интерфейса растут вместе с ним.
    @ScaledMetric(relativeTo: .subheadline) private var iconSize: CGFloat = 16

    var body: some View {
        if let today = store.streakDay {
            let streak = AzkarStreak(days: history.days, today: today)
            NavigationLink(value: AzkarRoute.calendar) {
                HStack(spacing: Spacing.xs) {
                    Image("tabIconAzkar")
                        .renderingMode(.template)
                        .resizable()
                        .scaledToFit()
                        .frame(width: iconSize, height: iconSize)
                        .foregroundStyle(theme.palette.gold)
                    Text(Self.text(current: streak.current))
                        .font(.subheadline)
                        .foregroundStyle(theme.palette.onAccent)
                    Image(systemName: "chevron.right")
                        .font(.system(size: iconSize * Self.chevronScale, weight: .semibold))
                        .foregroundStyle(theme.palette.onAccentSecondary)
                }
            }
            .buttonStyle(GlassCapsuleButtonStyle(tint: theme.palette.tabAzkar))
            .accessibilityHint(Text("azkar.calendar.open"))
        }
    }

    /// SF-шеврон с полями уже Material-значка: при равном кегле он крупнее — уменьшаем до вида Android.
    private static let chevronScale: CGFloat = 0.75

    /// «12 дней подряд» (`AzkarStreak.daysText`); без серии — «Начните серию сегодня».
    static func text(current: Int) -> LocalizedStringResource {
        current > 0 ? AzkarStreak.daysText(current) : "azkar.streak.start"
    }
}
