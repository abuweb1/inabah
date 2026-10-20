import Foundation

// Урок «Учимся читать» без SwiftUI (Android `ReadingLessonScreen.kt`): раскладка рядов, главная кнопка,
// части «лица» урока, арабские вставки в русском тексте.

/// Главная кнопка под уроком.
nonisolated enum LessonPrimaryAction: Hashable, Sendable {
    /// Урок без вопросов - «Готово» отмечает урок.
    case done
    /// «Проверить себя» - урок отметит проверка.
    case check
    /// Итог с зикрами - урок отмечается, когда прочитаны все зикры; кнопки нет.
    case none
}

/// Слог таблицы «Весь алфавит» по порядку «Послушать алфавит»: буква, знак (у закрытого - огласовка) и запись.
nonisolated struct TableSyllable: Hashable, Sendable, Identifiable {
    let letter: ReadingLetter
    let mark: ReadingMark
    let audio: String?

    /// Место в таблице: буква и знак.
    var id: String { Self.id(letter: letter, mark: mark) }

    static func id(letter: ReadingLetter, mark: ReadingMark) -> String { "\(letter.number).\(mark.id)" }
}

/// Кусок русского текста: арабская вставка или обычный текст.
nonisolated struct TextRun: Hashable, Sendable {
    let text: String
    let isArabic: Bool
}

nonisolated extension ReadingLogic {
    static func primaryAction(for step: ReadingStep) -> LessonPrimaryAction {
        if !step.zikrs.isEmpty { return .none }
        return step.quiz.isEmpty ? .done : .check
    }

    /// Части «лица» урока: пример шага, разделённый обычными пробелами (неразрывный держит словосочетание).
    static func heroParts(_ example: String) -> [String] {
        example.split(separator: " ").map(String.init)
    }

    /// Порядок «Послушать алфавит» в таблице слогов: ряд за рядом (буква), в ряду знаки по порядку `marks` -
    /// на экране справа налево (Android `syllablesPlayOrder`).
    static func syllablesPlayOrder(_ block: ReadingBlock.Syllables) -> [TableSyllable] {
        block.letters.flatMap { letter in
            block.marks.map { TableSyllable(letter: letter, mark: $0, audio: letter.audio[$0.id]) }
        }
    }

    /// То же для закрытых слогов (шаг «Сукун»): пустые места мадда (أُوْ, إِيْ) пропускаются.
    static func closedSyllablesPlayOrder(_ block: ReadingBlock.ClosedSyllables) -> [TableSyllable] {
        block.all.map { TableSyllable(letter: $0.letter, mark: $0.vowel, audio: $0.audio) }
    }

    /// Ряды сетки по `columns` с пустыми местами в неполном последнем ряду - ширина ячеек одинаковая.
    static func paddedRows<Item>(_ items: [Item], columns: Int) -> [[Item?]] {
        let width = Swift.max(columns, 1)
        return stride(from: 0, to: items.count, by: width).map { start in
            let row = items[start..<Swift.min(start + width, items.count)].map { Optional($0) }
            return row + Array(repeating: nil, count: width - row.count)
        }
    }

    /// Ряды формул: пустые места добавляются, только если в ряду есть формула с частями; ряд слов без
    /// частей растягивается во всю ширину.
    static func formulaRows(_ items: [ReadingFormula], columns: Int) -> [[ReadingFormula?]] {
        paddedRows(items, columns: columns).map { row in
            row.contains { $0?.parts.isEmpty == false } ? row : row.filter { $0 != nil }
        }
    }

    /// Арабские вставки в русском тексте: арабские буквы, между словами - пробелы.
    static func textRuns(_ text: String) -> [TextRun] {
        var runs: [TextRun] = []
        var rest = text[...]
        // По скалярам, как Kotlin по символам: буква со знаком - одна графема, но знак тоже арабский.
        let arabic = #/[\u{0600}-\u{06FF}]+(?:\s+[\u{0600}-\u{06FF}]+)*/#.matchingSemantics(.unicodeScalar)
        while let match = rest.firstMatch(of: arabic) {
            if match.range.lowerBound > rest.startIndex {
                runs.append(TextRun(text: String(rest[rest.startIndex..<match.range.lowerBound]), isArabic: false))
            }
            runs.append(TextRun(text: String(match.output), isArabic: true))
            rest = rest[match.range.upperBound...]
        }
        if !rest.isEmpty { runs.append(TextRun(text: String(rest), isArabic: false)) }
        return runs
    }
}
