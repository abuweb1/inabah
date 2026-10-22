import Foundation

// Руководства по произношению (Android `ReadingLessonScreen.kt`, 2026-10-21): какие буквы пометить точкой
// и какие руководства предложить кнопками под уроком.

nonisolated extension ReadingLogic {
    /// Все буквы руководств - у них точка «особое произношение» в сетке и таблице форм.
    static func markedLetters(course: ReadingCourse) -> Set<ReadingLetter> {
        Set(course.guides.flatMap(\.letters))
    }

    /// Руководства, буквы которых есть в сетке букв или таблице форм шага, в порядке данных.
    static func guides(for step: ReadingStep, course: ReadingCourse) -> [ReadingGuide] {
        var shown: Set<ReadingLetter> = []
        for block in step.blocks {
            switch block {
            case .letters(let letters): shown.formUnion(letters.letters)
            case .forms(let forms): shown.formUnion(forms.letters)
            default: break
            }
        }
        return course.guides.filter { $0.letters.contains(where: shown.contains) }
    }
}
