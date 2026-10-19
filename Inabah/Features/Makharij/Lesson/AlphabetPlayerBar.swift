import SwiftUI

/// Панель «Послушать алфавит» под навбаром урока (Android `AlphabetPlayerBar`, скриншот 04): название
/// звучащей буквы и «N из 29», ⏮ ⏸/▶ ⏹ ⏭. Пауза - заливка цвета раздела.
struct AlphabetPlayerBar: View {
    enum Command {
        case previous, togglePause, stop, next
    }

    let letter: ReadingLetter
    let playback: AlphabetPlayback
    let colors: ReadingColors
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
                Text(verbatim: letter.name)
                    .font(.headline)
                    .foregroundStyle(theme.palette.onAccent)
                Text("makharij.alphabet.position \(playback.index + 1) \(playback.count)")
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityElement(children: .combine)
            button("backward.end.fill", label: "makharij.alphabet.previous", command: .previous)
                .disabled(!playback.hasPrevious)
                .opacity(playback.hasPrevious ? 1 : Layout.disabledOpacity)
            button(
                playback.isPaused ? "play.fill" : "pause.fill",
                label: playback.isPaused ? "makharij.alphabet.resume" : "makharij.alphabet.pause",
                command: .togglePause,
                isProminent: true
            )
            button("stop.fill", label: "makharij.alphabet.stop", command: .stop)
            button("forward.end.fill", label: "makharij.alphabet.next", command: .next)
                .disabled(!playback.hasNext)
                .opacity(playback.hasNext ? 1 : Layout.disabledOpacity)
        }
        .padding(.leading, Spacing.l)
        .padding(.trailing, Spacing.s)
        .padding(.vertical, Spacing.s)
        .background {
            RoundedRectangle(cornerRadius: Radius.box)
                .fill(colors.sheet)
                .strokeBorder(colors.tileBorder, lineWidth: Size.hairline)
        }
        .fixedTextSize()
        // Сменилась буква - VoiceOver называет её и место.
        .onChange(of: playback.index) {
            AccessibilityNotification.Announcement(
                "\(letter.name), \(String(localized: "makharij.alphabet.position \(playback.index + 1) \(playback.count)"))"
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
