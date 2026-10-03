import CoreText
import SwiftUI

/// Шрифты приложения.
///
/// Арабский текст — Scheherazade New (SIL OFL, `Resources/Fonts`): классический насх с полной
/// огласовкой, как в прототипе. Заголовки — системный засечный (New York), остальной UI — SF.
nonisolated enum AppFont {
    static let arabicRegular = "ScheherazadeNew-Regular"
    static let arabicBold = "ScheherazadeNew-Bold"

    /// Регистрирует все `.ttf` из Bundle для процесса. Безопасно вызывать многократно и из
    /// любого потока: регистрация выполняется один раз (ленивая инициализация `static let`).
    /// Вызывается при старте приложения и при первом обращении к `Font.arabic` — для превью.
    static func registerBundledFonts() {
        _ = registration
    }

    private static let registration: Void = {
        let urls = Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? []
        guard !urls.isEmpty else { return }
        CTFontManagerRegisterFontURLs(urls as CFArray, .process, true, nil)
    }()
}

nonisolated extension Font {
    /// Арабский текст фиксированного кегля (кегль задаёт пользователь кнопками А−/А+).
    static func arabic(size: CGFloat, bold: Bool = false) -> Font {
        AppFont.registerBundledFonts()
        return .custom(bold ? AppFont.arabicBold : AppFont.arabicRegular, fixedSize: size)
    }
}
