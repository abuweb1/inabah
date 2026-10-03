import Foundation
import MediaPlayer

/// Связь плеера с экраном блокировки и Пунктом управления. Протокол — для тестов без MediaPlayer.
protocol NowPlayingUpdating: AnyObject {
    func attach(to controller: AudioPlayerController)
    func trackDidChange()
    func playbackDidChange()
}

/// Now Playing и пульт — только в режиме «Прослушать все»: одиночная запись в фоне не играет.
///
/// На экране блокировки — ⏮ ⏯ ⏭ и перемотка бегунком; ±10 с выключены (система показывает
/// либо переход между записями, либо ±10 с). Команды включаются по состоянию плеера при
/// каждом обновлении и выключаются в одиночном режиме и после закрытия плеера.
final class NowPlayingCoordinator: NowPlayingUpdating {
    private weak var controller: AudioPlayerController?
    private var isRegistered = false

    func attach(to controller: AudioPlayerController) {
        self.controller = controller
    }

    func trackDidChange() { update() }
    func playbackDidChange() { update() }

    private func update() {
        let center = MPRemoteCommandCenter.shared()
        guard let controller, controller.mode == .playlist, let item = controller.currentItem,
              controller.duration > 0 else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            setCommandsEnabled(false, in: center)
            return
        }
        registerCommandsIfNeeded()
        center.playCommand.isEnabled = true
        center.pauseCommand.isEnabled = true
        center.togglePlayPauseCommand.isEnabled = true
        center.changePlaybackPositionCommand.isEnabled = true
        center.nextTrackCommand.isEnabled = controller.canGoNext
        center.previousTrackCommand.isEnabled = controller.canGoPrevious
        center.skipForwardCommand.isEnabled = false
        center.skipBackwardCommand.isEnabled = false

        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: item.track.title,
            MPMediaItemPropertyArtist: item.track.subtitle,
            MPMediaItemPropertyAlbumTitle: item.track.category,
            MPMediaItemPropertyPlaybackDuration: controller.duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: controller.engineCurrentTime,
            // Реальная скорость: иначе прогресс на экране блокировки уплывает при 0,75× / 1,5×.
            MPNowPlayingInfoPropertyPlaybackRate: controller.isPlaying ? Double(controller.playbackRate) : 0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: Double(controller.playbackRate),
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: controller.index,
            MPNowPlayingInfoPropertyPlaybackQueueCount: controller.queueCount,
        ]
    }

    private func setCommandsEnabled(_ enabled: Bool, in center: MPRemoteCommandCenter) {
        for command in [
            center.playCommand, center.pauseCommand, center.togglePlayPauseCommand,
            center.nextTrackCommand, center.previousTrackCommand, center.changePlaybackPositionCommand,
            center.skipForwardCommand, center.skipBackwardCommand,
        ] {
            command.isEnabled = enabled
        }
    }

    private func registerCommandsIfNeeded() {
        guard !isRegistered else { return }
        isRegistered = true
        Self.registerCommands(for: controller)
    }

    /// Обработчики регистрируются вне главного актора. Система вызывает их на главном потоке —
    /// тогда команда выполняется сразу и возвращается настоящий статус; на другом потоке
    /// (не ожидается) команда переносится на главный актор.
    private nonisolated static func registerCommands(for controller: AudioPlayerController?) {
        let target = WeakController(value: controller)
        let center = MPRemoteCommandCenter.shared()

        func on(_ command: MPRemoteCommand, _ action: @escaping @Sendable @MainActor (AudioPlayerController) -> Void) {
            command.addTarget { _ in
                perform(on: target, action)
            }
        }

        on(center.playCommand) { $0.resume() }
        on(center.pauseCommand) { $0.pause() }
        on(center.togglePlayPauseCommand) { $0.togglePlayPause() }
        on(center.nextTrackCommand) { $0.next() }
        on(center.previousTrackCommand) { $0.previous() }

        center.changePlaybackPositionCommand.addTarget { event in
            guard let position = (event as? MPChangePlaybackPositionCommandEvent)?.positionTime else {
                return .commandFailed
            }
            return perform(on: target) { $0.seek(to: position) }
        }
    }

    private nonisolated static func perform(
        on target: WeakController,
        _ action: @escaping @Sendable @MainActor (AudioPlayerController) -> Void
    ) -> MPRemoteCommandHandlerStatus {
        guard Thread.isMainThread else {
            Task { @MainActor in
                if let controller = target.value { action(controller) }
            }
            return .success
        }
        return MainActor.assumeIsolated {
            guard let controller = target.value, controller.hasTrack else {
                return .noActionableNowPlayingItem
            }
            action(controller)
            return .success
        }
    }
}

/// Слабая ссылка, которую можно захватить в `@Sendable`-замыкание: плеер изолирован
/// на главном акторе (`Sendable`), а `weak let` компилятор считает изменяемой ячейкой.
private nonisolated struct WeakController: Sendable {
    weak var value: AudioPlayerController?
}
