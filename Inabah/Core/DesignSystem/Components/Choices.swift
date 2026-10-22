import SwiftUI

/// Переключатель и сегменты своим видом при `ChromeStyle.custom` (iOS 18–25) — копия Android `Choices.kt`,
/// которая повторяет элементы iOS 26. При `.system` — системные (стекло iOS 26).

// MARK: - Переключатель

/// Переключатель в цвет раздела: `.system` — системный `Toggle`, `.custom` — `GlassSwitchToggleStyle`.
private struct ChoiceToggleModifier: ViewModifier {
    let tint: Color

    @Environment(\.chromeStyle) private var chromeStyle

    func body(content: Content) -> some View {
        if chromeStyle == .custom {
            content.toggleStyle(GlassSwitchToggleStyle(tint: tint))
        } else {
            content.tint(tint)
        }
    }
}

extension View {
    /// Переключатель строки настроек и карточек в цвет раздела `tint`.
    func choiceToggleStyle(tint: Color) -> some View {
        modifier(ChoiceToggleModifier(tint: tint))
    }
}

/// Android `SwitchRow`: подпись слева, справа дорожка 51 × 31 с белой ручкой 27; вкл — цвет раздела,
/// выкл — `track`; цвет и ручка — `Motion.highlight`. Нажимается вся строка; без отклика нажатия
/// (как у Android), с тактильным откликом вкл/выкл. Неактивный — приглушён.
struct GlassSwitchToggleStyle: ToggleStyle {
    let tint: Color

    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.theme) private var theme

    private static let trackSize = CGSize(width: 51, height: 31)
    private static let knobSize: CGFloat = 27
    private static let knobInset: CGFloat = 2

    func makeBody(configuration: Configuration) -> some View {
        let travel = (Self.trackSize.width - Self.knobSize) / 2 - Self.knobInset
        Button {
            withAnimation(Motion.highlight) { configuration.isOn.toggle() }
        } label: {
            HStack(spacing: Spacing.m) {
                configuration.label
                    .frame(maxWidth: .infinity, alignment: .leading)
                Capsule()
                    .fill(configuration.isOn ? tint : theme.palette.track)
                    .frame(width: Self.trackSize.width, height: Self.trackSize.height)
                    .overlay {
                        Circle()
                            .fill(.white)
                            .frame(width: Self.knobSize, height: Self.knobSize)
                            .offset(x: configuration.isOn ? travel : -travel)
                    }
                    .animation(Motion.highlight, value: configuration.isOn)
                    .accessibilityHidden(true)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .opacity(isEnabled ? 1 : PressFeedback.disabledOpacity)
        .sensoryFeedback(trigger: configuration.isOn) { _, isOn in
            .impact(weight: isOn ? .medium : .light)
        }
        // Для VoiceOver — переключатель, как системный (свой стиль внутри не применяется — без рекурсии).
        .accessibilityRepresentation {
            Toggle(isOn: configuration.$isOn) { configuration.label }
                .toggleStyle(.switch)
        }
    }
}

// MARK: - Сегменты

/// Выбор из нескольких значений сегментами: `.system` — `Picker(.segmented)`, `.custom` — `GlassSegmentedControl`.
struct SegmentedChoice<Value: Hashable>: View {
    let title: LocalizedStringKey
    @Binding var selection: Value
    let options: [Value]
    let label: (Value) -> Text

    @Environment(\.chromeStyle) private var chromeStyle

    var body: some View {
        if chromeStyle == .custom {
            GlassSegmentedControl(title: title, selection: $selection, options: options, label: label)
        } else {
            Picker(title, selection: $selection) {
                ForEach(options, id: \.self) { option in
                    label(option).tag(option)
                }
            }
            .pickerStyle(.segmented)
        }
    }
}

/// Android `SegmentedControl`: капсула 32 цвета `track`, выбранный сегмент — белая капсула ×0.28,
/// переезжает `Motion.highlight`; подписи `subheadline`, у выбранной — полужирная. Сегменты равной ширины;
/// щелчок — только при смене выбора.
private struct GlassSegmentedControl<Value: Hashable>: View {
    let title: LocalizedStringKey
    @Binding var selection: Value
    let options: [Value]
    let label: (Value) -> Text

    @Environment(\.theme) private var theme
    @Namespace private var pill

    private static var height: CGFloat { 32 }
    private static var inset: CGFloat { 2 }
    private static var pillOpacity: Double { 0.28 }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.self) { option in
                let isSelected = option == selection
                Button {
                    guard !isSelected else { return }
                    withAnimation(Motion.highlight) { selection = option }
                } label: {
                    label(option)
                        .font(.subheadline.weight(isSelected ? .semibold : .medium))
                        .foregroundStyle(theme.palette.onAccent)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background {
                            if isSelected {
                                Capsule()
                                    .fill(theme.palette.onAccent.opacity(Self.pillOpacity))
                                    .matchedGeometryEffect(id: "pill", in: pill)
                            }
                        }
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
            }
        }
        .padding(Self.inset)
        .frame(height: Self.height)
        .background(theme.palette.track, in: .capsule)
        .sensoryFeedback(.selection, trigger: selection)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text(title))
    }
}
