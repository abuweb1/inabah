import Foundation

/// Сведения о сборнике для «О сборнике» (`data/collections.json`, docs/06-data-structure.md) — уже
/// на выбранном языке. Название сборника — в приложении, число хадисов — длина его файла.
nonisolated struct CollectionInfo: Hashable, Sendable {
    /// Составители; заготовка с пустым именем сюда не попадает.
    let authors: [CollectionAuthor]
    /// Абзацы «О книге», без пустых.
    let about: [String]
    /// Язык выбранного перевода — для переносов в абзацах; `nil` — сведений нет.
    var language: ContentLanguage? = nil

    static let empty = CollectionInfo(authors: [], about: [])
}

nonisolated struct CollectionAuthor: Hashable, Sendable {
    let name: String
    /// «631–676 гг. х.»; `nil` — годы не указаны.
    let years: String?
}
