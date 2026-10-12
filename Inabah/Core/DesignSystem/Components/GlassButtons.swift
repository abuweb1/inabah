import SwiftUI

/// Свои стеклянные кнопки — копия Android `GlassCapsuleButton` / `GlassIconButton` (`TopBar.kt`),
/// которые повторяют нажатие стекла iOS 26 («‹» в шапке): подаётся вперёд, заливается цветом раздела,
/// обводка разгорается, содержимое перекрашивается. Системное `.glass` не используется (решение
/// пользователя 2026-10-11). Цвета — `GlassColors` от цвета раздела `tint`.
private enum GlassMetrics {
    /// Высота капсулы и круг кнопки — как на Android (зона касания — `Size.minTapTarget`).
    static let height: CGFloat = 40
    static let capsulePressScale: CGFloat = 1.16
    static let circlePressScale: CGFloat = 1.38
}

/// Стекло в покое и при нажатии: подложка, заливка нажатия, обводка и перекраска содержимого.
private struct GlassBody<S: InsettableShape>: ViewModifier {
    let colors: GlassColors
    let shape: S
    let isPressed: Bool
    let pressedScale: CGFloat

    func body(content: Content) -> some View {
        content
            // Нажатое содержимое (текст и значки в своих цветах) — одним цветом поверх, только
            // по самому содержимому: отдельный слой, чтобы не окрасить подложку.
            .overlay {
                Rectangle()
                    .fill(colors.pressedIcon.color)
                    .opacity(isPressed ? 1 : 0)
                    .blendMode(.sourceAtop)
                    .allowsHitTesting(false)
            }
            .compositingGroup()
            .background {
                ZStack {
                    shape.fill(colors.fill.color)
                    shape.fill(colors.pressedFill.color).opacity(isPressed ? 1 : 0)
                    shape.strokeBorder(isPressed ? colors.pressedRim.color : colors.rim.color, lineWidth: Size.hairline)
                }
            }
            .scaleEffect(isPressed ? pressedScale : 1)
            .animation(isPressed ? Motion.glassPress : Motion.glassRelease, value: isPressed)
    }
}

/// Стеклянная капсула с содержимым в ряд (серия дней на главной «Азкаров»): высота от 40, поля по бокам 14.
struct GlassCapsuleButtonStyle: ButtonStyle {
    let tint: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, Spacing.l)
            .frame(minHeight: GlassMetrics.height)
            .modifier(GlassBody(
                colors: GlassColors(tint: SRGBColor(tint)),
                shape: Capsule(),
                isPressed: configuration.isPressed,
                pressedScale: GlassMetrics.capsulePressScale
            ))
            .frame(minHeight: Size.minTapTarget)
            .contentShape(.rect)
    }
}

/// Стеклянный круг 40 со значком (‹ › месяцев календаря). Неактивный — приглушён.
struct GlassIconButtonStyle: ButtonStyle {
    let tint: Color

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.theme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(theme.palette.onAccent)
            .frame(width: GlassMetrics.height, height: GlassMetrics.height)
            .modifier(GlassBody(
                colors: GlassColors(tint: SRGBColor(tint)),
                shape: Circle(),
                isPressed: configuration.isPressed,
                pressedScale: GlassMetrics.circlePressScale
            ))
            .frame(width: Size.minTapTarget, height: Size.minTapTarget)
            .contentShape(.rect)
            .opacity(isEnabled ? 1 : PressFeedback.disabledOpacity)
    }
}
