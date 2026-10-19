import Foundation

// Список шагов и карточка курса на главной (Android `ReadingStepsScreen.kt`, `MakharijHomeScreen.kt`):
// правила вынесены из экранов, чтобы проверять их тестами.

/// Состояние строки шага в списке.
nonisolated enum ReadingStepState: Hashable, Sendable {
    /// Урок пройден и все слова прочитаны.
    case done
    /// Первый непройденный шаг с уроком - кружок «дышит».
    case current
    /// Не пройдены шаги перед ним - замок, не нажимается.
    case locked
    case open
}

/// Подпись карточки «Учимся читать» на главной.
nonisolated enum ReadingHomeSummary: Hashable, Sendable {
    /// Текущий - итог: «Итог · Читаем зикр».
    case final(title: String)
    /// «Шаг 4 из 12 · Огласовки».
    case step(number: Int, total: Int, title: String)
    /// «Все шаги пройдены» - вместе с итогом.
    case allDone
    /// Шаги с уроком пройдены, но есть шаги без урока: «Пройдено 10 из 12».
    case progress(done: Int, total: Int)
}

nonisolated extension ReadingLogic {
    /// Состояние строки - по порядку: пройден → текущий → закрыт (если не «Открыть все шаги») → открыт.
    /// Пройденный и текущий шаги закрытыми не бывают; шаг без урока - открыт или закрыт.
    static func stepState(
        course: ReadingCourse,
        step: ReadingStep,
        completed: Set<Int>,
        current: ReadingStep?,
        allStepsOpen: Bool
    ) -> ReadingStepState {
        if completed.contains(step.number) { return .done }
        if step.number == current?.number { return .current }
        if !allStepsOpen, !isUnlocked(course: course, completed: completed, step: step) { return .locked }
        return .open
    }

    /// Последний открытый шаг с уроком (по настоящим правилам, без «Открыть все шаги»); нет - 1. Его замок
    /// «отщёлкивается» один раз - пока номер больше `ReadingProgress.unlockSeen`.
    static func lastUnlocked(course: ReadingCourse, completed: Set<Int>) -> Int {
        course.steps.last { $0.hasLesson && isUnlocked(course: course, completed: completed, step: $0) }?.number ?? 1
    }

    /// Строка показывает анимацию открытия: шаг открылся только что и его ещё не видели.
    static func isJustUnlocked(step: ReadingStep, state: ReadingStepState, unlockSeen: Int, lastUnlocked: Int) -> Bool {
        step.number > unlockSeen && step.number == lastUnlocked && state != .locked
    }

    /// Подпись карточки курса: текущий итог → текущий шаг → курс пройден → «пройдено N из M».
    static func homeSummary(course: ReadingCourse, completed: Set<Int>) -> ReadingHomeSummary {
        let total = numberedStepCount(course: course)
        if let current = currentStep(course: course, completed: completed) {
            return current.isFinal
                ? .final(title: current.title)
                : .step(number: current.number, total: total, title: current.title)
        }
        if isCourseDone(course: course, completed: completed) { return .allDone }
        return .progress(done: completedCount(course: course, completed: completed), total: total)
    }
}
