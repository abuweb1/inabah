import SwiftUI

/// Нажатие слегка уменьшает элемент — для крупных карточек и кнопок без собственного фона.
struct PressScaleButtonStyle: ButtonStyle {
    var pressedScale = PressFeedback.cardScale

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? pressedScale : 1)
            .animation(Motion.cardPress, value: configuration.isPressed)
    }
}

/// Плитка-переключатель «значок над подписью» (отметки хадиса): выключенная — цветной текст
/// на лёгкой подложке, включённая — белый текст на плотной подложке.
struct ToggleTileButtonStyle: ButtonStyle {
    let isOn: Bool
    let tint: Color
    let fill: Color
    let activeFill: Color

    @Environment(\.theme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .labelStyle(.verticalTile)
            .foregroundStyle(isOn ? theme.palette.onAccent : tint)
            .frame(maxWidth: .infinity, minHeight: Size.primaryButtonHeight)
            .padding(.vertical, Spacing.m)
            .surface(isOn ? activeFill : fill, cornerRadius: Radius.control)
            .scaleEffect(configuration.isPressed ? PressFeedback.cardScale : 1)
            .animation(Motion.press, value: configuration.isPressed)
            .animation(Motion.highlight, value: isOn)
            .fixedTextSize()
    }
}

/// Значок над подписью.
struct VerticalTileLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        VStack(spacing: Spacing.xxs) {
            configuration.icon
                .font(.title3)
            configuration.title
                .font(.footnote.weight(.semibold))
        }
    }
}

extension LabelStyle where Self == VerticalTileLabelStyle {
    static var verticalTile: VerticalTileLabelStyle { VerticalTileLabelStyle() }
}

/// Строки списков: нажатая строка приглушается (`opacity .65` в прототипе).
struct PressDimButtonStyle: ButtonStyle {
    var pressedOpacity = PressFeedback.rowOpacity

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? pressedOpacity : 1)
            .animation(Motion.press, value: configuration.isPressed)
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

    /// Размер значка — доля размера кнопки.
    private static let glyphRatio: CGFloat = 0.36

    func makeBody(configuration: Configuration) -> some View {
        let radius = shape == .circle ? size / 2 : Radius.control
        configuration.label
            .font(.system(size: size * Self.glyphRatio, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(width: size, height: size)
            .surface(background, cornerRadius: radius, border: border)
            .contentShape(.rect(cornerRadius: radius))
            .opacity(isEnabled ? 1 : PressFeedback.disabledOpacity)
            .scaleEffect(configuration.isPressed ? PressFeedback.iconScale : 1)
            .animation(Motion.press, value: configuration.isPressed)
            .fixedTextSize()
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
            .opacity(isEnabled ? (configuration.isPressed ? PressFeedback.bareOpacity : 1) : PressFeedback.disabledOpacity)
            .fixedTextSize()
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
            .scaleEffect(configuration.isPressed ? PressFeedback.roundScale : 1)
            .animation(Motion.press, value: configuration.isPressed)
            .fixedTextSize()
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
            .opacity(isEnabled ? (configuration.isPressed ? PressFeedback.wideOpacity : 1) : PressFeedback.disabledOpacity)
            .scaleEffect(configuration.isPressed ? PressFeedback.wideScale : 1)
            .animation(Motion.press, value: configuration.isPressed)
            .fixedTextSize()
    }
}
