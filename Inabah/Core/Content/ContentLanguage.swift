import Foundation

/// Язык перевода контента — код ISO 639-1, как ключи `translations` в `data/*.json`.
nonisolated struct ContentLanguage: RawRepresentable, Hashable, Sendable {
    let rawValue: String

    init(rawValue: String) {
        self.rawValue = rawValue.lowercased()
    }

    /// Базовый язык: перевод на нём обязателен у каждой записи (проверяет `check-data`).
    static let base = ContentLanguage(rawValue: "ru")
}

/// Порядок, в котором выбирается перевод записи: языки пользователя, затем базовый.
///
/// Перевод выбирается для каждой записи отдельно — частично переведённая коллекция
/// показывается на языке пользователя там, где перевод есть, и на базовом языке остальное.
nonisolated struct ContentLanguagePriority: Hashable, Sendable {
    let languages: [ContentLanguage]

    init(languages: [ContentLanguage]) {
        var unique: [ContentLanguage] = []
        for language in languages + [.base] where !unique.contains(language) {
            unique.append(language)
        }
        self.languages = unique
    }

    /// Приоритет по системным настройкам языка (`Locale.preferredLanguages`: "en-US", "ru-RU"…).
    init(preferredLanguages: [String]) {
        self.init(languages: preferredLanguages.compactMap { identifier in
            Locale.Language(identifier: identifier).languageCode.map {
                ContentLanguage(rawValue: $0.identifier)
            }
        })
    }

    static var system: ContentLanguagePriority {
        ContentLanguagePriority(preferredLanguages: Locale.preferredLanguages)
    }

    /// Первый доступный перевод по приоритету.
    func select<Translation>(
        from translations: [ContentLanguage: Translation]
    ) -> (language: ContentLanguage, translation: Translation)? {
        for language in languages {
            if let translation = translations[language] {
                return (language, translation)
            }
        }
        return nil
    }
}
