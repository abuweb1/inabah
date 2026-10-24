import CoreText
import SwiftUI

/// Шрифты приложения.
///
/// Арабский текст — Scheherazade New (SIL OFL, `Resources/Fonts`): классический насх с полной
/// огласовкой, как в прототипе. Суры и аяты Корана в азкарах — шрифт Мединского мусхафа
/// KFGQPC Uthmanic Script HAFS (2026-10-25; файл — без изменений, так требует лицензия).
/// Заголовки — системный засечный (New York), остальной UI — SF.
nonisolated enum AppFont {
    static let arabicRegular = "ScheherazadeNew-Regular"
    static let arabicBold = "ScheherazadeNew-Bold"
    static let mushaf = "KFGQPCUthmanicScriptHAFS"
    /// Hafs при том же кегле мельче Scheherazade: ×1,2 — буквы той же высоты (как `MUSHAF_SCALE` на Android).
    static let mushafScale = 1.2

    /// Регистрирует все `.ttf` и `.otf` из Bundle для процесса. Безопасно вызывать многократно и из
    /// любого потока: регистрация выполняется один раз (ленивая инициализация `static let`).
    /// Вызывается при старте приложения и при первом обращении к `Font.arabic` — для превью.
    static func registerBundledFonts() {
        _ = registration
    }

    private static let registration: Void = {
        let urls = ["ttf", "otf"].flatMap { Bundle.main.urls(forResourcesWithExtension: $0, subdirectory: nil) ?? [] }
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

    /// Текст Корана шрифтом мусхафа; кегль — уже увеличенный (`AppFont.mushafScale`). Жирного нет.
    static func mushaf(size: CGFloat) -> Font {
        AppFont.registerBundledFonts()
        return .custom(AppFont.mushaf, fixedSize: size)
    }
}
