import Foundation
@testable import Inabah

/// Курс «Учимся читать» из настоящего `reading.json` в Bundle (как `loadAssetCourse()` на Android).
enum ReadingCourseFixture {
    static func load() async throws -> ReadingCourse {
        try await BundleContentRepository(languages: ContentLanguagePriority(languages: [.base])).readingCourse()
    }
}

extension ReadingCourse {
    /// Шаг, который в данных точно есть.
    func requiredStep(_ number: Int) throws -> ReadingStep {
        guard let step = step(number) else { throw FixtureError.missing("шаг \(number)") }
        return step
    }

    func letter(_ character: String) throws -> ReadingLetter {
        guard let letter = letters.first(where: { $0.letter == character }) else {
            throw FixtureError.missing("буква \(character)")
        }
        return letter
    }

    func mark(_ id: String) throws -> ReadingMark {
        guard let mark = marks.first(where: { $0.id == id }) else { throw FixtureError.missing("знак \(id)") }
        return mark
    }
}

enum FixtureError: Error {
    case missing(String)
}

extension ReadingStep {
    var formsBlocks: [ReadingBlock.Forms] { blocks.compactMap { if case .forms(let b) = $0 { b } else { nil } } }
    var formulasBlocks: [ReadingBlock.Formulas] { blocks.compactMap { if case .formulas(let b) = $0 { b } else { nil } } }
    var syllablesBlocks: [ReadingBlock.Syllables] { blocks.compactMap { if case .syllables(let b) = $0 { b } else { nil } } }
    var closedSyllablesBlocks: [ReadingBlock.ClosedSyllables] {
        blocks.compactMap { if case .closedSyllables(let b) = $0 { b } else { nil } }
    }
    var marksBlocks: [ReadingBlock.Marks] { blocks.compactMap { if case .marks(let b) = $0 { b } else { nil } } }
    var ruleBlocks: [ReadingBlock.Rule] { blocks.compactMap { if case .rule(let b) = $0 { b } else { nil } } }
    var textBlocks: [ReadingBlock.Text] { blocks.compactMap { if case .text(let b) = $0 { b } else { nil } } }
}

extension ReadingExtra {
    var formulasBlocks: [ReadingBlock.Formulas] { blocks.compactMap { if case .formulas(let b) = $0 { b } else { nil } } }
    var ruleBlocks: [ReadingBlock.Rule] { blocks.compactMap { if case .rule(let b) = $0 { b } else { nil } } }
}

extension ReadingQuestion {
    var syllable: Syllable? { if case .syllable(let q) = self { q } else { nil } }
    var closedSyllable: ClosedSyllable? { if case .closedSyllable(let q) = self { q } else { nil } }
    var word: Word? { if case .word(let q) = self { q } else { nil } }
    var letterInForm: LetterInForm? { if case .letterInForm(let q) = self { q } else { nil } }
}
