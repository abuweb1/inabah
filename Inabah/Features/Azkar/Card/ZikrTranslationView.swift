import SwiftUI

/// Транслитерация, передатчик (если есть — как в данных, без подписи) под линией, перевод и источник зикра
/// (раскрывается кнопкой «Аа»).
/// Общий блок перевода в цветах азкаров (`TranslationBlock`, как на Android); шаг — из «Размера текста».
struct ZikrTranslationView: View {
    let translation: ZikrTranslation

    @Environment(\.theme) private var theme

    var body: some View {
        TranslationBlock(
            text: translation.text,
            language: translation.language,
            colors: .azkar(theme.palette),
            narrator: translation.narrator,
            transliteration: translation.transliteration,
            source: translation.source
        )
    }
}
