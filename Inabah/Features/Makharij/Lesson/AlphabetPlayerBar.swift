import SwiftUI

/// Панель «Послушать алфавит» под навбаром (Android `AlphabetPlayerBar`, скриншоты 04, 24): что звучит
/// («Ба» или «Ба · фатхьа») и «N из M», ⏮ ⏸/▶ ⏹ ⏭. Пауза - заливка цвета раздела.
struct AlphabetPlayerBar: View {
    enum Command {
        case previous, togglePause, stop, next
    }

    let item: AlphabetItem
    let playback: AlphabetPlayback
    let colors: ReadingColors
    /// Подписи ⏮ ⏭: «Предыдущая буква» в уроке, «Предыдущий слог» в таблице.
    let previousLabel: LocalizedStringResource
    let nextLabel: LocalizedStringResource
    let onCommand: (Command) -> Void

    @Environment(\.theme) private var theme

    private enum Layout {
        static let button: CGFloat = 44
        /// ⏮ на первой букве, ⏭ на последней.
        static let disabledOpacity = 0.4
    }

    var body: some View {
        HStack(spacing: Spacing.s) {
            VStack(alignment: .leading, spacing: Spacing.xxxs) {
                Text(verbatim: item.title)
                    .font(.headline)
                    .foregroundStyle(theme.palette.onAccent)
                Text("makharij.alphabet.position \(playback.index + 1) \(playback.count)")
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            button("backward.end.fill", label: previousLabel, command: .previous)
                .disabled(!playback.hasPrevious)
                .opacity(playback.hasPrevious ? 1 : Layout.disabledOpacity)
            button(
                playback.isPaused ? "play.fill" : "pause.fill",
                label: playback.isPaused ? "makharij.alphabet.resume" : "makharij.alphabet.pause",
                command: .togglePause,
                isProminent: true
            )
            button("stop.fill", label: "makharij.alphabet.stop", command: .stop)
            button("forward.end.fill", label: nextLabel, command: .next)
                .disabled(!playback.hasNext)
                .opacity(playback.hasNext ? 1 : Layout.disabledOpacity)
        }
        .padding(.leading, Spacing.l)
        .padding(.trailing, Spacing.s)
        .padding(.vertical, Spacing.s)
        .background {
            // Плавающая поверхность поверх содержимого: тень - только у фоновой фигуры.
            let shadow = ShadowToken.floating(theme.palette)
            RoundedRectangle(cornerRadius: Radius.box)
                .fill(colors.sheet)
                .strokeBorder(colors.tileBorder, lineWidth: Size.hairline)
                .shadow(color: shadow.color, radius: shadow.radius, y: shadow.y)
        }
        .fixedTextSize()
        // Сменился элемент - VoiceOver называет его и место.
        .onChange(of: playback.index) {
            AccessibilityNotification.Announcement(
                "\(item.title), \(String(localized: "makharij.alphabet.position \(playback.index + 1) \(playback.count)"))"
            ).post()
        }
    }

    private func button(_ symbol: String, label: LocalizedStringResource, command: Command, isProminent: Bool = false) -> some View {
        Button { onCommand(command) } label: {
            Image(systemName: symbol)
                .font(.body.weight(.semibold))
                .foregroundStyle(isProminent ? colors.onTint : theme.palette.onAccent)
                .frame(width: Layout.button, height: Layout.button)
                .background {
                    RoundedRectangle(cornerRadius: Radius.control)
                        .fill(isProminent ? colors.tint : colors.tile)
                        .strokeBorder(isProminent ? colors.tint : colors.tileBorder, lineWidth: Size.hairline)
                }
                .contentShape(.rect(cornerRadius: Radius.control))
        }
        .buttonStyle(PressScaleButtonStyle(pressedScale: PressFeedback.iconScale))
        .accessibilityLabel(Text(label))
    }
}
