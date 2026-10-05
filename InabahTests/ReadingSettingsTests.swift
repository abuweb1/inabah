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
}
