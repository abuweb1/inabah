import SwiftUI

/// Поле времени (часы и минуты): `ChromeStyle.system` — системный `DatePicker` (капсула, колесо в стекле
/// iOS 26); `.custom` (iOS 18–25) — своя капсула «17:00» и барабан под ней, как Android `SettingsValueChip`
/// + `TimePickerPopover`. Барабан показывает `timePickerHost()` контейнера (форма настроек) — поверх всего,
/// касание мимо закрывает; выбранное время применяется сразу. `selection` — дата опорного дня в `calendar`.
struct TimeField: View {
    let label: LocalizedStringResource
    @Binding var selection: Date
    let calendar: Calendar
    let tint: Color

    @Environment(\.chromeStyle) private var chromeStyle
    @Environment(\.theme) private var theme
    @Environment(\.locale) private var locale
    @State private var isOpen = false

    private static let fillOpacity = 0.1

    var body: some View {
        if chromeStyle == .custom {
            chip
        } else {
            DatePicker(selection: $selection, displayedComponents: .hourAndMinute) {
                Text(label)
            }
            .labelsHidden()
        }
    }

    private var chip: some View {
        let text = Text(selection, format: timeFormat)
        return Button {
            withAnimation(isOpen ? Motion.popoverClose : Motion.dialog) { isOpen.toggle() }
        } label: {
            text
                .font(.body.monospacedDigit())
                .foregroundStyle(isOpen ? tint : theme.palette.onAccent)
                .padding(.horizontal, Spacing.l)
                .padding(.vertical, Spacing.xs)
                .background(theme.palette.onAccent.opacity(Self.fillOpacity), in: .capsule)
        }
        .buttonStyle(PressDimButtonStyle())
        .accessibilityLabel(Text(label))
        .accessibilityValue(text)
        .anchorPreference(key: TimePickerRequestKey.self, value: .bounds) { anchor in
            guard isOpen else { return nil }
            let components = calendar.dateComponents([.hour, .minute], from: selection)
            return TimePickerRequest(
                anchor: anchor,
                hour: components.hour ?? 0,
                minute: components.minute ?? 0,
                tint: tint,
                dismiss: { withAnimation(Motion.popoverClose) { isOpen = false } },
                change: { hour, minute in
                    if let date = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: selection) {
                        selection = date
                    }
                }
            )
        }
    }

    /// Время как у `DatePicker`: при 24 часах — «05:00» (две цифры часа), при 12 — «5:00 AM».
    private var timeFormat: Date.FormatStyle {
        let style = Date.FormatStyle(locale: locale, calendar: calendar, timeZone: calendar.timeZone)
        let is12Hour = TimePickerHostModifier.dayPeriods(locale: locale) != nil
        return style
            .hour(is12Hour ? .defaultDigits(amPM: .abbreviated) : .twoDigits(amPM: .omitted))
            .minute(.twoDigits)
    }
}

/// Открытый барабан: капсула-якорь, текущее время и что делать с выбором.
struct TimePickerRequest {
    let anchor: Anchor<CGRect>
    let hour: Int
    let minute: Int
    let tint: Color
    let dismiss: () -> Void
    let change: (_ hour: Int, _ minute: Int) -> Void
}

private struct TimePickerRequestKey: PreferenceKey {
    static let defaultValue: TimePickerRequest? = nil

    static func reduce(value: inout TimePickerRequest?, nextValue: () -> TimePickerRequest?) {
        value = value ?? nextValue()
    }
}

/// Слой для барабана открытого `TimeField`: карточка под капсулой (Android `BelowEnd`, без уголка).
private struct TimePickerHostModifier: ViewModifier {
    @Environment(\.locale) private var locale

    func body(content: Content) -> some View {
        content.overlayPreferenceValue(TimePickerRequestKey.self) { request in
            GeometryReader { proxy in
                let periods = Self.dayPeriods(locale: locale)
                AnchoredPopover(
                    anchor: request.map { proxy[$0.anchor] },
                    containerWidth: proxy.size.width,
                    width: TimeWheelPicker.width(columns: periods == nil ? 2 : 3),
                    tint: request?.tint ?? .clear,
                    belowEnd: .init(containerHeight: proxy.size.height, cardHeight: TimeWheelPicker.height),
                    onDismiss: { request?.dismiss() }
                ) {
                    if let request {
                        TimeWheelPicker(hour: request.hour, minute: request.minute, dayPeriods: periods, onChange: request.change)
                    }
                }
            }
        }
    }

    /// AM / PM — когда система показывает время в 12-часовом формате (как `DatePicker`).
    static func dayPeriods(locale: Locale) -> (am: String, pm: String)? {
        let pattern = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: locale) ?? ""
        guard pattern.contains("a") else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        return (calendar.amSymbol, calendar.pmSymbol)
    }
}

extension View {
    /// Контейнер, над которым открываются барабаны `TimeField` (формы настроек ставят его сами).
    func timePickerHost() -> some View {
        modifier(TimePickerHostModifier())
    }
}
