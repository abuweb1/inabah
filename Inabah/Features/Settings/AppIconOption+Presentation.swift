import SwiftUI

/// Подписи и превью вариантов иконки. Превью — отдельные картинки (`AppIconPreviews`):
/// набор иконки приложения нельзя загрузить как изображение.
extension AppIconOption {
    var title: LocalizedStringResource {
        switch self {
        case .classic: "appIcon.classic"
        case .niche: "appIcon.niche"
        case .beads: "appIcon.beads"
        case .dawn: "appIcon.dawn"
        }
    }

    var preview: ImageResource {
        switch self {
        case .classic: .iconPreviewClassic
        case .niche: .iconPreviewNiche
        case .beads: .iconPreviewBeads
        case .dawn: .iconPreviewDawn
        }
    }
}
