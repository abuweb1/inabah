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

    @Test("Аудиофайл зикра лежит в Bundle", arguments: AzkarSection.allCases)
    func azkarAudioIsBundled(section: AzkarSection) async throws {
        let azkar = try await repository.azkar(in: section)
        let missing = azkar.filter { zikr in
            let name = (zikr.audioFileName as NSString).deletingPathExtension
            return Bundle.main.url(forResource: name, withExtension: "mp3") == nil
        }
        #expect(missing.isEmpty, "Нет аудио: \(missing.map(\.audioFileName))")
    }

    @Test("Сборник хадисов загружается", arguments: [
        (HadithCollection.nawawi, 50),
        (HadithCollection.qudsi, 40),
        (HadithCollection.ajurri, 40),
    ])
    func hadithsLoad(collection: HadithCollection, expectedCount: Int) async throws {
        let hadiths = try await repository.hadiths(in: collection)

        #expect(hadiths.count == expectedCount)
        #expect(hadiths.map(\.number) == Array(1...expectedCount))
        #expect(hadiths.allSatisfy { $0.translation?.language == .base })
        #expect(hadiths.allSatisfy { !$0.arabic.isEmpty })
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
