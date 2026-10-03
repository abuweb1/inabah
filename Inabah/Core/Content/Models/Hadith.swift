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
///
/// Заготовка этапа хадисов: сейчас слой (модель, DTO, `ContentRepository.hadiths(in:)`)
/// используется только тестами данных — экраны хадисов появятся на этапе 2.
nonisolated struct Hadith: Identifiable, Hashable, Sendable {
    let id: HadithID
    /// Арабский текст; строки иснада разделены `\n`.
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
