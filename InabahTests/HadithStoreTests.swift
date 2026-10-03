import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Сборники хадисов")
struct HadithStoreTests {
    private static func hadith(_ number: Int, text: String = "Текст", narrator: String? = nil) -> Hadith {
        Hadith(
            id: HadithID(collection: .nawawi, number: number),
            arabic: "حديث",
            translation: HadithTranslation(language: .base, narrator: narrator, text: text, source: nil)
        )
    }

    @Test("Загрузка сборника")
    func loads() async {
        let store = HadithStore(repository: InMemoryContentRepository(hadiths: [.nawawi: [Self.hadith(1), Self.hadith(2)]]))

        await store.loadAll()

        #expect(store.hadiths(in: .nawawi).map(\.number) == [1, 2])
        #expect(store.hadiths(in: .qudsi).isEmpty)
    }

    @Test("Ошибка контента — состояние ошибки")
    func failure() async {
        let store = HadithStore(repository: InMemoryContentRepository(error: .resourceMissing("nawawi.json")))

        await store.load(.nawawi)

        guard case .failed(.resourceMissing) = store.state(of: .nawawi) else {
            Issue.record("Ожидалась ошибка загрузки, получено \(store.state(of: .nawawi))")
            return
        }
    }

    @Test("Превью строки: без вступления «От …:»")
    func previewStripsNarratorPrefix() {
        let hadith = Self.hadith(1, text: "От Абу Хурайры: сказал Посланник Аллаха ﷺ")

        #expect(hadith.previewText == "сказал Посланник Аллаха ﷺ")
    }

    @Test("Превью строки: длинный текст обрезается до 80 символов с многоточием")
    func previewTruncates() {
        let hadith = Self.hadith(1, text: String(repeating: "слово ", count: 30))

        #expect(hadith.previewText.count <= Hadith.previewLength + 1)
        #expect(hadith.previewText.hasSuffix("…"))
    }

    @Test("Превью строки: текст без вступления не меняется")
    func previewKeepsPlainText() {
        #expect(Self.hadith(1, text: "Сказал Пророк ﷺ").previewText == "Сказал Пророк ﷺ")
    }

    @Test("Арабский текст — одним абзацем")
    func arabicJoinsLines() {
        let hadith = Hadith(
            id: HadithID(collection: .nawawi, number: 1),
            arabic: "عَنْ عُمَرَ\nقَالَ: \n«إِنَّمَا»",
            translation: nil
        )

        #expect(hadith.arabicDisplayText == "عَنْ عُمَرَ قَالَ: «إِنَّمَا»")
    }
}
