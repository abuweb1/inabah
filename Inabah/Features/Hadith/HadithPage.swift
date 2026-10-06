import SwiftUI

/// Одна страница экрана хадиса: арабский текст на пергаменте, отметки, аудио, перевод.
/// Статус приходит значением — отметка другого хадиса эту страницу не перестраивает.
struct HadithPage: View {
    let hadith: Hadith
    let status: HadithStatus

    @Environment(ReadingSettings.self) private var settings
    @Environment(\.theme) private var theme

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.m) {
                arabicPanel
                HadithStatusButtons(id: hadith.id, status: status)
                HadithAudioPlaceholder(number: hadith.number)
                if let translation = hadith.translation {
                    HadithTranslationCard(hadith: hadith, translation: translation)
                } else {
                    // Перевода нет — «Поделиться» справа внизу страницы.
                    HadithShareButton(hadith: hadith)
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
                    .foregroundStyle(theme.palette.parchmentInk)
                ArabicText(
                    text: hadith.arabicDisplayText,
                    size: settings.arabicFontSize,
                    color: theme.palette.parchmentInk
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

/// Аудио хадисов ещё не записано — неактивная карточка, как в прототипе.
private struct HadithAudioPlaceholder: View {
    let number: Int

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: Spacing.m) {
            Image(systemName: "play.fill")
                .font(.headline)
                .foregroundStyle(theme.palette.onAccentTertiary)
                .frame(width: Size.minTapTarget, height: Size.minTapTarget)
                .background(theme.palette.track, in: .circle)
                // Неактивный значок — не кнопка «Воспроизвести»: VoiceOver читает только подписи.
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Text("hadith.audio.title \(number)")
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
                Text("hadith.audio.soon")
                    .font(.caption2)
                    .foregroundStyle(theme.palette.onAccentTertiary)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, Spacing.m)
        .padding(.horizontal, Spacing.l)
        .surface(theme.palette.subtleFill, cornerRadius: Radius.box)
        .accessibilityElement(children: .combine)
    }
}

/// «Передал: …», перевод и «Приводится: …»; «Поделиться» — справа на уровне последней
/// строки источника (решение пользователя 2026-10-06), без источника — справа внизу карточки.
private struct HadithTranslationCard: View {
    let hadith: Hadith
    let translation: HadithTranslation

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

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
            if let source = translation.source {
                Text("hadith.detail.source \(source)")
                    .font(.content(.note, scale: scale).italic())
                    .foregroundStyle(theme.palette.onAccentTertiary)
                    // Место под значок справа: сам значок — в наложении и вёрстку не раздвигает.
                    .padding(.trailing, HadithShareButton.iconSize(scale: scale) + Spacing.s)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .overlay(alignment: Self.lastLineTrailing) {
                        let iconCenterAboveBaseline = ContentTextStyle.note.baseSize * scale * Self.xHeightRatio
                        let iconInset = (Size.minTapTarget - HadithShareButton.iconSize(scale: scale)) / 2
                        HadithShareButton(hadith: hadith)
                            // Центр значка — на середине строчных букв последней строки.
                            .alignmentGuide(.lastTextBaseline) { dimensions in
                                dimensions[VerticalAlignment.center] + iconCenterAboveBaseline
                            }
                            // Значок — вровень с правым краем текста; зона нажатия выходит в поля карточки.
                            .offset(x: iconInset)
                    }
                    .padding(.top, Spacing.s)
                    .overlay(alignment: .top) {
                        Rectangle()
                            .fill(theme.palette.hairline)
                            .frame(height: Size.hairline)
                    }
            } else {
                HadithShareButton(hadith: hadith)
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

/// «Поделиться» хадисом: значок без подложки цвета подписей; растёт вместе с переводом
/// (шаг «Размер текста»), а не по системному размеру.
private struct HadithShareButton: View {
    let hadith: Hadith

    @Environment(\.theme) private var theme
    @Environment(\.contentTextScale) private var scale

    static func iconSize(scale: Double) -> CGFloat {
        baseIconSize * scale
    }

    private static let baseIconSize: CGFloat = 19

    var body: some View {
        ShareButton(iconSize: Self.iconSize(scale: scale)) { hadith.shareText }
            .buttonStyle(BareIconButtonStyle(foreground: theme.palette.onAccentSecondary))
    }
}
