import Foundation

/// Сборник хадисов. `rawValue` — имя файла в `data/` и часть ключей прогресса.
nonisolated enum HadithCollection: String, CaseIterable, Hashable, Codable, Sendable {
    case nawawi
    case qudsi
    case ajurri
}

nonisolated struct HadithID: Hashable, Codable, Sendable {
    let collection: HadithCollection
    let number: Int
}

/// Хадис с уже выбранным переводом.
nonisolated struct Hadith: Identifiable, Hashable, Sendable {
    let id: HadithID
    /// Арабский текст; может содержать `\n` (в ан-Навави — переносы строк исходного издания).
    let arabic: String
    let translation: HadithTranslation?

    var collection: HadithCollection { id.collection }
    var number: Int { id.number }
}

nonisolated struct HadithTranslation: Hashable, Sendable {
    let language: ContentLanguage
    /// Передатчик на языке перевода; допускается цепочка через «—».
    let narrator: String?
    let text: String
    let source: String?
}
