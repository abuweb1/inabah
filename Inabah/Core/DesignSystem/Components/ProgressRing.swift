import SwiftUI

/// Кольцо прогресса: дорожка + заполнение от верхней точки по часовой стрелке.
struct ProgressRing: View {
    let fraction: Double
    let trackColor: Color
    let fillColor: Color
    var lineWidth: CGFloat = 4

    var body: some View {
        ZStack {
            Circle()
                .stroke(trackColor, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: min(max(fraction, 0), 1))
                .stroke(fillColor, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .padding(lineWidth / 2)
        .animation(.easeOut(duration: Motion.counterRing), value: fraction)
        .accessibilityHidden(true)
    }
}

#Preview {
    let palette = Theme.inabah.palette
    ProgressRing(fraction: 0.6, trackColor: palette.goldTrack, fillColor: palette.gold)
        .frame(width: Size.counter, height: Size.counter)
        .padding()
        .background(palette.card)
}
