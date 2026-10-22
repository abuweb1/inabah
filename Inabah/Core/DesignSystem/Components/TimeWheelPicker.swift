import SwiftUI

/// Барабан часов и минут — Android `TimeWheelPicker` (`WheelPicker.kt`), как iOS `DatePicker(.wheel)`:
/// одна полоса выбора на все барабаны; при 12-часовом формате — часы 12, 1…11 по кругу и барабан AM / PM.
/// `hour` и `onChange` — всегда 0…23.
struct TimeWheelPicker: View {
    let hour: Int
    let minute: Int
    /// Подписи AM / PM; `nil` — 24 часа.
    let dayPeriods: (am: String, pm: String)?
    let onChange: (_ hour: Int, _ minute: Int) -> Void

    @Environment(\.theme) private var theme

    private static let hours = 24
    private static let halfDay = 12
    private static let minutes = 60
    private static let selectionOpacity = 0.12
    private static let selectionRadius: CGFloat = 12

    /// Ширина с полями: барабаны по 60 через 8.
    static func width(columns: Int) -> CGFloat {
        let count = CGFloat(columns)
        return WheelColumn.width * count + WheelColumn.gap * (count - 1) + 2 * Spacing.m
    }

    /// Высота с полями: пять строк по 32.
    static let height: CGFloat = WheelColumn.rowHeight * WheelColumn.visibleRows + 2 * Spacing.s

    var body: some View {
        let columns: CGFloat = dayPeriods == nil ? 2 : 3
        ZStack {
            RoundedRectangle(cornerRadius: Self.selectionRadius)
                .fill(theme.palette.onAccent.opacity(Self.selectionOpacity))
                .frame(
                    width: WheelColumn.width * columns + WheelColumn.gap * (columns - 1),
                    height: WheelColumn.rowHeight
                )
            HStack(spacing: WheelColumn.gap) {
                if let dayPeriods {
                    // Часы 12-часового барабана: 0 — «12»; половина дня — с третьего барабана.
                    WheelColumn(
                        rows: WheelRows(count: Self.halfDay, looping: true),
                        value: hour % Self.halfDay,
                        description: "time.picker.hours",
                        label: { $0 == 0 ? "\(Self.halfDay)" : "\($0)" },
                        onChange: { onChange($0 + hour / Self.halfDay * Self.halfDay, minute) }
                    )
                    minuteColumn
                    WheelColumn(
                        rows: WheelRows(count: 2, looping: false),
                        value: hour / Self.halfDay,
                        description: "time.picker.dayPeriod",
                        label: { $0 == 0 ? dayPeriods.am : dayPeriods.pm },
                        onChange: { onChange(hour % Self.halfDay + $0 * Self.halfDay, minute) }
                    )
                } else {
                    WheelColumn(
                        rows: WheelRows(count: Self.hours, looping: true),
                        value: hour,
                        description: "time.picker.hours",
                        label: Self.twoDigits,
                        onChange: { onChange($0, minute) }
                    )
                    minuteColumn
                }
            }
        }
        .padding(.horizontal, Spacing.m)
        .padding(.vertical, Spacing.s)
    }

    private var minuteColumn: some View {
        WheelColumn(
            rows: WheelRows(count: Self.minutes, looping: true),
            value: minute,
            description: "time.picker.minutes",
            label: Self.twoDigits,
            onChange: { onChange(hour, $0) }
        )
    }

    private static func twoDigits(_ value: Int) -> String {
        value < 10 ? "0\(value)" : "\(value)"
    }
}

/// Один барабан: значения по кругу (или от края до края), выбранное — на полосе посередине, дальние строки
/// бледнее и сжаты. Значение сообщается, когда барабан остановился. Для VoiceOver — один регулируемый
/// элемент («Часы, 17», жест вверх / вниз — на одно значение).
private struct WheelColumn: View {
    let rows: WheelRows
    let value: Int
    let description: LocalizedStringResource
    let label: (Int) -> String
    let onChange: (Int) -> Void

    @Environment(\.theme) private var theme
    /// Строка на полосе выбора (`scrollPosition` по центру).
    @State private var centered: Int?
    @State private var isScrolling = false

    /// Компактнее iOS-барабана (как на Android: полный размер на телефоне выглядел громоздко).
    static let rowHeight: CGFloat = 32
    static let visibleRows: CGFloat = 5
    static let width: CGFloat = 60
    static let gap: CGFloat = 8
    /// Насколько гаснут и сжимаются строки у края окна.
    private static let edgeFade = 0.75
    private static let edgeSqueeze: CGFloat = 0.35

    var body: some View {
        let rowHeight = Self.rowHeight
        let center = rowHeight * Self.visibleRows / 2
        // Край окна — половина видимых строк и ещё полстроки.
        let edge = Self.visibleRows / 2 + 0.5
        let fade = Self.edgeFade
        let squeeze = Self.edgeSqueeze
        ScrollViewReader { proxy in
            ScrollView(.vertical) {
                LazyVStack(spacing: 0) {
                    ForEach(0..<rows.size, id: \.self) { index in
                        Text(verbatim: label(rows.value(at: index)))
                            .font(.title3.monospacedDigit())
                            .foregroundStyle(theme.palette.onAccent)
                            .frame(width: Self.width, height: rowHeight)
                            .visualEffect { content, geometry in
                                // Дальние от середины строки — бледнее и ниже, как у барабана iOS. Середина окна —
                                // в координатах строки (границы прокрутки), без учёта полей содержимого.
                                let window = geometry.bounds(of: .scrollView) ?? .zero
                                let distance = abs(window.midY - geometry.size.height / 2) / rowHeight
                                let t = min(distance / edge, 1)
                                return content
                                    .opacity(1 - fade * t)
                                    .scaleEffect(x: 1, y: 1 - squeeze * t)
                            }
                    }
                }
                .scrollTargetLayout()
            }
            .contentMargins(.vertical, center - rowHeight / 2, for: .scrollContent)
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $centered, anchor: .center)
            .scrollIndicators(.hidden)
            .frame(width: Self.width, height: rowHeight * Self.visibleRows)
            .onAppear {
                let index = rows.index(of: value)
                centered = index
                // iOS 18 не применяет начальную `scrollPosition` к ленивому стеку — докручиваем явно.
                proxy.scrollTo(index, anchor: .center)
            }
            .onScrollPhaseChange { _, phase in
                isScrolling = phase != .idle
                if phase == .idle, let centered { onChange(rows.value(at: centered)) }
            }
            // Значение не приняли или сменили снаружи — барабан едет к принятому, как iOS DatePicker.
            .onChange(of: value) { _, newValue in
                guard !isScrolling, let current = centered, rows.value(at: current) != newValue else { return }
                withAnimation(Motion.highlight) {
                    centered = current + rows.distance(from: rows.value(at: current), to: newValue)
                }
            }
            .sensoryFeedback(.selection, trigger: centered)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(description))
        .accessibilityValue(Text(verbatim: label(centered.map(rows.value(at:)) ?? value)))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: step(1)
            case .decrement: step(-1)
            @unknown default: break
            }
        }
    }

    /// VoiceOver: сдвиг на одно значение — та же прокрутка, значение применяется сразу.
    private func step(_ delta: Int) {
        let current = centered ?? rows.index(of: value)
        let target = rows.shifted(current, by: delta)
        guard target != current else { return }
        withAnimation(Motion.highlight) { centered = target }
        onChange(rows.value(at: target))
    }
}
