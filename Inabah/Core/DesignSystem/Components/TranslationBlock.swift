import SwiftUI

/// Цвета блока перевода — у каждого раздела свои (в «По умолчанию» азкары фиолетовые, хадисы зелёные;
/// в единых стилях — цвета стиля). `lead` — строки над переводом («Передал», транскрипция).
/// Таблица — как на Android (`TranslationColors`).
nonisolated struct TranslationColors: Hashable, Sendable {
    let stripe: Color
    let fill: Color
    let lead: Color
    let text: Color
    let source: Color

    static func azkar(_ palette: Palette) -> TranslationColors {
        TranslationColors(
            stripe: palette.accent,
            fill: palette.accentDim,
            lead: palette.textSecondary,
            text: palette.textPrimary,
            source: palette.zikrSource
        )
    }

    static func hadith(_ palette: Palette) -> TranslationColors {
        TranslationColors(
            stripe: palette.noteStripe,
            fill: palette.noteFill,
            lead: palette.noteText,
            text: palette.textPrimary,
            source: palette.onAccentSecondary
        )
    }
}

/// Блок перевода зикра и хадиса (решение пользователя 2026-10-10, `docs/analysis/2026-10-10-shared-translation-block.md`;
/// на Android — `TranslationBlock`): «Передал: …», транскрипция, перевод (в пузыре или просто текстом),
/// `belowText` (сноски хадиса), источник и справа от него — `sourceTrailing` (действия хадиса) на уровне
/// последней строки. Сворачивает блок не он, а экран. Язык — язык перевода (переносы); шрифты — переводов
/// (растут с «Размером переводов»).
struct TranslationBlock<Below: View, Trailing: View>: View {
    let text: String
    let language: ContentLanguage
    let colors: TranslationColors
    var narrator: String?
    var transliteration: String?
    var source: String?
    /// Перевод в пузыре (у зикра); у хадиса пузырь — только у сносок.
    var bubble = true
    /// Линия между «Передал» и текстом (у хадиса).
    var narratorDivider = false
    /// Отступ источника справа под `sourceTrailing` и сдвиг действий вправо, в поля карточки.
    var trailingInset: CGFloat = 0
    var trailingOffset: CGFloat = 0
    @ViewBuilder var belowText: Below
    @ViewBuilder var sourceTrailing: Trailing

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    /// Середина строчных букв над базовой линией — доля кегля.
    private static var xHeightRatio: CGFloat { 0.27 }
    private static var lastLineTrailing: Alignment { Alignment(horizontal: .trailing, vertical: .lastTextBaseline) }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            if let narrator {
                Text(verbatim: narrator)
                    .font(.content(.note, scale: scale, weight: .semibold))
                    .foregroundStyle(colors.lead)
                    .environment(\.locale, contentLocale)
                if narratorDivider {
                    Rectangle()
                        .fill(theme.palette.hairline)
                        .frame(height: Size.hairline)
                }
            }
            if let transliteration {
                Text(verbatim: transliteration)
                    .font(.content(.transliteration, scale: scale))
                    .foregroundStyle(colors.lead)
                    .environment(\.locale, contentLocale)
            }
            translation
            belowText
            sourceLine
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Язык перевода — только для текстов контента (переносы): подписи интерфейса внутри блока
    /// (закладка, «Сноски: N», VoiceOver) остаются на языке интерфейса (аудит iOS 1.1.1, А6).
    private var contentLocale: Locale { Locale(identifier: language.rawValue) }

    @ViewBuilder
    private var translation: some View {
        let body = Text(verbatim: text)
            .font(.content(.translation, scale: scale))
            .lineSpacing(ContentTextStyle.translationLineSpacing * scale)
            .foregroundStyle(colors.text)
            .environment(\.locale, contentLocale)
        if bubble {
            body.translationBubble(fill: colors.fill, stripe: colors.stripe)
        } else {
            body
        }
    }

    @ViewBuilder
    private var sourceLine: some View {
        if let source {
            Text(verbatim: source)
                .font(.content(.note, scale: scale))
                .foregroundStyle(colors.source)
                .environment(\.locale, contentLocale)
                // Место под действия справа: сами они — в наложении и вёрстку не раздвигают.
                .padding(.trailing, trailingInset)
                .frame(maxWidth: .infinity, alignment: .leading)
                .overlay(alignment: Self.lastLineTrailing) {
                    let iconCenterAboveBaseline = ContentTextStyle.note.baseSize * scale * Self.xHeightRatio
                    sourceTrailing
                        // Центр значков — на середине строчных букв последней строки.
                        .alignmentGuide(.lastTextBaseline) { dimensions in
                            dimensions[VerticalAlignment.center] + iconCenterAboveBaseline
                        }
                        .offset(x: trailingOffset)
                }
        } else {
            sourceTrailing
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }
}

extension TranslationBlock where Below == EmptyView, Trailing == EmptyView {
    init(
        text: String,
        language: ContentLanguage,
        colors: TranslationColors,
        narrator: String? = nil,
        transliteration: String? = nil,
        source: String? = nil,
        bubble: Bool = true,
        narratorDivider: Bool = false
    ) {
        self.init(
            text: text, language: language, colors: colors,
            narrator: narrator, transliteration: transliteration, source: source,
            bubble: bubble, narratorDivider: narratorDivider,
            belowText: { EmptyView() }, sourceTrailing: { EmptyView() }
        )
    }
}
