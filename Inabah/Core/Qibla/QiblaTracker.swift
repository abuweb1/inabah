import Foundation

/// В какую сторону поворачиваться к Къибле.
nonisolated enum TurnSide: Hashable, Sendable {
    case left, right
}

/// Насколько: «Поверните направо» / «Правее» / «Немного правее».
nonisolated enum TurnAmount: Hashable, Sendable {
    case far, mid, near
}

/// Метка Къиблы на кольце относительно указателя (верха телефона) — Android `QiblaPointer`.
nonisolated struct QiblaPointer: Hashable, Sendable {
    /// Сглаженное отклонение метки, −180…180: плюс — справа от указателя.
    var offset: Double
    /// Тот же угол без перескока через 0°/360°: копится по кратчайшим поворотам — анимация к нему не делает оборот.
    var angle: Double
    /// Сторона поворота; около 180° держится прежней (`QiblaTuning.flipZoneDegrees`).
    var side: TurnSide
    /// Направление найдено — с гистерезисом и только при надёжном показании.
    var aligned: Bool
    /// Сколько раз метка входила в совпадение после настоящего ухода из него: по росту — один виброотклик.
    var alignedEntries: Int
    /// Телефон наклонён (с гистерезисом).
    var tilted = false
    /// Компас неточен: статус датчика, оценка точности или помехи (с гистерезисом).
    var lowAccuracy = false
    /// Насколько поворачиваться — с запасом у границ.
    var amount = TurnAmount.far
    /// Номер подписки: у новой счёт входов с нуля — экран сравнивает счёт только внутри одного номера.
    var generation = 0
    /// Метка у указателя и показание надёжно, но координатам нельзя доверить подтверждение.
    var heldByLocation = false
}

/// Сглаживает показания датчика и решает, найдено ли направление (Android `QiblaTracker`). Состояние —
/// между показаниями; один трекер на подписку экрана, время показаний — монотонное (секунды).
nonisolated struct QiblaTracker {
    private let tuning: QiblaTuning
    let generation: Int

    private var angle: Double?
    private var lastTime: TimeInterval = 0
    private var side = TurnSide.right
    private var aligned = false
    private var alignedEnded: TimeInterval?
    private var entries = 0
    private var tilted = false
    private var inaccurate = false
    private var interference = false
    private var amount = TurnAmount.far

    private static let halfTurn = 180.0

    init(tuning: QiblaTuning = .default, generation: Int = 0) {
        precondition(tuning.alignEnterDegrees <= tuning.alignExitDegrees, "Порог входа не больше порога выхода")
        precondition(tuning.tiltExitDegrees <= tuning.tiltMaxDegrees)
        precondition(tuning.headingAccuracyExitDegrees <= tuning.headingAccuracyMaxDegrees)
        self.tuning = tuning
        self.generation = generation
    }

    /// Новое показание: метка должна стоять на `target` градусов от указателя (направление на Каабу минус
    /// направление телефона, любой угол). `locationConfirmable` — координатам можно доверить подтверждение.
    mutating func update(target: Double, sample: HeadingSample, locationConfirmable: Bool = true) -> QiblaPointer {
        let time = sample.time
        let elapsed = time - lastTime
        let next: Double
        if let current = angle {
            let step = QiblaMath.signedDelta(from: current, to: target)
            next = elapsed < 0 || elapsed > tuning.snapAfter ? current + step : current + step * smoothingFactor(elapsed)
        } else {
            next = QiblaMath.signedDelta(from: 0, to: target)
        }
        angle = next
        lastTime = time

        let offset = QiblaMath.signedDelta(from: 0, to: next)
        let distance = abs(offset)
        if distance < Self.halfTurn - tuning.flipZoneDegrees && offset != 0 {
            side = offset > 0 ? .right : .left
        }
        tilted = sample.tiltDegrees > (tilted ? tuning.tiltExitDegrees : tuning.tiltMaxDegrees)
        interference = isInterference(sample)
        let accuracyLimit = inaccurate ? tuning.headingAccuracyExitDegrees : tuning.headingAccuracyMaxDegrees
        inaccurate = sample.lowAccuracy || interference || (sample.accuracyDegrees.map { $0 > accuracyLimit } ?? false)

        let limit = aligned ? tuning.alignExitDegrees : tuning.alignEnterDegrees
        let onTarget = !tilted && !inaccurate && distance <= limit
        let nowAligned = onTarget && locationConfirmable
        if nowAligned && !aligned {
            if alignedEnded.map({ time - $0 >= tuning.alignRearm }) ?? true { entries += 1 }
        } else if !nowAligned && aligned {
            alignedEnded = time
        }
        aligned = nowAligned
        amount = turnAmount(distance)
        return QiblaPointer(
            offset: offset,
            angle: next,
            side: side,
            aligned: aligned,
            alignedEntries: entries,
            tilted: tilted,
            lowAccuracy: inaccurate,
            amount: amount,
            generation: generation,
            heldByLocation: onTarget && !locationConfirmable
        )
    }

    /// Помеха: сила поля далеко от ожидаемой в этой точке; гистерезис — флаг не мигает у края.
    /// На iOS ожидаемая сила неизвестна (мировой модели поля в открытом API нет) — помехи видны по точности.
    private func isInterference(_ sample: HeadingSample) -> Bool {
        guard let field = sample.fieldMicroTesla, let expected = sample.expectedFieldMicroTesla, expected > 0 else {
            return false
        }
        let deviation = abs(field / expected - 1)
        return deviation > (interference ? tuning.interferenceExitRatio : tuning.interferenceEnterRatio)
    }

    /// Степень поворота: граница сдвигается на запас в сторону прежней степени — надпись не мигает.
    private func turnAmount(_ distance: Double) -> TurnAmount {
        let margin = tuning.turnHysteresisDegrees
        let far = tuning.turnFarDegrees + (amount == .far ? -margin : margin)
        let near = tuning.turnNearDegrees + (amount == .near ? margin : -margin)
        if distance > far { return .far }
        if distance > near { return .mid }
        return .near
    }

    /// Доля пути к новому показанию за `elapsed`: экспоненциальное сглаживание, не зависит от частоты датчика.
    private func smoothingFactor(_ elapsed: TimeInterval) -> Double {
        tuning.smoothing == 0 ? 1 : 1 - exp(-elapsed / tuning.smoothing)
    }
}
