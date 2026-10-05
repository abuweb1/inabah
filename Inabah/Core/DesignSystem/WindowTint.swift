import SwiftUI
import UIKit

/// Tint окон приложения. Системные диалоги и алерты (`confirmationDialog`, `.alert`) рисует UIKit:
/// они берут tint окна, а не `.tint` SwiftUI, — без этого в любой палитре оставались бы
/// цвета `AccentColor`.
enum WindowTint {
    static func apply(_ color: Color) {
        let tint = UIColor(color)
        for scene in UIApplication.shared.connectedScenes {
            guard let windowScene = scene as? UIWindowScene else { continue }
            for window in windowScene.windows where window.tintColor != tint {
                window.tintColor = tint
            }
        }
    }
}
