import SwiftUI

/// Корень настроек: список разделов. У корня своя тема (графит), у разделов — фон своего раздела.
struct SettingsView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        List {
            Section {
                ForEach(SettingsRoute.allCases, id: \.self) { route in
                    NavigationLink(value: route) {
                        SettingsSectionRow(route: route)
                    }
                    .settingsRow()
                }
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .navigationTitle(Text("settings.title"))
        .audioPlayerInset()
    }
}

/// Строка раздела: значок в цвет раздела, название и краткое описание.
private struct SettingsSectionRow: View {
    let route: SettingsRoute

    @Environment(\.theme) private var theme

    private enum Layout {
        static let iconSize: CGFloat = 32
    }

    var body: some View {
        HStack(spacing: Spacing.m) {
            Image(systemName: route.symbolName)
                .font(.title3)
                .foregroundStyle(theme.palette.onAccent)
                .frame(width: Layout.iconSize, height: Layout.iconSize)
                .background(route.tint(in: theme).linear, in: .rect(cornerRadius: Radius.small))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Text(route.title)
                    .font(.body)
                Text(route.subtitle)
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .padding(.vertical, Spacing.xxs)
    }
}

private extension SettingsRoute {
    var title: LocalizedStringResource {
        switch self {
        case .azkar: "settings.azkar.title"
        case .hadith: "settings.hadith.title"
        }
    }

    var subtitle: LocalizedStringResource {
        switch self {
        case .azkar: "settings.azkar.subtitle"
        case .hadith: "settings.hadith.subtitle"
        }
    }

    var symbolName: String {
        switch self {
        case .azkar: "hands.and.sparkles.fill"
        case .hadith: "book.closed.fill"
        }
    }

    /// Плашка значка — в цвет раздела.
    func tint(in theme: Theme) -> ThemeGradient {
        switch self {
        case .azkar: theme.gradients.morningCard
        case .hadith: theme.gradients.nawawiCard
        }
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
    .appEnvironment(.preview)
}
