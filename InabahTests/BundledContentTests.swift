import AVFoundation
import Foundation
import Testing
@testable import Inabah

/// Реальные `data/*.json` из Bundle приложения: декодируются, номера идут подряд, есть базовый перевод.
@Suite("Контент в Bundle")
struct BundledContentTests {
    let repository = BundleContentRepository(languages: ContentLanguagePriority(languages: [.base]))

    @Test("Азкары раздела загружаются", arguments: [
        (AzkarSection.morning, 16),
        (AzkarSection.evening, 16),
    ])
    func azkarLoad(section: AzkarSection, expectedCount: Int) async throws {
        let azkar = try await repository.azkar(in: section)

        #expect(azkar.count == expectedCount)
        #expect(azkar.map(\.number) == Array(1...expectedCount))
        #expect(azkar.allSatisfy { $0.section == section })
        #expect(azkar.allSatisfy { $0.translation?.language == .base })
        #expect(azkar.allSatisfy { $0.repetitions >= 1 })
    }

    /// Свои записи (2026-10-12, `docs/process/audio.md`): у каждого зикра — файл в Bundle.
    @Test("Записи азкаров: у каждого зикра есть файл в Bundle", arguments: AzkarSection.allCases)
    @MainActor
    func everyZikrHasAudio(section: AzkarSection) async throws {
        let azkar = try await repository.azkar(in: section)

        for zikr in azkar {
            #expect(zikr.audioTrack() != nil, "\(section) \(zikr.number): нет файла \(zikr.audioFileName ?? "—")")
        }
    }

    /// Вечерний зикр, совпадающий с утренним, берёт его запись — только если арабский тот же
    /// (без огласовок: у утреннего и вечернего 7 знаки в «اللَّهُمَّ» набраны в другом порядке).
    @Test("Общая запись утреннего и вечернего — только при одинаковом арабском")
    func sharedAudioMatchesText() async throws {
        let all = try await repository.azkar(in: .morning) + repository.azkar(in: .evening)
        let shared = Dictionary(grouping: all) { $0.audioFileName ?? "" }.filter { $0.value.count > 1 }

        #expect(shared.count == 12)
        for (file, azkar) in shared {
            #expect(Set(azkar.map { Self.bare($0.arabic) }).count == 1, "\(file): разный арабский текст")
        }
    }

    @Test("Все записи — AAC .m4a и читаются плеером; MP3 нет")
    func audioFilesPlay() throws {
        let files = Bundle.main.urls(forResourcesWithExtension: "m4a", subdirectory: nil) ?? []

        #expect(files.count == 20)
        #expect(Bundle.main.urls(forResourcesWithExtension: "mp3", subdirectory: nil) ?? [] == [])
        for url in files {
            let player = try AVAudioPlayer(contentsOf: url)
            #expect(player.duration > 1, "\(url.lastPathComponent)")
        }
    }

    /// Арабский без огласовок и пробелов — для сравнения текстов.
    private static func bare(_ text: String) -> String {
        String(String.UnicodeScalarView(text.unicodeScalars.filter {
            !(0x064B...0x0652).contains($0.value) && $0.value != 0x0670 && !$0.properties.isWhitespace
        }))
    }

    @Test("Запись указана, но файла нет в Bundle — трека нет, а не падение")
    @MainActor
    func missingAudioFileGivesNoTrack() {
        let zikr = Zikr(
            id: ZikrID(section: .morning, number: 1),
            arabic: "سُبْحَانَ اللَّهِ",
            repetitions: 1,
            audioFileName: "morning_99.mp3",
            translation: nil
        )

        #expect(zikr.hasAudio)
        #expect(zikr.audioTrack() == nil)
    }

    @Test("Сборник хадисов загружается", arguments: [
        (HadithCollection.nawawi, 50),
        (HadithCollection.qudsi, 42),
        (HadithCollection.ajurri, 40),
    ])
    func hadithsLoad(collection: HadithCollection, expectedCount: Int) async throws {
        let hadiths = try await repository.hadiths(in: collection)

        #expect(hadiths.count == expectedCount)
        #expect(hadiths.map(\.number) == Array(1...expectedCount))
        #expect(hadiths.allSatisfy { $0.translation?.language == .base })
        #expect(hadiths.allSatisfy { !$0.arabic.isEmpty })
    }

    @Test("Сведения «О сборнике» есть у каждого сборника", arguments: HadithCollection.allCases)
    func collectionInfoLoads(collection: HadithCollection) async throws {
        let info = try await repository.collectionInfo(collection)

        #expect(info.language == .base)
        #expect(!info.about.isEmpty)
        // Къудси — своя подборка: составителей нет (заготовка с пустым именем не показывается).
        #expect(info.authors.isEmpty == (collection == .qudsi))
    }

    @Test("Тексты напоминаний: все виды на базовом языке")
    func reminderTextsLoad() async throws {
        let texts = try #require(try await repository.reminderTexts())

        #expect(Set(texts.azkarStart.keys) == Set(AzkarSection.allCases))
        #expect(Set(texts.azkarUnfinished.keys) == Set(AzkarSection.allCases))
        #expect(Set(texts.streakAtRisk.keys) == Set(AzkarSection.allCases))
        #expect(texts.streakAtRisk.values.allSatisfy { $0.title.contains("{streak}") })
        let hadith = try #require(texts.hadithOfDay)
        #expect(hadith.title.contains("{collection}"))
    }

    /// Сноски появляются во всех сборниках по мере правки данных (аль-Аджурри — 2026-10-11), поэтому
    /// проверяется разбор, а не то, у каких сборников они есть.
    @Test("Сноски у хадисов разбираются: у къудси есть, у всех — со словом и пояснением")
    func hadithNotesLoad() async throws {
        let qudsi = try await repository.hadiths(in: .qudsi)
        #expect(qudsi.contains { !($0.translation?.notes.isEmpty ?? true) })

        for collection in HadithCollection.allCases {
            let notes = try await repository.hadiths(in: collection).flatMap { $0.translation?.notes ?? [] }
            #expect(notes.allSatisfy { !$0.term.isEmpty && !$0.text.isEmpty }, "\(collection)")
        }
    }

    @Test("Служебные данные data/pending не попадают в Bundle")
    func pendingIsNotBundled() {
        #expect(Bundle.main.url(forResource: "missing-texts", withExtension: "json") == nil)
    }

    @Test("Текст лицензии для «О приложении» лежит в Bundle", arguments: LicenseDocument.allCases)
    func licenseIsBundled(document: LicenseDocument) throws {
        let text = try #require(document.text(), "нет файла \(document.resourceName).txt")
        #expect(!text.isEmpty)
    }

    @Test("Текст лицензии: строки абзаца склеиваются, пустые строки и линии остаются")
    func licenseReflow() {
        let source = "Copyright line one\nline two\n\n----\nTITLE\n----\n\nSecond\nparagraph"

        #expect(LicenseDocument.reflowed(source) == "Copyright line one line two\n\n----\nTITLE\n----\n\nSecond paragraph")
    }
}
