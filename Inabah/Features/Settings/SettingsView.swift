import SwiftUI

/// Корень настроек: список разделов. У корня своя тема (графит), у разделов — фон своего раздела.
struct SettingsView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        List {
            Section {
                ForEach(SettingsSection.allCases, id: \.self) { section in
                    NavigationLink(value: section.route) {
                        SettingsSectionRow(section: section)
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

/// Разделы в корне настроек (маршрутов в настройках больше — у разделов есть свои подэкраны).
private enum SettingsSection: CaseIterable {
    case azkar
    case hadith

    var route: SettingsRoute {
        switch self {
        case .azkar: .azkar
        case .hadith: .hadith
        }
    }

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

/// Строка раздела: значок в цвет раздела, название и краткое описание.
private struct SettingsSectionRow: View {
    let section: SettingsSection

    @Environment(\.theme) private var theme

    private enum Layout {
        static let iconSize: CGFloat = 32
    }

    var body: some View {
        HStack(spacing: Spacing.m) {
            Image(systemName: section.symbolName)
                .font(.title3)
                .foregroundStyle(theme.palette.onAccent)
                .frame(width: Layout.iconSize, height: Layout.iconSize)
                .background(section.tint(in: theme).linear, in: .rect(cornerRadius: Radius.small))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Text(section.title)
                    .font(.body)
                Text(section.subtitle)
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
        }
        .padding(.vertical, Spacing.xxs)
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
    .appEnvironment(.preview)
}
