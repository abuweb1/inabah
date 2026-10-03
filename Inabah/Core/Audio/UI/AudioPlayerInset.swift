import SwiftUI

/// Мини-плеер над таб-баром для экрана, к которому применён модификатор.
///
/// Панель строится только у экрана, который сейчас на виду (`onAppear`/`onDisappear`):
/// экраны ниже в стеке навигации (главная под списком) и на неактивных вкладках остаются
/// в иерархии, и без этого флага у каждого из них работала бы своя невидимая панель
/// с обновлением позиции и анимацией бегунка.
private struct AudioPlayerInsetModifier: ViewModifier {
    @Environment(AudioPlayerController.self) private var player
    @State private var isOnScreen = false

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                // Анимация — только у самой панели, а не у всего экрана: иначе в неё попадает
                // и перерисовка ленты (смена отступа, состояние карточек).
                ZStack {
                    if isOnScreen && player.isPanelVisible {
                        AudioPlayerPanel()
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                }
                .animation(Motion.collapse, value: player.isPanelVisible)
            }
            .onAppear { isOnScreen = true }
            .onDisappear { isOnScreen = false }
    }
}

extension View {
    /// Мини-плеер над таб-баром. Применяется к корню каждого экрана (не к `NavigationStack`):
    /// так прокрутка экрана получает отступ снизу и последняя карточка не прячется под плеером.
    func audioPlayerInset() -> some View {
        modifier(AudioPlayerInsetModifier())
    }
}
