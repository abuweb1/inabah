import Foundation
import Observation

/// Настройки напоминаний — ключи и значения по умолчанию как на Android (`ReminderSettings.kt`,
/// docs/android/03-domain-logic.md, 3.8): всё выключено, хадис дня — в 20:00. Переключатели сохраняются
/// сразу; время хадиса дня экран правит черновиком и сохраняет при уходе.
@Observable
final class ReminderSettings {
    private enum Key {
        static let morning = "reminders.azkar.morning"
        static let evening = "reminders.azkar.evening"
        static let unfinished = "reminders.azkar.unfinished"
        static let streak = "reminders.azkar.streak"
        static let hadithEnabled = "reminders.hadith.enabled"
        static let hadithTime = "reminders.hadith.time"
        static let offerAnswered = "reminders.offer.answered"
        static let hadithLast = "reminders.hadith.last"
        static let hadithPlan = "reminders.hadith.plan"

        static func reading(_ slot: ReadingReminderSlot) -> String { "reminders.reading.\(slot.rawValue)" }
        static func readingTime(_ slot: ReadingReminderSlot) -> String { "reminders.reading.\(slot.rawValue).time" }
    }

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var preferences: ReminderPreferences
    private(set) var offerAnswered: Bool

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        var preferences = ReminderPreferences()
        if defaults.bool(forKey: Key.morning) { preferences.azkarStart.insert(.morning) }
        if defaults.bool(forKey: Key.evening) { preferences.azkarStart.insert(.evening) }
        preferences.azkarUnfinished = defaults.bool(forKey: Key.unfinished)
        preferences.azkarStreak = defaults.bool(forKey: Key.streak)
        preferences.hadithOfDay = defaults.bool(forKey: Key.hadithEnabled)
        if let minutes = defaults.object(forKey: Key.hadithTime) as? Int {
            preferences.hadithTime = DayTime(minutesSinceMidnight: minutes)
        }
        for slot in ReadingReminderSlot.allCases {
            if defaults.bool(forKey: Key.reading(slot)) { preferences.readingSlots.insert(slot) }
            if let minutes = defaults.object(forKey: Key.readingTime(slot)) as? Int {
                preferences.readingTimes[slot] = DayTime(minutesSinceMidnight: minutes)
            }
        }
        self.preferences = preferences
        offerAnswered = defaults.bool(forKey: Key.offerAnswered)
    }

    func isStartEnabled(_ section: AzkarSection) -> Bool { preferences.azkarStart.contains(section) }

    /// Включение любого напоминания отвечает и на предложение на главной.
    func setStart(_ isOn: Bool, for section: AzkarSection) {
        guard isOn != isStartEnabled(section) else { return }
        if isOn { preferences.azkarStart.insert(section) } else { preferences.azkarStart.remove(section) }
        defaults.set(isOn, forKey: section == .morning ? Key.morning : Key.evening)
        if isOn { answerOffer() }
    }

    func setUnfinished(_ isOn: Bool) {
        guard isOn != preferences.azkarUnfinished else { return }
        preferences.azkarUnfinished = isOn
        defaults.set(isOn, forKey: Key.unfinished)
        if isOn { answerOffer() }
    }

    /// «Если серия под угрозой» (Android-first 2026-10-11) — независимо от напоминаний в начале времени.
    func setStreak(_ isOn: Bool) {
        guard isOn != preferences.azkarStreak else { return }
        preferences.azkarStreak = isOn
        defaults.set(isOn, forKey: Key.streak)
        if isOn { answerOffer() }
    }

    func setHadithOfDay(_ isOn: Bool) {
        guard isOn != preferences.hadithOfDay else { return }
        preferences.hadithOfDay = isOn
        defaults.set(isOn, forKey: Key.hadithEnabled)
        if isOn { answerOffer() }
    }

    func setHadithTime(_ time: DayTime) {
        guard time != preferences.hadithTime else { return }
        preferences.hadithTime = time
        defaults.set(time.minutesSinceMidnight, forKey: Key.hadithTime)
    }

    // MARK: - «Пора на урок» (Android-first 2026-10-20)

    func isReadingEnabled(_ slot: ReadingReminderSlot) -> Bool { preferences.readingSlots.contains(slot) }

    func setReading(_ isOn: Bool, for slot: ReadingReminderSlot) {
        guard isOn != isReadingEnabled(slot) else { return }
        if isOn { preferences.readingSlots.insert(slot) } else { preferences.readingSlots.remove(slot) }
        defaults.set(isOn, forKey: Key.reading(slot))
        if isOn { answerOffer() }
    }

    /// Записывается только выбранное время (как Android): без него - время слота по умолчанию.
    func setReadingTime(_ time: DayTime, for slot: ReadingReminderSlot) {
        guard time != preferences.readingTime(slot) else { return }
        preferences.readingTimes[slot] = time
        defaults.set(time.minutesSinceMidnight, forKey: Key.readingTime(slot))
    }

    func answerOffer() {
        guard !offerAnswered else { return }
        offerAnswered = true
        defaults.set(true, forKey: Key.offerAnswered)
    }

    // MARK: - Хадис дня: показанные

    /// Последний показанный хадис дня; повреждённое значение — `nil`.
    var lastHadith: HadithID? {
        ReminderPayload.hadithID(fromKey: defaults.string(forKey: Key.hadithLast))
    }

    func setLastHadith(_ id: HadithID?) {
        guard id != lastHadith else { return }
        defaults.set(id.map(ReminderPayload.key), forKey: Key.hadithLast)
    }

    /// Запланированные хадисы дня: момент уведомления (секунды) → хадис. По нему при следующем
    /// планировании узнаётся, какой хадис уже показан.
    var hadithPlan: [TimeInterval: HadithID] {
        guard let stored = defaults.dictionary(forKey: Key.hadithPlan) as? [String: String] else { return [:] }
        var plan: [TimeInterval: HadithID] = [:]
        for (time, key) in stored {
            if let seconds = TimeInterval(time), let id = ReminderPayload.hadithID(fromKey: key) { plan[seconds] = id }
        }
        return plan
    }

    func setHadithPlan(_ plan: [TimeInterval: HadithID]) {
        let stored = Dictionary(uniqueKeysWithValues: plan.map { (String(Int($0.key)), ReminderPayload.key($0.value)) })
        defaults.set(stored, forKey: Key.hadithPlan)
    }
}
