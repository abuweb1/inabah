import SwiftUI

/// Тонкая полоса прогресса с градиентной заливкой.
struct LinearProgressBar<Fill: ShapeStyle>: View {
    let fraction: Double
    let track: Color
    let fill: Fill
    var height: CGFloat = Size.progressBarHeight

    var body: some View {
        Capsule()
            .fill(track)
            .overlay(alignment: .leading) {
                GeometryReader { proxy in
                    Capsule()
                        .fill(fill)
                        .frame(width: proxy.size.width * min(max(fraction, 0), 1))
                }
            }
            .frame(height: height)
            .clipShape(.capsule)
            .animation(Motion.progress, value: fraction)
            .accessibilityHidden(true)
    }
}
