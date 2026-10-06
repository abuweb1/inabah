import Foundation

extension Zikr {
    /// Идентификатор записи в плеере: стабилен между запусками, уникален среди всех разделов.
    var audioTrackID: AudioTrack.ID { "azkar.\(section.rawValue).\(number)" }

    /// Есть ли у зикра запись — иначе вместо ▶ и «Прослушать все» заглушка «Аудио скоро».
    nonisolated var hasAudio: Bool { audioFileName != nil }

    /// Запись зикра из Bundle; `nil`, если записи нет или файла нет в Bundle.
    func audioTrack(in bundle: Bundle = .main) -> AudioTrack? {
        // Имя с расширением целиком («morning_03.mp3») — Bundle ищет его как есть.
        guard let audioFileName, let url = bundle.url(forResource: audioFileName, withExtension: nil) else {
            return nil
        }
        return AudioTrack(
            id: audioTrackID,
            url: url,
            title: String(localized: "audio.zikr.title \(number)"),
            subtitle: String(localized: section.title),
            category: String(localized: "audio.category.azkar")
        )
    }
}

extension AzkarSection {
    /// Идентификатор плейлиста «Прослушать все» раздела.
    var playlistID: String { "azkar.\(rawValue)" }
}
