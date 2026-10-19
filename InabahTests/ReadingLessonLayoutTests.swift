import Foundation
import SwiftUI
import Testing
@testable import Inabah

/// Урок «Учимся читать»: раскладка рядов, главная кнопка, «лицо», арабские вставки, выделение буквы.
@Suite("Курс «Учимся читать»: урок")
struct ReadingLessonLayoutTests {
    @Test("Неполный ряд сетки - с пустыми местами, ширина ячеек одинаковая")
    func paddedRows() {
        #expect(ReadingLogic.paddedRows([1, 2, 3, 4, 5, 6, 7], columns: 5) == [[1, 2, 3, 4, 5], [6, 7, nil, nil, nil]])
        #expect(ReadingLogic.paddedRows([1, 2], columns: 2) == [[1, 2]])
        #expect(ReadingLogic.paddedRows([Int](), columns: 3).isEmpty)
    }

    @Test("Ряд формул: пустые места - только если в ряду есть формула с частями")
    func formulaRows() {
        let withParts = ReadingFormula(parts: ["بَ", "ا"], result: "بَا", transliteration: nil)
        let word = ReadingFormula(parts: [], result: "ٱلْحَمْدُ", transliteration: nil)

        #expect(ReadingLogic.formulaRows([withParts, withParts, withParts, withParts], columns: 3).map(\.count) == [3, 3])
        #expect(ReadingLogic.formulaRows([word, word, word, word], columns: 3).map(\.count) == [3, 1], "слова - во всю ширину")
    }

    @Test("«Лицо» урока делится обычными пробелами, неразрывный держит словосочетание")
    func heroParts() {
        #expect(ReadingLogic.heroParts("ا ب ت") == ["ا", "ب", "ت"])
        #expect(ReadingLogic.heroParts("فِي\u{00A0}ٱلْقُبُورِ") == ["فِي\u{00A0}ٱلْقُبُورِ"])
    }

    @Test("Главная кнопка: «Готово» без вопросов, «Проверить себя» с вопросами, у итога с зикрами - нет")
    func primaryAction() async throws {
        let course = try await ReadingCourseFixture.load()
        let lesson: [ReadingBlock] = [.text(.init(text: "Урок"))]
        let noQuiz = ReadingStep(number: 1, markIds: [], example: nil, title: "", subtitle: "", blocks: lesson, quiz: [])

        #expect(ReadingLogic.primaryAction(for: noQuiz) == .done)
        #expect(ReadingLogic.primaryAction(for: try course.requiredStep(2)) == .check)
        #expect(ReadingLogic.primaryAction(for: try #require(course.steps.last)) == .none)
    }

    @Test("Арабские вставки в русском тексте - отдельными кусками, слова вставки вместе")
    func textRuns() {
        let runs = ReadingLogic.textRuns("Буква ب и слово قَالَ رَسُولُ - читаются так")

        #expect(runs == [
            TextRun(text: "Буква ", isArabic: false),
            TextRun(text: "ب", isArabic: true),
            TextRun(text: " и слово ", isArabic: false),
            TextRun(text: "قَالَ رَسُولُ", isArabic: true),
            TextRun(text: " - читаются так", isArabic: false),
        ])
        #expect(ReadingLogic.textRuns("Без арабского") == [TextRun(text: "Без арабского", isArabic: false)])
    }

    @Test("Выделение буквы в слове - ровно её символы со знаками")
    func highlightedLetter() {
        let word = "جَبُنَ"
        let range = ReadingLogic.letterRange(word: word, letter: "ب", form: .medial)

        let attributed = ReadingText.highlighted(word, range: range, color: .red)

        let colored = attributed.runs.filter { $0.foregroundColor != nil }.map { String(attributed[$0.range].characters) }
        #expect(colored == ["بُ"])
        #expect(String(attributed.characters) == word)
        #expect(ReadingText.highlighted(word, range: nil, color: .red).runs.allSatisfy { $0.foregroundColor == nil })
    }
}
