import AVFoundation
import CoreText
import Foundation
import Testing
@testable import Inabah

/// Реальные `data/*.json` из Bundle приложения: декодируются, номера идут подряд, есть базовый перевод.
@Suite("Контент в Bundle")
struct BundledContentTests {
    let repository = BundleContentRepository(languages: ContentLanguagePriority(languages: [.base]))

    /// Записаны первые 16 зикров раздела; новые (с 2026-10-24, утро 17-21, вечер 17-22) - пока без записи.
    private static let recordedAzkar = 16

    @Test("Азкары раздела загружаются", arguments: [
        (AzkarSection.morning, 21),
        (AzkarSection.evening, 22),
    ])
    func azkarLoad(section: AzkarSection, expectedCount: Int) async throws {
        let azkar = try await repository.azkar(in: section)

        #expect(azkar.count == expectedCount)
        #expect(azkar.map(\.number) == Array(1...expectedCount))
        #expect(azkar.allSatisfy { $0.section == section })
        #expect(azkar.allSatisfy { $0.translation?.language == .base })
        #expect(azkar.allSatisfy { $0.repetitions >= 1 })
    }

    /// Свои записи (2026-10-12, `docs/process/audio.md`): у записанных зикров — файл в Bundle, у новых — записи нет.
    @Test("Записи азкаров: у записанных зикров есть файл в Bundle", arguments: AzkarSection.allCases)
    @MainActor
    func everyZikrHasAudio(section: AzkarSection) async throws {
        let azkar = try await repository.azkar(in: section)

        for zikr in azkar.prefix(Self.recordedAzkar) {
            #expect(zikr.audioTrack() != nil, "\(section) \(zikr.number): нет файла \(zikr.audioFileName ?? "—")")
        }
        for zikr in azkar.dropFirst(Self.recordedAzkar) {
            #expect(zikr.audioTrack() == nil, "\(section) \(zikr.number): запись появилась - поправить recordedAzkar")
        }
    }

    /// Вечерний зикр, совпадающий с утренним, берёт его запись — только если арабский тот же
    /// (без огласовок: у утреннего и вечернего 7 знаки в «اللَّهُمَّ» набраны в другом порядке).
    @Test("Общая запись утреннего и вечернего — только при одинаковом арабском")
    func sharedAudioMatchesText() async throws {
        let all = try await repository.azkar(in: .morning) + repository.azkar(in: .evening)
        let recorded = all.filter(\.hasAudio)
        let shared = Dictionary(grouping: recorded) { $0.audioFileName ?? "" }.filter { $0.value.count > 1 }

        #expect(shared.count == 12)
        for (file, azkar) in shared {
            #expect(Set(azkar.map { Self.bare($0.arabic) }).count == 1, "\(file): разный арабский текст")
        }
    }

    /// Суры и аяты Корана (`quran: true`, 2026-10-25): аль-Ихлас, аль-Фалак, ан-Нас, аят аль-Курси, «Амана-р-расулю».
    @Test("Суры и аяты Корана отмечены, обычные зикры — нет")
    func quranAzkarMarked() async throws {
        let morning = try await repository.azkar(in: .morning)
        let evening = try await repository.azkar(in: .evening)

        #expect(morning.filter(\.isQuran).map(\.number) == [14, 15, 16, 17])
        #expect(evening.filter(\.isQuran).map(\.number) == [13, 14, 15, 16, 17])
    }

    /// Набор по умолчанию — прежние 16 + 16 (2026-10-24): новые зикры добавляются в «Составе азкаров».
    @Test("Новые зикры — не в наборе по умолчанию")
    func newAzkarOptional() async throws {
        let morning = try await repository.azkar(in: .morning)
        let evening = try await repository.azkar(in: .evening)

        #expect(morning.filter(\.isOptional).map(\.number) == Array(17...21))
        #expect(evening.filter(\.isOptional).map(\.number) == Array(17...22))
    }

    /// Карточка рисует их шрифтом мусхафа: символа без глифа там быть не должно — иначе подставится
    /// системный шрифт посреди аята. Неразрывный пробел перед номером аята (так в тексте QPC) в Hafs
    /// не нарисован, но он невидимый — как на Android (`FontCoverageTest`).
    @Test("Суры и аяты Корана покрыты шрифтом мусхафа")
    func quranAzkarCoveredByMushafFont() async throws {
        AppFont.registerBundledFonts()
        let font = CTFontCreateWithName(AppFont.mushaf as CFString, 20, nil)
        #expect(CTFontCopyPostScriptName(font) as String == AppFont.mushaf)

        let azkar = try await repository.azkar(in: .morning) + repository.azkar(in: .evening)
        let text = azkar.filter(\.isQuran).map(\.arabic).joined(separator: "\n")
        #expect(!text.isEmpty)

        let scalars = Set(text.unicodeScalars).filter {
            !$0.properties.isWhitespace && $0.properties.generalCategory != .format
        }
        let missing = scalars.filter { scalar in
            var units = Array(String(scalar).utf16)
            var glyphs = [CGGlyph](repeating: 0, count: units.count)
            return !CTFontGetGlyphsForCharacters(font, &units, &glyphs, units.count)
        }
        #expect(missing.isEmpty, "нет в Hafs: \(missing.map { String(format: "U+%04X", $0.value) }.sorted())")
    }

    @Test("Все записи — AAC .m4a и читаются плеером; MP3 нет; азкаров — 20")
    func audioFilesPlay() throws {
        let files = Bundle.main.urls(forResourcesWithExtension: "m4a", subdirectory: nil) ?? []
        // Записи курса «Учимся читать» (`reading_*`) - в том же Bundle, но не азкары.
        let azkar = files.filter { url in ["morning_", "evening_"].contains { url.lastPathComponent.hasPrefix($0) } }

        #expect(azkar.count == 20)
        #expect(Bundle.main.urls(forResourcesWithExtension: "mp3", subdirectory: nil) ?? [] == [])
        for url in files {
            let player = try AVAudioPlayer(contentsOf: url)
            // Зикр длиннее секунды; слог курса короче (0,8-0,9 с) - достаточно, что запись не пустая.
            let isAzkar = azkar.contains(url)
            #expect(player.duration > (isAzkar ? 1 : 0), "\(url.lastPathComponent)")
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
        let lesson = try #require(texts.readingLesson)
        #expect(lesson.title == "Пора на урок")
        #expect(lesson.body.contains("{step}"))
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
