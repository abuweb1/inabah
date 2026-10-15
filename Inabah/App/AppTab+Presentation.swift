import SwiftUI

extension AppTab {
    /// Порядок вкладок в панели.
    static let ordered: [AppTab] = [.azkar, .hadith, .makharij, .settings]

    var titleKey: LocalizedStringKey {
        switch self {
        case .azkar: "tab.azkar"
        case .hadith: "tab.hadith"
        case .makharij: "tab.makharij"
        case .settings: "tab.settings"
        }
    }

    /// Значки — общие с Android (`Assets.xcassets/TabIcons`).
    var iconName: String {
        switch self {
        case .azkar: "tabIconAzkar"
        case .hadith: "tabIconHadith"
        case .makharij: "tabIconMakharij"
        case .settings: "tabIconSettings"
        }
    }

    /// Подсветка выбранной вкладки — в тон фона раздела.
    func tint(in theme: Theme) -> Color {
        switch self {
        case .azkar: theme.palette.tabAzkar
        case .hadith: theme.palette.tabHadith
        case .makharij: theme.palette.tabMakharij
        case .settings: theme.palette.tabSettings
        }
    }
}
