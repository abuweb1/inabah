import SwiftUI

/// Текст переводов — единый для азкаров и хадисов. Размер = база × шаг из настроек
/// (`ContentTextSize`); системный размер текста не влияет.
nonisolated enum ContentTextStyle: Sendable {
    /// Перевод зикра и хадиса.
    case translation
    /// Транскрипция зикра (курсив).
    case transliteration
    /// Источник и передатчик.
    case note

    /// Базовый размер в pt (при шаге «Обычный»).
    var baseSize: Double {
        switch self {
        case .translation: 17
        case .transliteration: 15
        case .note: 13
        }
    }

    /// Дополнительный межстрочный интервал перевода — растёт вместе с текстом.
    static let translationLineSpacing: Double = 4
}

extension EnvironmentValues {
    /// Множитель размера переводов (`ContentTextSize.scale`).
    @Entry var contentTextScale: Double = 1
}

nonisolated extension Font {
    /// Шрифт переводов: фиксированный размер, на него не влияет системный размер текста.
    static func content(_ style: ContentTextStyle, scale: Double, weight: Font.Weight = .regular) -> Font {
        let font = Font.system(size: style.baseSize * scale, weight: weight)
        return style == .transliteration ? font.italic() : font
    }
}

extension View {
    /// Закрепить стандартный размер текста: заголовки навбара и кнопки не меняются ни от
    /// настройки «Размер интерфейса», ни от системного размера текста.
    func fixedTextSize() -> some View {
        dynamicTypeSize(.large)
    }
}
