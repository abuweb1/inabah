import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Текст «Поделиться»")
struct ShareTextTests {
    /// `nonisolated` — читается и в аргументах параметризованного теста.
    private nonisolated static let link = "https://apps.apple.com/app/id6819638881"

    private static func zikr(translation: ZikrTranslation?) -> Zikr {
        Zikr(
            id: ZikrID(section: .morning, number: 1),
            arabic: "سُبْحَانَ اللَّهِ",
            repetitions: 100,
            audioFileName: "morning_01.mp3",
            translation: translation
        )
    }

    private static func hadith(narrator: String?, text: String?, source: String?) -> Hadith {
        Hadith(
            id: HadithID(collection: .nawawi, number: 2),
            arabic: "عَنْ عُمَرَ\nقَالَ",
            translation: text.map {
                HadithTranslation(language: .base, narrator: narrator, text: $0, source: source)
            }
        )
    }

    @Test("Блоки: пустые и отсутствующие пропускаются, края обрезаются")
    func blocksSkipEmpty() {
        #expect(ShareText.blocks("  А\n", nil, "", " \n ", "Б") == "А\n\nБ")
        #expect(ShareText.blocks(nil, nil) == "")
    }

    @Test("Зикр с переводом: раздел, арабский, «N раз», транскрипция, перевод, источник, ссылка")
    func zikrWithTranslation() {
        let zikr = Self.zikr(translation: ZikrTranslation(
            language: .base, text: "Пречист Аллах", transliteration: "Субхана-Ллах", source: "(Муслим)"
        ))

        #expect(zikr.shareText.components(separatedBy: ShareText.blockSeparator) == [
            "Утренние азкары", "سُبْحَانَ اللَّهِ", "100 раз", "Субхана-Ллах", "Пречист Аллах", "(Муслим)", Self.link,
        ])
    }

    @Test("Зикр без перевода: раздел, арабский, «N раз», ссылка")
    func zikrWithoutTranslation() {
        #expect(Self.zikr(translation: nil).shareText.components(separatedBy: ShareText.blockSeparator) == [
            "Утренние азкары", "سُبْحَانَ اللَّهِ", "100 раз", Self.link,
        ])
    }

    @Test("Хадис полностью: заголовок, арабский одним абзацем, передатчик, перевод, источник, ссылка")
    func fullHadith() {
        let hadith = Self.hadith(narrator: "Умар", text: "Перевод", source: "Муслим")

        #expect(hadith.shareText.components(separatedBy: ShareText.blockSeparator) == [
            "40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", "Передал: Умар", "Перевод", "Приводится: Муслим", Self.link,
        ])
    }

    @Test("Напоминание: заголовок, призыв, слова Абу ад-Дарды в 4 строки, черта со временем, ссылка")
    func morningReminder() throws {
        let blocks = AzkarSection.morning.reminderText.components(separatedBy: ShareText.blockSeparator)

        try #require(blocks.count == 5)
        #expect(blocks[0] == "‼️ Утренние азкары ‼️")
        #expect(blocks[1].hasSuffix("прочитайте утренние азкары."))
        #expect(blocks[2].components(separatedBy: "\n") == [
            "Абу ад-Дарда сказал:",
            AzkarSection.abuAdDardaArabic,
            "«Те, чьи языки влажные от поминания Аллаха, войдут в Рай смеясь».",
            "Ахмад в «аз-Зухд», 726",
        ])
        #expect(blocks[3] == AzkarSection.reminderSeparator + "\n"
            + "❗ Лучшее время для чтения утренних азкаров — с Фаджра до восхода солнца.")
        #expect(blocks[4] == Self.link)
    }

    @Test("Напоминание вечерних — только про вечерние")
    func eveningReminder() {
        let text = AzkarSection.evening.reminderText

        #expect(text.hasPrefix("‼️ Вечерние азкары ‼️"))
        #expect(text.contains("— с Асра до Магриба."))
        #expect(!text.contains("утренние"))
        #expect(!text.contains("*"))
    }

    @Test("Хадис без части блоков — пропущенные не оставляют пустых строк", arguments: [
        (nil as String?, "Перевод" as String?, "Муслим" as String?,
         ["40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", "Перевод", "Приводится: Муслим", link]),
        ("Умар", "Перевод", nil,
         ["40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", "Передал: Умар", "Перевод", link]),
        (nil, nil, nil,
         ["40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", link]),
    ])
    func partialHadith(narrator: String?, text: String?, source: String?, expected: [String]) {
        let shareText = Self.hadith(narrator: narrator, text: text, source: source).shareText

        #expect(shareText.components(separatedBy: ShareText.blockSeparator) == expected)
        #expect(!shareText.contains("\n\n\n"))
    }
}
