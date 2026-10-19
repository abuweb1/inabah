import SwiftUI

/// Увеличение плитки долгим нажатием (Android `TileZoomOverlay`, `TileZoom.kt`): затемнение и плитка по
/// центру, вылетающая из рамки исходной ячейки - только трансформации отрисовки. Касание по затемнению -
/// обратно в ячейку. Общее для карточки буквы и «Читаем слова».
struct TileZoomOverlay<Tile: View>: View {
    /// Рамка исходной ячейки в координатах слоя.
    let source: CGRect
    /// Кегль слова в ячейке и в увеличенной плитке - текст летит, не растягиваясь.
    let sourceTextSize: Double
    let zoomedTextSize: Double
    let onDismissed: () -> Void
    @ViewBuilder let tile: Tile

    @Environment(\.theme) private var theme
    @State private var progress = 0.0
    @State private var isClosing = false

    static var tileHeight: CGFloat { 240 }
    private static var scrimOpacity: Double { 0.6 }

    var body: some View {
        GeometryReader { proxy in
            let target = CGRect(
                x: Spacing.xl,
                y: (proxy.size.height - Self.tileHeight) / 2,
                width: max(proxy.size.width - Spacing.xl * 2, 0),
                height: Self.tileHeight
            )
            ZStack(alignment: .topLeading) {
                theme.palette.shadow.opacity(Self.scrimOpacity * progress)
                    .accessibilityHidden(true)
                ZoomFlight(
                    progress: progress,
                    source: source,
                    target: target,
                    textScale: sourceTextSize / zoomedTextSize
                ) {
                    tile
                }
                // Касание по плитке без звука - как по затемнению: закрыть.
                .allowsHitTesting(false)
            }
        }
        // Касание в любом месте слоя - закрыть (вид затемнения меняется с анимацией и сам касания не ловит).
        .contentShape(.rect)
        .onTapGesture(perform: dismiss)
        .onAppear {
            withAnimation(Motion.zoom) { progress = 1 }
        }
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, dismiss)
    }

    private func dismiss() {
        guard !isClosing else { return }
        isClosing = true
        withAnimation(Motion.zoom) {
            progress = 0
        } completion: {
            onDismissed()
        }
    }
}

/// Кадр полёта при доле `progress`: плитка размера `target` сжата до `source` и сдвинута к нему;
/// содержимое - обратным масштабом, чтобы слово не растягивалось, а плавно росло.
private struct ZoomFlight<Content: View>: View, Animatable {
    var progress: Double
    let source: CGRect
    let target: CGRect
    let textScale: Double
    @ViewBuilder let content: Content

    var animatableData: Double {
        get { progress }
        set { progress = newValue }
    }

    var body: some View {
        let scaleX = lerp(source.width / max(target.width, 1), 1)
        let scaleY = lerp(source.height / max(target.height, 1), 1)
        let uniform = lerp(textScale, 1)
        content
            .scaleEffect(x: uniform / scaleX, y: uniform / scaleY)
            .frame(width: target.width, height: target.height)
            .scaleEffect(x: scaleX, y: scaleY, anchor: .topLeading)
            .offset(
                x: target.minX + lerp(source.minX - target.minX, 0),
                y: target.minY + lerp(source.minY - target.minY, 0)
            )
            .opacity(progress > 0 ? 1 : 0)
    }

    private func lerp(_ start: Double, _ end: Double) -> Double {
        start + (end - start) * progress
    }
}
