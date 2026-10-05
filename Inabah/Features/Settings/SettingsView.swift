import SwiftUI

/// Корень настроек: список разделов. У корня своя тема (графит), у разделов — фон своего раздела.
struct SettingsView: View {
    @Environment(\.theme) private var theme

    var body: some View {
        List {
            Section {
                rows(SettingsSection.content)
            }
            Section {
                rows(SettingsSection.appearance)
            } header: {
                Text("settings.appearance.header")
                    .interfaceTextSize()
            }
        }
        .settingsForm(background: theme.gradients.settingsBackground)
        .navigationTitle(Text("settings.title"))
        .audioPlayerInset()
    }

    private func rows(_ sections: [SettingsSection]) -> some View {
        ForEach(sections, id: \.self) { section in
            NavigationLink(value: section.route) {
                // Шаг интерфейса — у строк, а не у списка или экрана: крупный заголовок
                // «Настройки» UIKit масштабирует по размеру списка, а заголовки не меняются.
                SettingsSectionRow(section: section)
                    .interfaceTextSize()
            }
            .settingsRow()
        }
    }
}

/// Разделы в корне настроек (маршрутов в настройках больше — у разделов есть свои подэкраны).
private enum SettingsSection {
    case azkar
    case hadith
    case palette
    case textSize
    case appIcon

    /// Настройки разделов приложения.
    static let content: [SettingsSection] = [.azkar, .hadith]
    /// Оформление.
    static let appearance: [SettingsSection] = [.palette, .textSize, .appIcon]

    var route: SettingsRoute {
        switch self {
        case .azkar: .azkar
        case .hadith: .hadith
        case .palette: .palette
        case .textSize: .textSize
        case .appIcon: .appIcon
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .azkar: "settings.azkar.title"
        case .hadith: "settings.hadith.title"
        case .palette: "settings.palette.title"
        case .textSize: "settings.textSize.title"
        case .appIcon: "settings.appIcon.title"
        }
    }

    var subtitle: LocalizedStringResource {
        switch self {
        case .azkar: "settings.azkar.subtitle"
        case .hadith: "settings.hadith.subtitle"
        case .palette: "settings.palette.subtitle"
        case .textSize: "settings.textSize.subtitle"
        case .appIcon: "settings.appIcon.subtitle"
        }
    }

    /// Значок строки. У разделов приложения — те же значки, что на их вкладках (общие с Android,
    /// `Assets.xcassets/TabIcons`), у оформления — SF Symbols.
    var icon: SettingsSectionIcon {
        switch self {
        case .azkar: .asset("tabIconAzkar")
        case .hadith: .asset("tabIconHadith")
        case .palette: .symbol("paintpalette.fill")
        case .textSize: .symbol("textformat.size")
        case .appIcon: .symbol("app.badge.fill")
        }
    }

    /// Плашка значка — в цвет раздела.
    func tint(in theme: Theme) -> ThemeGradient {
        switch self {
        case .azkar: theme.gradients.morningCard
        case .hadith: theme.gradients.nawawiCard
        case .palette: theme.gradients.qudsiCard
        case .textSize: theme.gradients.ajurriCard
        case .appIcon: theme.gradients.eveningCard
        }
    }
}

/// Строка раздела: значок в цвет раздела, название и краткое описание.
private struct SettingsSectionRow: View {
    let section: SettingsSection

    @Environment(\.theme) private var theme

    /// Плашка значка растёт вместе с текстом строки (размер интерфейса).
    @ScaledMetric(relativeTo: .body) private var iconSize: CGFloat = 32
    /// Векторный значок из ассетов — по размеру глифа SF Symbol `title3` в той же плашке.
    @ScaledMetric(relativeTo: .body) private var assetGlyphSize: CGFloat = 22

    var body: some View {
        HStack(spacing: Spacing.m) {
            glyph
                .foregroundStyle(theme.palette.onAccent)
                .frame(width: iconSize, height: iconSize)
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

    @ViewBuilder
    private var glyph: some View {
        switch section.icon {
        case .symbol(let name):
            Image(systemName: name)
                .font(.title3)
        case .asset(let name):
            Image(name)
                .resizable()
                .scaledToFit()
                .frame(width: assetGlyphSize, height: assetGlyphSize)
        }
    }
}

/// Значок строки настроек: системный символ или векторный значок из ассетов.
private enum SettingsSectionIcon {
    case symbol(String)
    case asset(String)
}

#Preview {
    NavigationStack {
        SettingsView()
    }
    .appEnvironment(.preview)
}
