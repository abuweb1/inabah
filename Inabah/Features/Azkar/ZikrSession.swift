import Foundation
import Observation

/// Чтение одного зикра в текущей сессии: счёт и раскрытые блоки карточки.
///
/// Отдельный наблюдаемый объект на каждый зикр: нажатие на счётчик перерисовывает
/// только свою карточку, а не всю ленту.
@Observable
final class ZikrSession: Identifiable {
    let zikr: Zikr

    private(set) var count = 0
    /// Показаны транслитерация и перевод (кнопка «Аа»).
    var isTranslationVisible = false
    /// Выполненная карточка развёрнута из мини-строки обратно.
    var isExpanded = false

    init(zikr: Zikr) {
        self.zikr = zikr
    }

    var id: ZikrID { zikr.id }
    var isCompleted: Bool { count >= zikr.repetitions }

    enum IncrementResult: Equatable {
        case counted
        case completed
        case alreadyCompleted
    }

    @discardableResult
    func increment() -> IncrementResult {
        guard !isCompleted else { return .alreadyCompleted }
        count += 1
        if isCompleted {
            isExpanded = false
            return .completed
        }
        return .counted
    }

    func reset() {
        count = 0
        isExpanded = false
    }
}
