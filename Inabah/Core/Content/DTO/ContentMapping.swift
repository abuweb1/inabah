import Foundation

nonisolated extension Zikr {
    init(dto: ZikrDTO, section: AzkarSection, languages: ContentLanguagePriority) {
        self.init(
            id: ZikrID(section: section, number: dto.id),
            arabic: dto.arabic,
            repetitions: Swift.max(dto.max, 1),
            audioFileName: dto.audio,
            translation: languages.select(from: dto.translations.keyedByLanguage).map { language, t in
                ZikrTranslation(
                    language: language,
                    text: t.text,
                    transliteration: t.translit.nonEmpty,
                    source: t.source.nonEmpty
                )
            }
        )
    }
}

nonisolated extension Hadith {
    init(dto: HadithDTO, collection: HadithCollection, languages: ContentLanguagePriority) {
        self.init(
            id: HadithID(collection: collection, number: dto.id),
            arabic: dto.arabic,
            translation: languages.select(from: dto.translations.keyedByLanguage).map { language, t in
                HadithTranslation(
                    language: language,
                    narrator: t.rawi.nonEmpty,
                    text: t.text,
                    source: t.source.nonEmpty
                )
            }
        )
    }
}

private nonisolated extension Dictionary where Key == String {
    var keyedByLanguage: [ContentLanguage: Value] {
        Dictionary<ContentLanguage, Value>(
            map { (ContentLanguage(rawValue: $0.key), $0.value) },
            uniquingKeysWith: { first, _ in first }
        )
    }
}

private nonisolated extension Optional where Wrapped == String {
    /// Пустые строки в данных означают «не заполнено» (например, `source` ждёт уточнения).
    var nonEmpty: String? {
        guard let trimmed = self?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
