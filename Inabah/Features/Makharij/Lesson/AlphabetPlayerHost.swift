import SwiftUI

extension View {
    /// «Послушать алфавит» экрана (Android `AlphabetPlayerHost` + `AlphabetPlaybackEffect`): панель под шапкой
    /// поверх содержимого - выезжает сверху и уезжает обратно, содержимое не сдвигает (просьба пользователя
    /// 2026-10-20); звук - элементы `items` по `playback`. Общее для урока (буквы) и «Весь алфавит» (слоги).
    func alphabetPlayer(
        _ playback: Binding<AlphabetPlayback?>,
        items: [AlphabetItem],
        colors: ReadingColors,
        previousLabel: LocalizedStringResource = "makharij.alphabet.previous",
        nextLabel: LocalizedStringResource = "makharij.alphabet.next"
    ) -> some View {
        modifier(AlphabetPlayerHost(
            playback: playback, items: items, colors: colors, previousLabel: previousLabel, nextLabel: nextLabel
        ))
    }
}

private struct AlphabetPlayerHost: ViewModifier {
    @Binding var playback: AlphabetPlayback?
    let items: [AlphabetItem]
    let colors: ReadingColors
    let previousLabel: LocalizedStringResource
    let nextLabel: LocalizedStringResource

    @Environment(ClipPlayer.self) private var clips
    @Environment(\.scenePhase) private var scenePhase

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                ZStack(alignment: .top) {
                    if let playback, items.indices.contains(playback.index) {
                        AlphabetPlayerBar(
                            item: items[playback.index], playback: playback, colors: colors,
                            previousLabel: previousLabel, nextLabel: nextLabel, onCommand: perform
                        )
                        .padding(.horizontal, Spacing.xl)
                        .padding(.top, Spacing.s)
                        .transition(.move(edge: .top).combined(with: .opacity))
                    }
                }
                .frame(maxWidth: .infinity)
                // Выезжает из-под шапки: выше своего верха не рисуется, тень снизу и по бокам остаётся.
                .clipShape(BelowTop())
                .animation(Motion.collapse, value: playback == nil)
            }
            .task(id: playback?.key) { await play() }
            .onChange(of: scenePhase) { _, phase in
                if phase != .active { playback?.pause() }
            }
    }

    private func perform(_ command: AlphabetPlayerBar.Command) {
        switch command {
        case .previous: playback?.previous()
        case .togglePause: playback?.togglePause()
        case .stop: playback = nil
        case .next: playback?.next()
        }
    }

    /// Звучит элемент; доиграл - пауза и следующий. Без записи - подсветка `silentItem`. Прервали или зазвучало
    /// другое - пауза. Сменился элемент, пауза, стоп, ушли с экрана - задача отменяется, и запись замолкает
    /// (`playToEnd`).
    private func play() async {
        guard let playback, !playback.isPaused, items.indices.contains(playback.index) else { return }
        if let file = items[playback.index].audio, clips.available.contains(file) {
            let finished = await clips.playToEnd(file)
            guard !Task.isCancelled else { return }
            guard finished else {
                self.playback?.pause()
                return
            }
            try? await Task.sleep(for: AlphabetPlayback.gap)
        } else {
            try? await Task.sleep(for: AlphabetPlayback.silentItem)
        }
        guard !Task.isCancelled else { return }
        if clips.playing != nil {
            self.playback?.pause()
        } else if self.playback?.advance() == false {
            self.playback = nil
        }
    }
}

/// Всё ниже верхнего края: обрезает только то, что выше (панель, уезжающая под шапку), не тень снизу и по бокам.
private nonisolated struct BelowTop: Shape {
    private static let reach: CGFloat = 10_000

    func path(in rect: CGRect) -> Path {
        Path(CGRect(x: rect.minX - Self.reach, y: rect.minY, width: rect.width + 2 * Self.reach, height: rect.height + Self.reach))
    }
}
