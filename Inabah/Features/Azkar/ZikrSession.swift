import Foundation
import Observation

/// Чтение одного зикра в текущем периоде: счёт и раскрытые блоки карточки.
///
/// Отдельный наблюдаемый объект на каждый зикр: нажатие на счётчик перерисовывает
/// только свою карточку, а не всю ленту.
@Observable
final class ZikrSession: Identifiable {
    let zikr: Zikr

    private(set) var count: Int
    /// Показаны транслитерация и перевод (кнопка «Аа»).
    var isTranslationVisible = false
    /// Выполненная карточка развёрнута из мини-строки обратно.
    var isExpanded = false
    /// Счёт изменился — `AzkarStore` сохраняет прогресс раздела.
    @ObservationIgnored var onCountChange: (() -> Void)?

    /// - Parameter count: восстановленный счёт (прогресс текущего периода), не больше нужного.
    init(zikr: Zikr, count: Int = 0) {
        self.zikr = zikr
        self.count = count.clamped(to: 0...zikr.repetitions)
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
        onCountChange?()
        if isCompleted {
            isExpanded = false
            return .completed
        }
        return .counted
    }

    func reset() {
        let hadProgress = count > 0
        count = 0
        isExpanded = false
        if hadProgress { onCountChange?() }
    }
}
