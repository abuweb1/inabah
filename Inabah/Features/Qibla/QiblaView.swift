import SwiftUI

/// Экран «Къибла» (`docs/android/06-screens.md`, «Къибла»; Android `QiblaScreen.kt`): кольцо, под ним
/// надпись, плитки местоположения и совета. Компас и поиск координат работают, только пока экран на виду
/// и приложение активно. Доступ к местоположению при первом входе - сначала наше пояснение, потом окно системы.
struct QiblaView: View {
    @Environment(\.qibla) private var services

    var body: some View {
        if let services {
            QiblaScreen(services: services)
        }
    }
}

private struct QiblaScreen: View {
    let services: QiblaServices

    @Environment(\.theme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    @State private var model: QiblaModel
    /// «Перезапустить компас» (решение пользователя 2026-10-24): новая подписка - координаты заново, метка без сглаживания.
    @State private var session = 0
    @State private var dialogDismissed = false
    @State private var helpVisible = false
    @State private var helpHintVisible = false

    private enum Layout {
        static let dialMaxSize: CGFloat = 360
        static let dialHeightShare: CGFloat = 0.46
        /// Подсказка у ⓘ: через 0,6 с после открытия, 4 с - как в «Учимся читать».
        static let helpHintDelay = Duration.milliseconds(600)
        static let helpHintDuration = Duration.seconds(4)
    }

    init(services: QiblaServices) {
        self.services = services
        _model = State(initialValue: QiblaModel(location: services.location, heading: services.heading))
    }

    private var access: LocationAccess { services.access.access }
    private var dialogVisible: Bool { !dialogDismissed && access == .notGranted }
    private var run: RunKey { RunKey(access: access, session: session, active: scenePhase == .active) }

    var body: some View {
        content
            .toolbar(.hidden, for: .navigationBar)
            // Пока открыты окно или справка, VoiceOver читает только их.
            .accessibilityHidden(dialogVisible || helpVisible)
            .overlay {
                if dialogVisible {
                    // Под окном - размытая копия экрана: пока окно открыто, доступа нет и экран не меняется.
                    QiblaPermissionDialog(onAllow: requestPermission, onLater: { dialogDismissed = true }) {
                        content
                    }
                    .transition(.opacity)
                }
            }
            .overlay {
                SlideUpSheet(isPresented: helpVisible, onDismiss: { helpVisible = false }) {
                    QiblaHelpContent { helpVisible = false }
                }
            }
            .animation(Motion.dialog, value: dialogVisible)
            .animation(.easeInOut, value: helpHintVisible)
            // Источники - пока экран на виду и приложение активно; новый доступ или ↻ - новый запуск.
            .task(id: run) {
                guard run.active else { return }
                await model.run(access: run.access)
            }
            // Окно системы делает приложение неактивным: подсказка ждёт, пока оно закроется.
            .task(id: [dialogVisible, scenePhase == .active]) { await showHelpHint() }
    }

    /// Экран без окон поверх: шапка, кольцо, надпись, плитки на фоне раздела.
    private var content: some View {
        let screen = model.screen
        let headline = QiblaHeadline(screen)
        return GeometryReader { proxy in
            let dialSize = min(proxy.size.width - 2 * Spacing.xl, proxy.size.height * Layout.dialHeightShare, Layout.dialMaxSize)
            ScrollView {
                VStack(spacing: 0) {
                    QiblaDialHost(model: model, screen: screen, fallback: headline.title)
                        .frame(width: dialSize, height: dialSize)
                    QiblaHeadlineBlock(headline: headline, actions: actions)
                    VStack(spacing: Spacing.m) {
                        QiblaLocationTile(state: screen, onRefresh: model.refreshLocation)
                        QiblaAdviceTile(helpHintVisible: helpHintVisible, onOpenHelp: openHelp)
                    }
                    .padding(.top, Spacing.xlPlus)
                    QiblaHoldFlatNote(tilted: screen.guidance.map(Self.isTilted) ?? false)
                        .padding(.top, Spacing.xlPlus)
                }
                .padding(.horizontal, Spacing.xl)
                .padding(.top, Spacing.xs)
                .padding(.bottom, Spacing.xl)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            QiblaHeaderBar(onRestart: { session += 1 })
        }
        .background { theme.gradients.azkarBackground.linear.ignoresSafeArea() }
    }

    private var actions: QiblaActions {
        QiblaActions(
            requestPermission: requestPermission,
            openSettings: openSettings,
            refreshLocation: model.refreshLocation,
            openHelp: openHelp
        )
    }

    private func requestPermission() {
        dialogDismissed = true
        services.access.request()
    }

    /// На iOS - только экран приложения в настройках (раздел «Геолокация» системы открыть нельзя).
    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        openURL(url)
    }

    private func openHelp() {
        helpHintVisible = false
        services.hints.markHelpSeen()
        helpVisible = true
    }

    /// Подсказка у ⓘ - один раз, когда поверх ничего нет: ни нашего окна, ни окна системы.
    private func showHelpHint() async {
        guard !services.hints.helpSeen, !dialogVisible else { return }
        try? await Task.sleep(for: Layout.helpHintDelay)
        // Пока ждали, ⓘ могли нажать, ушли с экрана или поверх открылось окно системы.
        guard !Task.isCancelled, !services.hints.helpSeen, !dialogVisible, scenePhase == .active else { return }
        services.hints.markHelpSeen()
        helpHintVisible = true
        try? await Task.sleep(for: Layout.helpHintDuration)
        helpHintVisible = false
    }

    private static func isTilted(_ guidance: QiblaGuidance) -> Bool {
        if case .tilted = guidance { true } else { false }
    }
}

/// Запуск источников: доступ, номер ↻ и активно ли приложение.
private struct RunKey: Equatable {
    let access: LocationAccess
    let session: Int
    let active: Bool
}

/// Шапка без фона над градиентом (Android `InabahTopBar`): «‹», по центру «Къибла» и «Направление молитвы»,
/// справа ↻ «Перезапустить компас» с откликом, как у счётчика азкаров.
private struct QiblaHeaderBar: View {
    let onRestart: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.dismiss) private var dismiss
    @State private var restarts = 0

    private static let barHeight: CGFloat = 56

    var body: some View {
        HStack(spacing: Spacing.s) {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.backward")
                    .font(.body.weight(.semibold))
            }
            .buttonStyle(GlassIconButtonStyle(tint: theme.palette.tabAzkar))
            .accessibilityLabel(Text("navigation.back"))
            VStack(spacing: 0) {
                Text("qibla.title")
                    .font(.headline.bold())
                    .foregroundStyle(theme.palette.onAccent)
                Text("qibla.subtitle")
                    .font(.caption)
                    .foregroundStyle(theme.palette.onAccentSecondary)
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            Button {
                restarts += 1
                onRestart()
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.body.weight(.semibold))
            }
            .buttonStyle(GlassIconButtonStyle(tint: theme.palette.tabAzkar))
            .accessibilityLabel(Text("qibla.restart"))
            .sensoryFeedback(.impact(weight: .light), trigger: restarts)
        }
        .padding(.horizontal, Spacing.xl)
        .frame(height: Self.barHeight)
        .fixedTextSize()
    }
}
