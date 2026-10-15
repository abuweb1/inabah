import SwiftUI

/// Кнопки «А− / А+» размера арабского шрифта — для тулбаров экранов чтения.
/// Кегль меняется анимацией: текст и высота карточек перетекают плавно, без скачка ленты.
///
/// `ChromeStyle.custom` — одна стеклянная капсула, как стекло iOS 26 и Android (`GlassFontSizeCapsule`).
/// `.system`: `compact` — одна капсула с кнопками своей ширины вместо группы системных элементов
/// (у каждого — поле около 44 pt): рядом с ещё одной кнопкой справа на узких iPhone (390 pt)
/// группа не помещалась и уходила в меню «…».
struct FontSizeControls: ToolbarContent {
    let settings: ReadingSettings
    var compact = false
    /// Цвет раздела и шапки — для своего стекла.
    let tint: Color
    let header: Color

    @Environment(\.chromeStyle) private var chromeStyle

    private static let compactButtonWidth: CGFloat = 38

    var body: some ToolbarContent {
        if chromeStyle == .custom {
            ToolbarItem(placement: .topBarTrailing) {
                GlassFontSizeCapsule(settings: settings, tint: tint, header: header)
            }
        } else if compact {
            ToolbarItem(placement: .topBarTrailing) {
                HStack(spacing: 0) {
                    decrease.frame(width: Self.compactButtonWidth, height: Size.minTapTarget)
                    increase.frame(width: Self.compactButtonWidth, height: Size.minTapTarget)
                }
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                decrease
                increase
            }
        }
    }

    private var decrease: some View {
        Button {
            withAnimation(Motion.fontSize) { settings.decreaseArabicFontSize() }
        } label: {
            Text("reading.fontSize.decrease.short")
        }
        .disabled(!settings.canDecreaseArabicFontSize)
        .accessibilityLabel(Text("reading.fontSize.decrease"))
        // Кнопки навбара не меняются с размером интерфейса.
        .fixedTextSize()
    }

    private var increase: some View {
        Button {
            withAnimation(Motion.fontSize) { settings.increaseArabicFontSize() }
        } label: {
            Text("reading.fontSize.increase.short")
        }
        .disabled(!settings.canIncreaseArabicFontSize)
        .accessibilityLabel(Text("reading.fontSize.increase"))
        .fixedTextSize()
    }
}

/// «А− А+» одной стеклянной капсулой из двух половин — Android `FontSizeControls` (`TopBar.kt`):
/// нажатая половина светит от своего края (ярко у пальца, к другому краю темнее), обводка разгорается,
/// капсула подаётся вперёд ×1.16. Высота — как у кругов навбара (36: навбар 44 обрезает всё выше).
private struct GlassFontSizeCapsule: View {
    let settings: ReadingSettings
    let tint: Color
    let header: Color

    @Environment(\.theme) private var theme
    @State private var decreasePressed = false
    @State private var increasePressed = false

    private static let halfWidth: CGFloat = 44
    private static let height = GlassIconButtonStyle.navigationBarDiameter
    private static let pressScale: CGFloat = 1.16
    /// На границе яркой части (`glowSolid`) свет уже на 30 % ушёл к цвету дальнего края.
    private static let glowMid = 0.3

    var body: some View {
        let colors = GlassColors(tint: SRGBColor(tint), header: SRGBColor(header))
        let isPressed = decreasePressed || increasePressed
        HStack(spacing: 0) {
            half(
                "reading.fontSize.decrease.short",
                label: "reading.fontSize.decrease",
                enabled: settings.canDecreaseArabicFontSize,
                pressed: $decreasePressed
            ) {
                settings.decreaseArabicFontSize()
            }
            half(
                "reading.fontSize.increase.short",
                label: "reading.fontSize.increase",
                enabled: settings.canIncreaseArabicFontSize,
                pressed: $increasePressed
            ) {
                settings.increaseArabicFontSize()
            }
        }
        .frame(height: Self.height)
        .background {
            ZStack {
                Capsule().fill(colors.fill.color)
                Capsule().fill(glow(colors, from: .leading)).opacity(decreasePressed ? 1 : 0)
                Capsule().fill(glow(colors, from: .trailing)).opacity(increasePressed ? 1 : 0)
                Capsule().strokeBorder(colors.rim.color, lineWidth: Size.hairline)
                Capsule().strokeBorder(colors.pressedRim.color, lineWidth: Size.hairline).opacity(isPressed ? 1 : 0)
            }
        }
        .scaleEffect(isPressed ? Self.pressScale : 1)
        .animation(isPressed ? Motion.glassPress : Motion.glassRelease, value: isPressed)
        .frame(minHeight: Size.minTapTarget)
        // Кнопки навбара не меняются с размером интерфейса.
        .fixedTextSize()
    }

    private func half(
        _ title: LocalizedStringKey,
        label: LocalizedStringKey,
        enabled: Bool,
        pressed: Binding<Bool>,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            withAnimation(Motion.fontSize) { action() }
        } label: {
            Text(title)
                .font(.body.weight(.semibold))
                .foregroundStyle(theme.palette.onAccent)
                .frame(width: Self.halfWidth, height: Size.minTapTarget)
                .contentShape(.rect)
        }
        .buttonStyle(PressReportingButtonStyle(isPressed: pressed))
        .disabled(!enabled)
        .opacity(enabled ? 1 : PressFeedback.disabledOpacity)
        .accessibilityLabel(Text(label))
    }

    private func glow(_ colors: GlassColors, from edge: UnitPoint) -> LinearGradient {
        LinearGradient(
            stops: [
                .init(color: colors.glowNear.color, location: 0),
                .init(color: colors.glowNear.mixed(with: colors.glowFar, by: Self.glowMid).color, location: colors.glowSolid),
                .init(color: colors.glowFar.color, location: 1),
            ],
            startPoint: edge,
            endPoint: edge == .leading ? .trailing : .leading
        )
    }
}

/// Кнопка без своего вида, сообщающая о нажатии наверх — отклик рисует капсула целиком.
private struct PressReportingButtonStyle: ButtonStyle {
    @Binding var isPressed: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, pressed in isPressed = pressed }
    }
}
