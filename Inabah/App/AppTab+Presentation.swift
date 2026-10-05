import SwiftUI

extension AppTab {
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
