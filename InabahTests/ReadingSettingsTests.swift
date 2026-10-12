import Foundation
import Testing
@testable import Inabah

@MainActor
@Suite("Размер арабского шрифта")
struct ReadingSettingsTests {
    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("ReadingSettingsTests")
    }

    @Test("По умолчанию — 21")
    func defaultSize() {
        #expect(ReadingSettings(defaults: defaults).arabicFontSize == 21)
    }

    @Test("Шаг 2, верхняя граница 40")
    func increaseClampsToMax() {
        let settings = ReadingSettings(defaults: defaults)

        settings.increaseArabicFontSize()
        #expect(settings.arabicFontSize == 23)

        (0..<20).forEach { _ in settings.increaseArabicFontSize() }
        #expect(settings.arabicFontSize == 40)
        #expect(!settings.canIncreaseArabicFontSize)
    }

    @Test("Нижняя граница 14")
    func decreaseClampsToMin() {
        let settings = ReadingSettings(defaults: defaults)

        (0..<10).forEach { _ in settings.decreaseArabicFontSize() }
        #expect(settings.arabicFontSize == 14)
        #expect(!settings.canDecreaseArabicFontSize)
        #expect(settings.canIncreaseArabicFontSize)
    }

    @Test("Размер сохраняется между запусками")
    func persists() {
        ReadingSettings(defaults: defaults).increaseArabicFontSize()
        #expect(ReadingSettings(defaults: defaults).arabicFontSize == 23)
    }

    @Test("Сохранённое значение вне диапазона приводится к границе")
    func clampsStoredValue() {
        defaults.set(100.0, forKey: "reading.arabicFontSize")
        #expect(ReadingSettings(defaults: defaults).arabicFontSize == 40)
    }

    @Test("«Сворачивать арабский текст»: по умолчанию выключено, сохраняется под ключом Android")
    func hadithArabicCollapsed() {
        let settings = ReadingSettings(defaults: defaults)
        #expect(!settings.hadithArabicCollapsed)

        settings.setHadithArabicCollapsed(true)

        #expect(defaults.bool(forKey: "reading.hadithArabicCollapsed"))
        #expect(ReadingSettings(defaults: defaults).hadithArabicCollapsed)
    }
}

@Suite("Превью арабского текста")
struct ArabicPreviewTests {
    @Test("Длиннее 8 слов — первые 8 и «…»")
    func truncates() {
        let text = (1...10).map { "ك\($0)" }.joined(separator: " ")
        #expect(ArabicText.preview(of: text) == (1...8).map { "ك\($0)" }.joined(separator: " ") + "…")
    }

    @Test("8 слов и меньше — все, через одиночный пробел, без «…»")
    func keepsShort() {
        #expect(ArabicText.preview(of: "قَالَ\n  رَسُولُ   اللَّهِ") == "قَالَ رَسُولُ اللَّهِ")
    }
}
