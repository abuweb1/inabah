import SwiftUI

/// Оформление кольца прогресса на карточке раздела — у каждой карточки своё.
struct NavCardRingStyle: Equatable {
    let track: Color
    let fill: Color
    /// Цвет процентов внутри кольца.
    let text: Color
}

/// Прогресс раздела на карточке: тонкое кольцо с процентами; по заполнении кольцо замыкается,
/// а проценты сменяются галочкой того же цвета — без заливки, в стиле карточки.
struct NavCardProgressRing: View {
    let fraction: Double
    let style: NavCardRingStyle

    private var isDone: Bool { fraction >= 1 }
    /// До полного выполнения — не больше 99 %: «100 %» без галочки сбивало бы с толку.
    private var percent: Int {
        isDone ? 100 : min(Int((max(fraction, 0) * 100).rounded()), 99)
    }

    private enum Layout {
        static let minimumTextScale: CGFloat = 0.7
        /// Кольцо фиксированного размера: проценты растут с Dynamic Type только до этого размера,
        /// дальше задевали бы кольцо (VoiceOver читает их полностью).
        static let maxTextSize = DynamicTypeSize.xxxLarge
    }

    var body: some View {
        ZStack {
            ProgressRing(fraction: fraction, trackColor: style.track, fillColor: style.fill, lineWidth: Size.ringStroke)
            if isDone {
                Image(systemName: "checkmark")
                    .font(.callout.weight(.bold))
                    .foregroundStyle(style.fill)
                    .transition(.scale.combined(with: .opacity))
            } else {
                Text(Double(percent) / 100, format: .percent.precision(.fractionLength(0)))
                    .font(.caption2.weight(.bold))
                    .monospacedDigit()
                    .foregroundStyle(style.text)
                    .contentTransition(.numericText(value: Double(percent)))
                    .minimumScaleFactor(Layout.minimumTextScale)
                    .transition(.opacity)
            }
        }
        .dynamicTypeSize(...Layout.maxTextSize)
        .frame(width: Size.minTapTarget, height: Size.minTapTarget)
        .animation(Motion.highlight, value: isDone)
        .animation(Motion.highlight, value: percent)
        .accessibilityElement()
        .accessibilityLabel(isDone ? Text("section.progress.done") : Text("section.progress.percent \(percent)"))
    }
}

#Preview {
    let palette = Theme.inabah.palette
    let style = NavCardRingStyle(track: palette.goldTrack, fill: palette.gold, text: palette.gold)
    HStack {
        NavCardProgressRing(fraction: 0.13, style: style)
        NavCardProgressRing(fraction: 1, style: style)
    }
    .padding()
    .background(palette.card)
}
