import Foundation

// Формат файлов `data/*.json` (описан в docs/06-data-structure.md).
// DTO повторяют JSON один в один; в доменные модели они превращаются в `ContentMapping`.

nonisolated struct AzkarFileDTO: Decodable, Sendable {
    let morning: [ZikrDTO]
    let evening: [ZikrDTO]

    func items(in section: AzkarSection) -> [ZikrDTO] {
        switch section {
        case .morning: morning
        case .evening: evening
        }
    }
}

nonisolated struct ZikrDTO: Decodable, Sendable {
    let id: Int
    let arabic: String
    let max: Int
    /// Имя аудиофайла в Bundle; поле необязательное — сейчас записей нет (с 2026-10-06).
    let audio: String?
    let translations: [String: ZikrTranslationDTO]
}

nonisolated struct ZikrTranslationDTO: Decodable, Sendable {
    let text: String
    let translit: String?
    let source: String?
}

nonisolated struct HadithDTO: Decodable, Sendable {
    let id: Int
    let arabic: String
    let translations: [String: HadithTranslationDTO]
}

nonisolated struct HadithTranslationDTO: Decodable, Sendable {
    let rawi: String?
    let text: String
    let source: String?
}
