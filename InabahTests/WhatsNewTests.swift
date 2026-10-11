import Foundation
import Testing
@testable import Inabah

/// «Что нового» и «История версий» — как на Android (`WhatsNewDecisionTest`, `ReleaseNotesSourceTest`,
/// `WhatsNewTest`).
@Suite("Номер версии")
struct AppVersionNumberTests {
    @Test("Части сравниваются числами")
    func numericOrder() throws {
        let v = { (text: String) in try #require(AppVersionNumber(text)) }
        #expect(try v("1.10.0") > v("1.9.0"))
        #expect(try v("2.0.0") > v("1.99.99"))
        #expect(try v("1.01.0") == v("1.1.0"))
        #expect(try v("1.01.0").hashValue == v("1.1.0").hashValue)
        #expect(try v("1.1.1").text == "1.1.1")
    }

    @Test("Не версия", arguments: [nil, "", "1.1", "1.1.1.1", "v1.1.1", "1.1.1-beta", "1.a.1", " 1.1.1", "1..1", "99999999999999999999.0.0"])
    func invalid(text: String?) {
        #expect(AppVersionNumber(text) == nil)
    }
}

@Suite("Решение «Что нового»")
struct WhatsNewDecisionTests {
    private static let v110 = AppVersionNumber("1.1.0")
    private static let v111 = AppVersionNumber("1.1.1")!
    private static let v120 = AppVersionNumber("1.2.0")

    @Test("Правила по порядку", arguments: [
        // Новая установка без ключа — тихо, даже если пункты есть.
        (nil as AppVersionNumber?, true, true, WhatsNewDecision.recordSilently),
        // Ключа нет, но приложение открывали (обновление с 1.1.0) — показать.
        (nil, false, true, .show),
        (nil, false, false, .recordSilently),
        // Версия выросла.
        (v110, false, true, .show),
        (v110, false, false, .recordSilently),
        // Ключ есть — «новая установка» не важна.
        (v110, true, true, .show),
        // Та же версия или откат — ничего.
        (v111, false, true, .nothing),
        (v120, false, true, .nothing),
    ])
    func decide(lastShown: AppVersionNumber?, isFreshInstall: Bool, hasNotes: Bool, expected: WhatsNewDecision) {
        #expect(WhatsNewDecision.decide(lastShown: lastShown, current: Self.v111, isFreshInstall: isFreshInstall, hasNotes: hasNotes) == expected)
    }
}

@Suite("История версий: разбор versions.json")
struct ReleaseNotesParsingTests {
    private static func file(_ json: String) throws -> VersionsFileDTO {
        try JSONDecoder().decode(VersionsFileDTO.self, from: Data(json.utf8))
    }

    @Test("Пункты: общие, затем iOS; Android не читается; края обрезаются, пустые — вон")
    func iosItems() throws {
        let dto = try Self.file("""
        {"versions":[{"version":"1.1.1","date":"2026-10-12","translations":{"ru":{
          "items":[" Общее ",""],"ios":["Только iOS"],"android":["Только Android"]}}}]}
        """)
        let notes = ReleaseNote.notes(from: dto, languages: ContentLanguagePriority(languages: [.base]))

        #expect(notes.map(\.items) == [["Общее", "Только iOS"]])
        #expect(notes.first?.version.text == "1.1.1")
    }

    @Test("Язык — по приоритету, нет перевода — запасной ru")
    func language() throws {
        let dto = try Self.file("""
        {"versions":[{"version":"1.1.1","date":"2026-10-12","translations":{"ru":{"items":["Новое"]},"en":{"items":["New"]}}}]}
        """)
        let english = ReleaseNote.notes(from: dto, languages: ContentLanguagePriority(languages: [ContentLanguage(rawValue: "en")]))
        let german = ReleaseNote.notes(from: dto, languages: ContentLanguagePriority(languages: [ContentLanguage(rawValue: "de")]))

        #expect(english.first?.items == ["New"])
        #expect(german.first?.items == ["Новое"])
    }

    @Test("Негодные записи пропускаются, остальные остаются в порядке файла")
    func skipsBroken() throws {
        let dto = try Self.file("""
        {"versions":[
          {"version":"1.2","date":"2026-10-12","translations":{"ru":{"items":["А"]}}},
          {"version":"1.1.2","date":"2026-02-30","translations":{"ru":{"items":["Б"]}}},
          {"version":"1.1.1","date":"2026-10-12","translations":{"ru":{"items":[" "]}}},
          {"version":"1.1.0","date":"2026-10-08","translations":{"en":{"items":["В"]}}},
          {"version":"1.0.1","date":"2026-10-05","translations":{"ru":{"items":["Г"]}}},
          {"version":"1.0.0","date":"2026-10-01","translations":{"ru":{"items":["Д"]}}}
        ]}
        """)
        let notes = ReleaseNote.notes(from: dto, languages: ContentLanguagePriority(languages: [.base]))

        // Неверная версия, несуществующий день, пустые пункты, перевод только на недоступном языке.
        #expect(notes.map(\.version.text) == ["1.0.1", "1.0.0"])
        #expect(notes.first?.items == ["Г"])
    }

    @Test("Настоящий файл: есть записи по убыванию и запись текущей версии")
    func bundledFile() async throws {
        let notes = try await BundleContentRepository(languages: ContentLanguagePriority(languages: [.base])).releaseNotes()

        try #require(!notes.isEmpty)
        #expect(zip(notes, notes.dropFirst()).allSatisfy { $0.version > $1.version })
        #expect(notes.allSatisfy { !$0.items.isEmpty })
        #expect(notes.contains { $0.version == AppVersionNumber(AppVersion.marketing) })
    }
}

@MainActor
@Suite("«Что нового»: показ один раз")
struct WhatsNewTests {
    let storage: IsolatedDefaults
    var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("WhatsNewTests")
    }

    private static let note = ReleaseNote(
        version: AppVersionNumber("1.1.1")!,
        date: .now,
        items: ["Новое название — Inabah"],
        language: .base
    )

    private func whatsNew(version: String = "1.1.1", notes: [ReleaseNote] = [note]) -> WhatsNew {
        WhatsNew(defaults: defaults, repository: InMemoryContentRepository(notes: notes), currentVersion: version)
    }

    /// Как после 1.1.0: прогресс азкаров записан, ключа «Что нового» нет.
    private func simulateUpdateFrom110() {
        defaults.set(Data("{}".utf8), forKey: "azkar.progress.morning")
    }

    @Test("Обновление с 1.1.0 — окно; «ОК» записывает версию, после перезапуска окна нет")
    func showsOnceAfterUpdate() async {
        simulateUpdateFrom110()
        let first = whatsNew()
        await first.check()
        #expect(first.pending == Self.note)

        first.dismiss()

        #expect(first.pending == nil)
        #expect(defaults.string(forKey: WhatsNew.lastVersionKey) == "1.1.1")
        let second = whatsNew()
        await second.check()
        #expect(second.pending == nil)
    }

    @Test("Не закрыли — при следующем запуске окно снова")
    func showsAgainUntilDismissed() async {
        simulateUpdateFrom110()
        await whatsNew().check()

        let next = whatsNew()
        await next.check()

        #expect(next.pending == Self.note)
        #expect(defaults.string(forKey: WhatsNew.lastVersionKey) == nil)
    }

    @Test("Новая установка — без окна, версия записана; следующая версия без пунктов — тихо")
    func freshInstallRecordsSilently() async {
        let fresh = whatsNew()
        await fresh.check()
        #expect(fresh.pending == nil)
        #expect(defaults.string(forKey: WhatsNew.lastVersionKey) == "1.1.1")

        let next = whatsNew(version: "1.2.0")
        await next.check()
        #expect(next.pending == nil)
        #expect(defaults.string(forKey: WhatsNew.lastVersionKey) == "1.2.0")
    }

    @Test("Испорченный ключ — как будто ключа нет: показать")
    func corruptKeyShows() async {
        defaults.set("мусор", forKey: WhatsNew.lastVersionKey)
        let whatsNew = whatsNew()
        await whatsNew.check()
        #expect(whatsNew.pending == Self.note)
    }

    @Test("Отладочная версия не X.Y.Z — ни окна, ни записи")
    func debugVersionDoesNothing() async {
        simulateUpdateFrom110()
        let whatsNew = whatsNew(version: "1.1.1-debug")
        await whatsNew.check()
        #expect(whatsNew.pending == nil)
        #expect(defaults.object(forKey: WhatsNew.lastVersionKey) == nil)
    }

    @Test("История версий: не загрузилась — пусто")
    func historyFailure() async {
        let whatsNew = WhatsNew(
            defaults: defaults,
            repository: InMemoryContentRepository(error: .resourceMissing("versions.json")),
            currentVersion: "1.1.1"
        )
        #expect(await whatsNew.history().isEmpty)
    }
}
