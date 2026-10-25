import SwiftUI

/// Вход в «Къиблу» - справа вверху главной «Азкаров» (Android `topAction`): стеклянный круг 40 с Каабой 24.
/// Подсказка под кнопкой - один раз, через 0,5 с после открытия главной, 4 с; ждёт, пока решится и закроется
/// «Что нового» (решение пользователя 2026-10-24, как в «Учимся читать»).
struct QiblaEntryButton: View {
    @Environment(\.qibla) private var services
    @Environment(WhatsNew.self) private var whatsNew
    @Environment(\.theme) private var theme
    @Environment(\.scenePhase) private var scenePhase

    @State private var hintVisible = false

    private enum Layout {
        /// 2026-10-25, было 28 - пользователь: крупно.
        static let kaaba: CGFloat = 24
        static let hintDelay = Duration.milliseconds(500)
        static let hintDuration = Duration.seconds(4)
    }

    var body: some View {
        NavigationLink(value: AzkarRoute.qibla) {
            Image(.qiblaKaabaIcon)
                .resizable()
                .scaledToFit()
                .frame(width: Layout.kaaba, height: Layout.kaaba)
        }
        // Кааба при нажатии не засвечивается - светится только фон кнопки (решение пользователя 2026-10-25).
        .buttonStyle(GlassIconButtonStyle(tint: theme.palette.tabAzkar, recolorsContent: false))
        .simultaneousGesture(TapGesture().onEnded { markSeen() })
        .accessibilityLabel(Text("qibla.title"))
        .hintBubble(
            "azkar.qibla.hint",
            isVisible: hintVisible,
            tint: theme.palette.tabAzkar,
            textColor: theme.palette.background,
            below: true
        )
        .animation(.easeInOut, value: hintVisible)
        .task(id: HintGate(checked: whatsNew.isChecked, blocked: whatsNew.pending != nil, active: scenePhase == .active)) {
            await showHint()
        }
        .onDisappear { hintVisible = false }
    }

    private func showHint() async {
        guard let hints = services?.hints, !hints.entrySeen,
              whatsNew.isChecked, whatsNew.pending == nil, scenePhase == .active else { return }
        try? await Task.sleep(for: Layout.hintDelay)
        // Пока ждали, кнопку могли нажать или ушли с главной.
        guard !Task.isCancelled, !hints.entrySeen else { return }
        hints.markEntrySeen()
        hintVisible = true
        try? await Task.sleep(for: Layout.hintDuration)
        hintVisible = false
    }

    private func markSeen() {
        hintVisible = false
        services?.hints.markEntrySeen()
    }
}

/// Когда подсказке можно появиться: «Что нового» проверено, окна нет, приложение активно.
private struct HintGate: Equatable {
    let checked: Bool
    let blocked: Bool
    let active: Bool
}
