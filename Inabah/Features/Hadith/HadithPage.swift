import SwiftUI

/// Одна страница экрана хадиса: арабский текст на пергаменте, отметки, аудио, перевод.
/// Статус приходит значением — отметка другого хадиса эту страницу не перестраивает.
struct HadithPage: View {
    let hadith: Hadith
    let status: HadithStatus
    /// Страница на экране; ушли с неё — раскрытые сноски сворачиваются (как на Android).
    var isCurrent = true

    @Environment(ReadingSettings.self) private var settings
    @Environment(\.theme) private var theme

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.m) {
                arabicPanel
                HadithStatusButtons(id: hadith.id, status: status)
                AudioSoonPlaceholder(title: Text("hadith.audio.title \(hadith.number)"))
                if let translation = hadith.translation {
                    HadithTranslationCard(hadith: hadith, translation: translation, isCurrent: isCurrent)
                } else {
                    // Перевода нет — закладка и «Поделиться» справа внизу страницы.
                    HadithCardActions(hadith: hadith)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                }
            }
            .padding(Spacing.m)
        }
        .scrollIndicators(.hidden)
    }

    private var arabicPanel: some View {
        ParchmentPanel(bottomCornerRadius: Radius.card) {
            VStack(alignment: .leading, spacing: Spacing.m) {
                Text("hadith.detail.number \(hadith.number)")
                    .font(.caption2.weight(.bold))
                    .textCase(.uppercase)
                    .tracking(Tracking.label)
                    .foregroundStyle(theme.palette.parchmentText)
                ArabicText(
                    text: hadith.arabicDisplayText,
                    size: settings.arabicFontSize,
                    color: theme.palette.parchmentText
                )
            }
        }
    }
}

/// «◎ Прочитать / ✓ Прочитан» и «☆ Выучить / ★ Выучен». Статус приходит значением.
private struct HadithStatusButtons: View {
    let id: HadithID
    let status: HadithStatus

    @Environment(HadithProgress.self) private var progress
    @Environment(\.theme) private var theme

    private static let glyphSize: CGFloat = 26

    var body: some View {
        HStack(spacing: Spacing.s) {
            Button {
                progress.toggleRead(id)
            } label: {
                Label {
                    Text(status.isRead ? "hadith.status.read" : "hadith.status.markRead")
                } icon: {
                    StatusGlyph(.read, isFilled: status.isRead, size: Self.glyphSize, relativeTo: .title3)
                }
            }
            .buttonStyle(ToggleTileButtonStyle(
                isOn: status.isRead,
                tint: theme.palette.statusRead,
                fill: theme.palette.statusReadTint,
                activeFill: theme.palette.statusReadStrong
            ))
            // Переключатель, как системная кнопка-переключатель UIKit: подпись постоянная,
            // состояние — признак «выбран» («вкл/выкл» читает VoiceOver).
            .accessibilityLabel(Text("hadith.status.read"))
            .accessibilityAddTraits(status.isRead ? [.isToggle, .isSelected] : .isToggle)

            Button {
                progress.toggleMemorized(id)
            } label: {
                Label {
                    Text(status.isMemorized ? "hadith.status.memorized" : "hadith.status.memorize")
                } icon: {
                    StatusGlyph(.memorized, isFilled: status.isMemorized, size: Self.glyphSize, relativeTo: .title3)
                }
            }
            .buttonStyle(ToggleTileButtonStyle(
                isOn: status.isMemorized,
                tint: theme.palette.statusMemorized,
                fill: theme.palette.statusMemorizedTint,
                activeFill: theme.palette.statusMemorizedStrong
            ))
            .accessibilityLabel(Text("hadith.status.memorized"))
            .accessibilityAddTraits(status.isMemorized ? [.isToggle, .isSelected] : .isToggle)
        }
        .sensoryFeedback(.selection, trigger: status)
    }
}

/// «Передал: …», перевод, сноски и «Приводится: …»; закладка и «Поделиться» — справа на уровне последней
/// строки источника (решение пользователя 2026-10-06), без источника — справа внизу карточки.
private struct HadithTranslationCard: View {
    let hadith: Hadith
    let translation: HadithTranslation
    let isCurrent: Bool

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    @State private var isNotesExpanded = false

    var body: some View {
        // Размеры — общие с переводом зикра (`ContentTextStyle`), шаг — из настройки «Размер текста».
        VStack(alignment: .leading, spacing: Spacing.s) {
            if let narrator = translation.narrator {
                Text("hadith.detail.narrator \(narrator)")
                    .font(.content(.note, scale: scale, weight: .semibold))
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
            Text(verbatim: translation.text)
                .font(.content(.translation, scale: scale))
                .lineSpacing(ContentTextStyle.translationLineSpacing * scale)
                .foregroundStyle(theme.palette.onAccent)
                // Язык перевода — только для его текста (переносы); подписи «Передал:» /
                // «Приводится:» — на языке интерфейса.
                .environment(\.locale, Locale(identifier: translation.language.rawValue))
            if !translation.notes.isEmpty {
                HadithNotes(
                    notes: translation.notes,
                    translation: translation.text,
                    language: translation.language,
                    isExpanded: $isNotesExpanded
                )
                .onChange(of: isCurrent) { _, isCurrent in
                    if !isCurrent { isNotesExpanded = false }
                }
            }
            if let source = translation.source {
                Text("hadith.detail.source \(source)")
                    .font(.content(.note, scale: scale).italic())
                    .foregroundStyle(theme.palette.onAccentTertiary)
                    // Место под значки справа: сами значки — в наложении и вёрстку не раздвигают.
                    .padding(.trailing, HadithCardActions.textInset(scale: scale))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: Self.lastLineTrailing) {
                        let iconCenterAboveBaseline = ContentTextStyle.note.baseSize * scale * Self.xHeightRatio
                        HadithCardActions(hadith: hadith)
                            // Центр значка — на середине строчных букв последней строки.
                            .alignmentGuide(.lastTextBaseline) { dimensions in
                                dimensions[VerticalAlignment.center] + iconCenterAboveBaseline
                            }
                            // Правый значок — вровень с правым краем текста; зона нажатия выходит в поля карточки.
                            .offset(x: HadithCardActions.iconInset(scale: scale))
                    }
                    .padding(.top, Spacing.s)
                    .overlay(alignment: .top) {
                        Rectangle()
                            .fill(theme.palette.hairline)
                            .frame(height: Size.hairline)
                    }
            } else {
                HadithCardActions(hadith: hadith)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, Spacing.l)
        .padding(.horizontal, Spacing.xl)
        .surface(theme.palette.subtleFill, cornerRadius: Radius.box)
    }

    private static let lastLineTrailing = Alignment(horizontal: .trailing, vertical: .lastTextBaseline)
    /// Половина высоты строчных букв относительно кегля.
    private static let xHeightRatio: CGFloat = 0.27
}

/// Закладка «В избранном» и «Поделиться» хадисом (как на Android: закладка слева): значки без
/// подложки, растут вместе с переводом (шаг «Размер текста»), а не по системному размеру.
/// Избранное читает только этот ряд — закладка не перестраивает страницу.
private struct HadithCardActions: View {
    let hadith: Hadith

    @Environment(HadithFavorites.self) private var favorites
    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    static func iconSize(scale: Double) -> CGFloat {
        baseIconSize * scale
    }

    /// Поле от правого значка до края его зоны нажатия — на столько ряд сдвигается вправо.
    static func iconInset(scale: Double) -> CGFloat {
        (Size.minTapTarget - iconSize(scale: scale)) / 2
    }

    /// Отступ текста источника справа: место под два значка, видимые части которых стоят в тексте.
    static func textInset(scale: Double) -> CGFloat {
        Size.minTapTarget * 2 - iconInset(scale: scale) + Spacing.s
    }

    private static let baseIconSize: CGFloat = 19

    var body: some View {
        let isFavorite = favorites.isFavorite(hadith.id)
        HStack(spacing: 0) {
            Button {
                favorites.toggle(hadith.id)
            } label: {
                Image(systemName: isFavorite ? "bookmark.fill" : "bookmark")
                    .font(.system(size: Self.iconSize(scale: scale)))
            }
            .buttonStyle(BareIconButtonStyle(foreground: isFavorite ? theme.palette.gold : theme.palette.onAccentSecondary))
            .accessibilityLabel(Text("hadith.favorite"))
            .accessibilityAddTraits(isFavorite ? [.isToggle, .isSelected] : .isToggle)
            .sensoryFeedback(.selection, trigger: isFavorite)

            ShareButton(iconSize: Self.iconSize(scale: scale)) { hadith.shareText }
                .buttonStyle(BareIconButtonStyle(foreground: theme.palette.onAccentSecondary))
        }
    }
}
