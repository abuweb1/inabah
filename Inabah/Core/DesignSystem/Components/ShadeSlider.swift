import SwiftUI

/// Плавный ползунок от −1 до +1 с серединой «как задумано» («Фон перевода», 2026-10-10; на Android —
/// `ShadeSlider`). Вид — как у `StepSlider`: дорожка, белый бегунок; заливка — от середины к бегунку,
/// у середины бегунок прилипает к 0 со щелчком. По краям — кружки цвета, который будет на краю.
/// `onChange` — на каждое движение, `onCommit` — палец отпущен (VoiceOver меняет и сразу сохраняет).
struct ShadeSlider: View {
    let value: Double
    /// Подпись для VoiceOver.
    let label: LocalizedStringResource
    /// Описание значения для VoiceOver.
    let valueTitle: Text
    let startSwatch: Color
    let endSwatch: Color
    let onChange: (Double) -> Void
    let onCommit: () -> Void

    @Environment(\.theme) private var theme
    @State private var width: CGFloat = 1
    /// Палец на дорожке. Сбрасывается и при отмене жеста (прокрутка перехватила касание, звонок), где
    /// `onEnded` не вызывается, — сохранение идёт по его концу (аудит iOS 1.1.1, А4).
    @GestureState private var isDragging = false

    private enum Layout {
        static let trackHeight: CGFloat = 4
        static let tickWidth: CGFloat = 3
        static let tickHeight: CGFloat = 12
        static let thumbSize: CGFloat = 26
        static let swatchSize: CGFloat = 16
        /// Шаг VoiceOver — десятая часть половины дорожки.
        static let accessibilityStep: Double = 0.1
    }

    /// Насколько близко к середине бегунок прилипает к 0 — доля половины дорожки (как на Android).
    static let snap: Double = 0.06

    /// Значение под касанием `x` на дорожке шириной `width` с отступом `inset` по краям; вне дорожки —
    /// край, около середины — ровно 0.
    static func value(at x: CGFloat, width: CGFloat, inset: CGFloat) -> Double {
        let usable = max(width - 2 * inset, 1)
        let value = min(max(Double((x - inset) / usable) * 2 - 1, -1), 1)
        return abs(value) < snap ? 0 : value
    }

    var body: some View {
        HStack(spacing: Spacing.m) {
            swatch(startSwatch)
            track
            swatch(endSwatch)
        }
        .frame(minHeight: Size.minTapTarget)
        // Закреплён, как `StepSlider`: шаг интерфейса не меняет дорожку под пальцем.
        .fixedTextSize()
        .accessibilityElement()
        .accessibilityLabel(Text(label))
        .accessibilityValue(valueTitle)
        .accessibilityAdjustableAction { direction in
            let step = direction == .increment ? Layout.accessibilityStep : -Layout.accessibilityStep
            let target = min(max(value + step, -1), 1)
            change(abs(target) < Self.snap ? 0 : target)
            onCommit()
        }
        .sensoryFeedback(.selection, trigger: value == 0) { _, isCenter in isCenter }
    }

    private func swatch(_ color: Color) -> some View {
        Circle()
            .fill(color)
            .overlay { Circle().strokeBorder(theme.palette.hairline, lineWidth: Size.hairline) }
            .frame(width: Layout.swatchSize, height: Layout.swatchSize)
            .accessibilityHidden(true)
    }

    private var track: some View {
        let inset = Layout.thumbSize / 2
        let half = max(width - Layout.thumbSize, 1) / 2
        let shadow = ShadowToken.card(theme.palette)

        return ZStack(alignment: .leading) {
            Capsule()
                .fill(theme.palette.track)
                .frame(width: half * 2, height: Layout.trackHeight)
                .offset(x: inset)
            // Заливка от середины к бегунку — масштабом от середины, без смены вёрстки.
            Capsule()
                .fill(theme.palette.accentLight)
                .frame(width: half, height: Layout.trackHeight)
                .scaleEffect(x: abs(value), anchor: value < 0 ? .trailing : .leading)
                .offset(x: inset + (value < 0 ? 0 : half))
            Capsule()
                .fill(value == 0 ? theme.palette.accentLight : theme.palette.track)
                .frame(width: Layout.tickWidth, height: Layout.tickHeight)
                .offset(x: inset + half - Layout.tickWidth / 2)
            // Бегунок — фигура без содержимого: тень у неё самой, двигается сдвигом.
            Circle()
                .fill(theme.palette.onAccent)
                .shadow(color: shadow.color, radius: shadow.radius, y: shadow.y)
                .frame(width: Layout.thumbSize, height: Layout.thumbSize)
                .offset(x: half * (value + 1))
        }
        .frame(maxWidth: .infinity, minHeight: Size.minTapTarget, alignment: .leading)
        .contentShape(.rect)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = max($0, 1) }
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($isDragging) { _, state, _ in state = true }
                .onChanged { drag in change(Self.value(at: drag.location.x, width: width, inset: inset)) }
        )
        .onChange(of: isDragging) { _, dragging in
            if !dragging { onCommit() }
        }
    }

    private func change(_ target: Double) {
        guard target != value else { return }
        onChange(target)
    }
}
