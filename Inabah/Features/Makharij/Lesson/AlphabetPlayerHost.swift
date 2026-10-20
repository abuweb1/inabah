import SwiftUI

/// «Послушать алфавит» экрана: что звучит (`playback`) и играет ли вообще (`isActive`). Отдельным объектом, а не
/// `@State` экрана: кнопке ▶/■ в навбаре нужно только `isActive`, и смена буквы или слога (каждые 2 с) не
/// пересобирает экран вместе с навбаром - только панель и звучащие ячейки.
@Observable
final class AlphabetPlayer {
    var playback: AlphabetPlayback? {
        didSet {
            let active = playback != nil
            if active != isActive { isActive = active }
        }
    }

    private(set) var isActive = false

    /// ▶ - с первого элемента, ■ - стоп.
    func toggle(count: Int) {
        playback = isActive ? nil : AlphabetPlayback(count: count)
    }
}

/// ▶/■ «Послушать алфавит» в навбаре: читает только `isActive`.
struct AlphabetPlayToggle: View {
    let player: AlphabetPlayer
    let count: Int

    var body: some View {
        ReadingHeaderButton(
            symbol: player.isActive ? "stop.fill" : "play.fill",
            label: player.isActive ? "makharij.alphabet.stop" : "makharij.alphabet.play"
        ) {
            player.toggle(count: count)
        }
    }
}

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

    /// Запас под панелью, где рисуется её тень (`ShadowToken.floating`: радиус 18, сдвиг 6).
    private static let shadowRoom: CGFloat = 28

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
                // Выезжает из-под шапки: выше своего верха не рисуется. Обрезка - прямоугольником (`clipped`)
                // с запасом снизу на тень: маска произвольной формы пересчитывалась каждый кадр выезда.
                .padding(.bottom, Self.shadowRoom)
                .clipped()
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
