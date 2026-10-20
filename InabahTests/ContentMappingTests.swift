import Foundation
import Testing
@testable import Inabah

/// DTO → доменные модели: необязательные поля, пустые значения, выбор языка (как `ContentMappingTest` на Android).
@Suite("Разбор контента")
struct ContentMappingTests {
    let russian = ContentLanguagePriority(languages: [.base])

    private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try JSONDecoder().decode(type, from: Data(json.utf8))
    }

    @Test("«Передал» у зикра: обрезается, пустое и отсутствующее — nil", arguments: [
        (#""rawi": "  Абу Хурайра ""#, "Абу Хурайра" as String?),
        (#""rawi": "   ""#, nil),
        (#""source": "Бухари""#, nil),
    ])
    func zikrNarrator(field: String, expected: String?) throws {
        let dto = try decode(ZikrDTO.self, #"{ "id": 1, "arabic": "ا", "max": 1, "translations": { "ru": { "text": "т", "# + field + " } } }")

        let zikr = Zikr(dto: dto, section: .morning, languages: russian)

        #expect(zikr.translation?.narrator == expected)
    }

    @Test("Сноски: порядок файла, пустые отбрасываются; [] и нет поля — без сносок")
    func hadithNotes() throws {
        let filled = try decode(HadithDTO.self, """
            { "id": 1, "arabic": "ا", "translations": { "ru": { "text": "т", "notes": [
              { "term": " иман ", "text": " вера " },
              { "term": "", "text": "без слова" },
              { "term": "ислам", "text": "  " },
              { "term": "ихсан", "text": "совершенство" }
            ] } } }
            """)
        let empty = try decode(HadithDTO.self, #"{ "id": 1, "arabic": "ا", "translations": { "ru": { "text": "т", "notes": [] } } }"#)
        let missing = try decode(HadithDTO.self, #"{ "id": 1, "arabic": "ا", "translations": { "ru": { "text": "т" } } }"#)

        let notes = Hadith(dto: filled, collection: .nawawi, languages: russian).translation?.notes

        #expect(notes == [HadithNote(term: "иман", text: "вера"), HadithNote(term: "ихсан", text: "совершенство")])
        #expect(Hadith(dto: empty, collection: .nawawi, languages: russian).translation?.notes == [])
        #expect(Hadith(dto: missing, collection: .nawawi, languages: russian).translation?.notes == [])
    }

    @Test("«О сборнике»: заготовка автора и пустые годы и абзацы отбрасываются")
    func collectionInfo() throws {
        let dto = try decode(CollectionInfoDTO.self, """
            { "translations": { "ru": {
              "authors": [ { "name": "ан-Навави", "years": " 631–676 гг. х. " }, { "name": "Ибн Раджаб", "years": "" }, { "name": "", "years": "" } ],
              "about": [ " Первый ", "", "Второй" ] } } }
            """)

        let info = CollectionInfo(dto: dto, languages: russian)

        #expect(info.authors == [
            CollectionAuthor(name: "ан-Навави", years: "631–676 гг. х."),
            CollectionAuthor(name: "Ибн Раджаб", years: nil),
        ])
        #expect(info.about == ["Первый", "Второй"])
        #expect(info.language == .base)
    }

    @Test("«О сборнике» без подходящего языка — пусто")
    func collectionInfoWithoutLanguage() throws {
        let dto = try decode(CollectionInfoDTO.self, #"{ "translations": { "en": { "about": [ "About" ] } } }"#)

        #expect(CollectionInfo(dto: dto, languages: russian) == .empty)
    }

    @Test("Сборника нет в файле — пусто, а не ошибка")
    func missingCollectionInfo() async throws {
        let repository: any ContentRepository = InMemoryContentRepository()

        #expect(try await repository.collectionInfo(.qudsi) == .empty)
    }

    @Test("Тексты напоминаний: пустой вид не задан, подстановки заполняются и текст обрезается")
    func reminderTexts() throws {
        let dto = try decode(RemindersFileDTO.self, """
            { "translations": { "ru": {
              "azkarStart": { "morning": { "title": "Утро", "body": "" }, "evening": { "title": " ", "body": "" } },
              "hadithOfDay": { "title": "Прочти хадис · {collection}, № {number}", "body": "{text} " },
              "readingLesson": { "title": "Пора на урок", "body": "{step}" } } } }
            """)

        let texts = try #require(ReminderTexts(dto: dto, languages: russian))
        let hadith = try #require(texts.hadithOfDay)

        #expect(texts.azkarStart[.morning] == ReminderText(title: "Утро", body: ""))
        #expect(texts.azkarStart[.evening] == nil)
        #expect(texts.azkarUnfinished.isEmpty)
        #expect(hadith.filled([.collection: "40 хадисов ан-Навави", .number: "7", .text: ""])
            == ReminderText(title: "Прочти хадис · 40 хадисов ан-Навави, № 7", body: ""))
        #expect(texts.readingLesson?.filled([.step: "Шаг 7 · Сукун"]) == ReminderText(title: "Пора на урок", body: "Шаг 7 · Сукун"))
    }

    @Test("Тексты напоминаний без подходящего языка — nil")
    func reminderTextsWithoutLanguage() throws {
        let dto = try decode(RemindersFileDTO.self, #"{ "translations": { "en": { "hadithOfDay": { "title": "Hadith" } } } }"#)

        #expect(ReminderTexts(dto: dto, languages: russian) == nil)
    }
}
