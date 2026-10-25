import Foundation
import Observation

/// Предложение серии, начавшейся `seriesStart`, на её `streak`-м дне.
nonisolated struct MotivationOffer: Hashable, Sendable {
    let seriesStart: AzkarCalendarDay
    let streak: Int

    /// День серии, на котором предложили.
    var day: AzkarCalendarDay { seriesStart.adding(days: streak - 1) }
}

/// «Добавьте ещё азкаров» (решение пользователя 2026-10-25, `docs/android/03-domain-logic.md`): кто держит
/// серию на неполном наборе, тому после «Машаа Аллах!» предлагается добавить азкаров. Хранит, когда
/// предлагали последний раз: ключи `azkar.motivation.seriesStart` (`yyyy-MM-dd`) и `azkar.motivation.streak`,
/// как Android `AzkarMotivation.kt`.
@Observable
final class AzkarMotivation {
    /// С какого дня серии предлагать.
    nonisolated static let firstStreak = 14
    /// Через сколько дней серии предложить снова после «В другой раз» или «ОК» без изменений.
    nonisolated static let repeatDays = 7

    private static let startKey = "azkar.motivation.seriesStart"
    private static let streakKey = "azkar.motivation.streak"

    @ObservationIgnored private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// Последнее предложение; испорченное — как не предлагали.
    var last: MotivationOffer? {
        guard let start = defaults.string(forKey: Self.startKey).flatMap(AzkarCalendarDay.init(key:)),
              let streak = defaults.object(forKey: Self.streakKey) as? Int
        else { return nil }
        return MotivationOffer(seriesStart: start, streak: streak)
    }

    /// См. `due(days:today:last:canAddMore:)`.
    func due(days: [AzkarCalendarDay: AzkarDay], today: AzkarCalendarDay, canAddMore: Bool) -> MotivationOffer? {
        Self.due(days: days, today: today, last: last, canAddMore: canAddMore)
    }

    /// Окно закрыто («ОК», «В другой раз», касание мимо): запоминается показанное, а не день закрытия.
    func offered(_ offer: MotivationOffer) {
        defaults.set(offer.seriesStart.key, forKey: Self.startKey)
        defaults.set(offer.streak, forKey: Self.streakKey)
    }

    /// Предложение на сегодня или `nil`. Пора, когда в наборе выбраны не все азкары (`canAddMore`), день `today`
    /// прочитан полностью, серия не меньше `firstStreak` и этой серии ещё не предлагали или предлагали
    /// `repeatDays` дней назад. Серия узнаётся по дате начала: прервалась — у новой другое начало, отсчёт снова.
    nonisolated static func due(
        days: [AzkarCalendarDay: AzkarDay],
        today: AzkarCalendarDay,
        last: MotivationOffer?,
        canAddMore: Bool
    ) -> MotivationOffer? {
        guard canAddMore, days[today]?.status == .full else { return nil }
        let streak = AzkarStreak(days: days, today: today).current
        guard streak >= firstStreak else { return nil }
        let offer = MotivationOffer(seriesStart: today.adding(days: 1 - streak), streak: streak)
        if let last, last.seriesStart == offer.seriesStart, streak < last.streak + repeatDays { return nil }
        return offer
    }
}
