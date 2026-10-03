import SwiftUI

/// Нажатие слегка уменьшает элемент — для крупных карточек и кнопок без собственного фона.
struct PressScaleButtonStyle: ButtonStyle {
    var pressedScale: CGFloat = 0.97

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? pressedScale : 1)
            .animation(Motion.cardPress, value: configuration.isPressed)
    }
}

/// Иконка-кнопка с подложкой и рамкой: круглая (действия карточки) или скруглённый квадрат (плеер).
/// Неактивная — приглушена.
struct IconButtonStyle: ButtonStyle {
    enum Shape {
        case circle
        case roundedSquare
    }

    var shape: Shape = .circle
    var size: CGFloat = Size.minTapTarget
    var foreground: Color
    var background: Color
    var border: Color

    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        let radius = shape == .circle ? size / 2 : Radius.control
        configuration.label
            .font(.system(size: size * 0.36, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .surface(background, cornerRadius: radius, border: border)
            .contentShape(.rect(cornerRadius: radius))
            .opacity(isEnabled ? 1 : 0.4)
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(Motion.press, value: configuration.isPressed)
    }
}

/// Иконка без подложки (перемотка ±10 с в плеере).
struct BareIconButtonStyle: ButtonStyle {
    var foreground: Color

    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title2.weight(.medium))
            .foregroundStyle(foreground)
            .frame(width: Size.minTapTarget, height: Size.minTapTarget)
            .contentShape(.circle)
            .opacity(isEnabled ? (configuration.isPressed ? 0.5 : 1) : 0.4)
    }
}

/// Крупная круглая золотая кнопка (плей/пауза плеера).
struct ProminentRoundButtonStyle: ButtonStyle {
    @Environment(\.theme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title.weight(.bold))
            .foregroundStyle(theme.palette.parchmentInk)
            .frame(width: Size.playerMainButton, height: Size.playerMainButton)
            .surface(
                theme.gradients.counterButton.linear,
                cornerRadius: Size.playerMainButton / 2,
                shadow: .goldButton(theme.palette)
            )
            .scaleEffect(configuration.isPressed ? 0.93 : 1)
            .animation(Motion.press, value: configuration.isPressed)
    }
}

/// Основная кнопка действия на всю ширину («На главную», «Слушать»).
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.theme) private var theme
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .foregroundStyle(theme.palette.onAccent)
            .frame(maxWidth: .infinity, minHeight: Size.primaryButtonHeight)
            .padding(.horizontal, Spacing.xl)
            .surface(theme.palette.accent, cornerRadius: Radius.box)
            .opacity(isEnabled ? (configuration.isPressed ? 0.8 : 1) : 0.4)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(Motion.press, value: configuration.isPressed)
    }
}
