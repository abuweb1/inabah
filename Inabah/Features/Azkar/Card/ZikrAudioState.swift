import Foundation

/// Состояние записи зикра для карточки — простое значение, которое сравнивается дёшево.
/// Только то, что карточка показывает: пауза/продолжение записи карточку не перерисовывают.
nonisolated enum ZikrAudioState: Equatable, Sendable {
    /// Запись не выбрана в плеере.
    case idle
    /// Запись выбрана и не доиграла (звучит или на паузе).
    case active(isInPlaylist: Bool)

    var isInPlaylist: Bool {
        if case .active(let isInPlaylist) = self { isInPlaylist } else { false }
    }
}

extension AudioPlayerController {
    /// Состояние записи зикра — вычисляется лентой и передаётся в карточку.
    func audioState(of zikr: Zikr) -> ZikrAudioState {
        guard isActive(zikr.audioTrackID) else { return .idle }
        return .active(isInPlaylist: mode == .playlist)
    }
}
