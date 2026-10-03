import Foundation

extension Zikr {
    /// Идентификатор записи в плеере: стабилен между запусками, уникален среди всех разделов.
    var audioTrackID: AudioTrack.ID { "azkar.\(section.rawValue).\(number)" }

    /// Запись зикра из Bundle; `nil`, если файла нет.
    func audioTrack(in bundle: Bundle = .main) -> AudioTrack? {
        // Имя с расширением целиком («morning_03.mp3») — Bundle ищет его как есть.
        guard let url = bundle.url(forResource: audioFileName, withExtension: nil) else {
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
