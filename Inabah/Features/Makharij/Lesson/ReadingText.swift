import SwiftUI

/// Текст курса, собранный из кусков: арабские вставки в русском тексте и слово с выделенной буквой.
nonisolated enum ReadingText {
    /// Арабская вставка крупнее русского текста - как Android `withArabicRuns` (1,3 em).
    private static let arabicRunScale = 1.3
    /// Изоляция направления вокруг вставки: арабский не переставляет соседние русские слова.
    private static let isolateStart = "\u{2067}"
    private static let isolateEnd = "\u{2069}"

    /// Русский текст с арабскими вставками Scheherazade; остальной текст - шрифтом вида.
    static func withArabicRuns(_ text: String, baseSize: Double) -> AttributedString {
        ReadingLogic.textRuns(text).reduce(into: AttributedString()) { result, run in
            if run.isArabic {
                // Вставка не разрывается между строками: «(ص ض ظ ط)» читается целиком.
                let unbroken = run.text.replacing(#/\s+/#, with: "\u{00A0}")
                var part = AttributedString(isolateStart + unbroken + isolateEnd)
                part.font = .arabic(size: baseSize * arabicRunScale)
                result += part
            } else {
                result += AttributedString(run.text)
            }
        }
    }

    /// Слово с буквой `range` (единицы UTF-16, `ReadingLogic.letterRange`) цвета `color`; нет - слово целиком.
    static func highlighted(_ word: String, range: ClosedRange<Int>?, color: Color) -> AttributedString {
        var result = AttributedString(word)
        guard let range,
              let lower = index(word, utf16Offset: range.lowerBound),
              let upper = index(word, utf16Offset: range.upperBound + 1),
              let start = AttributedString.Index(lower, within: result),
              let end = AttributedString.Index(upper, within: result)
        else { return result }
        result[start..<end].foregroundColor = color
        return result
    }

    /// Позиция в строке по смещению в UTF-16 (как индексы Kotlin); за пределами строки - `nil`.
    private static func index(_ string: String, utf16Offset offset: Int) -> String.Index? {
        guard offset >= 0, offset <= string.utf16.count else { return nil }
        return string.utf16.index(string.utf16.startIndex, offsetBy: offset)
    }
}
