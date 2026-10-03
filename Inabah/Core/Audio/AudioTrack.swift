import Foundation

/// Аудиозапись для плеера. Не знает, откуда она (зикр, хадис) — плеер работает с любыми треками.
nonisolated struct AudioTrack: Identifiable, Hashable, Sendable {
    /// Стабильный идентификатор источника, например `azkar.morning.3`.
    let id: String
    let url: URL
    /// Уже локализованные строки: нужны и в плеере, и на экране блокировки (Now Playing).
    let title: String
    let subtitle: String
    /// Название раздела над заголовком («АЗКАРЫ»).
    let category: String
}

/// Элемент очереди плейлиста: трек и сколько раз подряд его проиграть.
nonisolated struct AudioQueueItem: Hashable, Sendable {
    let track: AudioTrack
    let repeatCount: Int

    init(track: AudioTrack, repeatCount: Int) {
        self.track = track
        self.repeatCount = max(repeatCount, 1)
    }
}
