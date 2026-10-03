import Testing
@testable import Inabah

@Suite("Выбор языка контента")
struct ContentLanguageTests {
    @Test("Базовый язык всегда последний и не дублируется", arguments: [
        (["en-US", "ru-RU"], ["en", "ru"]),
        (["ru-RU", "en"], ["ru", "en"]),
        (["de-DE", "de-AT"], ["de", "ru"]),
        ([], ["ru"]),
    ])
    func priorityFromSystemLanguages(preferred: [String], expected: [String]) {
        let priority = ContentLanguagePriority(preferredLanguages: preferred)
        #expect(priority.languages.map(\.rawValue) == expected)
    }

    @Test("Берётся первый доступный перевод по приоритету")
    func selectsFirstAvailable() throws {
        let priority = ContentLanguagePriority(languages: [ContentLanguage(rawValue: "en"), .base])
        let translations = [ContentLanguage.base: "Русский", ContentLanguage(rawValue: "en"): "English"]

        let selected = try #require(priority.select(from: translations))

        #expect(selected.language.rawValue == "en")
        #expect(selected.translation == "English")
    }

    @Test("Без перевода на языке пользователя — базовый язык")
    func fallsBackToBase() throws {
        let priority = ContentLanguagePriority(preferredLanguages: ["fr-FR"])
        let selected = try #require(priority.select(from: [ContentLanguage.base: "Русский"]))
        #expect(selected.language == .base)
    }

    @Test("Нет ни одного подходящего перевода")
    func noMatchingTranslation() {
        let priority = ContentLanguagePriority(languages: [.base])
        #expect(priority.select(from: [ContentLanguage(rawValue: "en"): "English"]) == nil)
    }

    @Test("Код языка нормализуется к нижнему регистру")
    func normalizesCase() {
        #expect(ContentLanguage(rawValue: "RU") == .base)
    }
}
