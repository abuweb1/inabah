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

    @Test("Зикр с передатчиком: как в данных, без «Передал:» — после «N раз», перед транскрипцией")
    func zikrWithNarrator() {
        let zikr = Self.zikr(translation: ZikrTranslation(
            language: .base, text: "Пречист Аллах", transliteration: "Субхана-Ллах", source: nil, narrator: "Абу Хурайра"
        ))

        #expect(zikr.shareText.components(separatedBy: ShareText.blockSeparator) == [
            "Утренние азкары", "سُبْحَانَ اللَّهِ", "100 раз", "Абу Хурайра", "Субхана-Ллах", "Пречист Аллах", Self.link,
        ])
    }

    @Test("Зикр без перевода: раздел, арабский, «N раз», ссылка")
    func zikrWithoutTranslation() {
        #expect(Self.zikr(translation: nil).shareText.components(separatedBy: ShareText.blockSeparator) == [
            "Утренние азкары", "سُبْحَانَ اللَّهِ", "100 раз", Self.link,
        ])
    }

    @Test("Хадис полностью: заголовок, арабский одним абзацем, вступление без «Передал:», перевод, источник, ссылка")
    func fullHadith() {
        let hadith = Self.hadith(narrator: "Передаётся от Умара, что он сказал:", text: "Перевод", source: "Муслим")

        #expect(hadith.shareText.components(separatedBy: ShareText.blockSeparator) == [
            "40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", "Передаётся от Умара, что он сказал:", "Перевод",
            "Приводится: Муслим", Self.link,
        ])
    }

    @Test("Сноски — после источника: «Комментарии учёных:», слово и пояснение на двух строках, между сносками пустая строка")
    func hadithWithNotes() {
        let hadith = Hadith(
            id: HadithID(collection: .nawawi, number: 2),
            arabic: "عَنْ عُمَرَ",
            translation: HadithTranslation(
                language: .base, narrator: nil, text: "Перевод", source: "Муслим",
                notes: [HadithNote(term: "иман", text: "вера"), HadithNote(term: "Различия передач", text: "у Муслима иначе")]
            )
        )

        #expect(hadith.shareText.hasSuffix("""
            Приводится: Муслим

            Комментарии учёных:
            иман
            вера

            Различия передач
            у Муслима иначе

            \(Self.link)
            """))
    }

    @Test("Напоминание утренних — текст пользователя целиком (docs/02-screen-azkar.md, «Новый вид»)")
    func morningReminder() {
        let dots = String(repeating: "•", count: 30)

        #expect(AzkarSection.reminderSeparator == dots)
        #expect(AzkarSection.morning.reminderText == """
            ⚠️ Утренние азкары ⚠️
            \(dots)
            О рабы Аллаха, защитите себя и свою семью от сглаза, зависти и колдовства: прочитайте утренние азкары.
            \(dots)

            Абу ад-Дарда сказал:
            \(AzkarSection.abuAdDardaArabic)
            «Те, чьи языки влажные от поминания Аллаха, войдут в Рай смеясь».
            Ахмад в «аз-Зухд», 726

            \(dots)
            ✅ Лучшее время для чтения утренних азкаров с Фаджра до восхода солнца.

            \(Self.link)
            """)
    }

    @Test("Напоминание вечерних — только про вечерние, без разметки WhatsApp")
    func eveningReminder() {
        let text = AzkarSection.evening.reminderText

        #expect(text.hasPrefix("⚠️ Вечерние азкары ⚠️\n"))
        #expect(text.contains("✅ Лучшее время для чтения вечерних азкаров с Асра до Магриба."))
        #expect(!text.contains("—"))
        #expect(!text.contains("утренние"))
        #expect(!text.contains("*"))
        #expect(!text.contains("_"))
    }

    @Test("Хадис без части блоков — пропущенные не оставляют пустых строк", arguments: [
        (nil as String?, "Перевод" as String?, "Муслим" as String?,
         ["40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", "Перевод", "Приводится: Муслим", link]),
        ("Умар", "Перевод", nil,
         ["40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", "Умар", "Перевод", link]),
        (nil, nil, nil,
         ["40 хадисов ан-Навави · Хадис 2", "عَنْ عُمَرَ قَالَ", link]),
    ])
    func partialHadith(narrator: String?, text: String?, source: String?, expected: [String]) {
        let shareText = Self.hadith(narrator: narrator, text: text, source: source).shareText

        #expect(shareText.components(separatedBy: ShareText.blockSeparator) == expected)
        #expect(!shareText.contains("\n\n\n"))
    }
}
