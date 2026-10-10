import Foundation

// Напоминания (решения пользователя 2026-10-10, docs/07-screen-settings.md «Напоминания»; правила — как на
// Android, `ReminderPlanner.kt`). На iOS приложение не запускается в момент уведомления: всё решается при
// планировании, на `ReminderPlan.horizonDays` вперёд, и план пересобирается при каждом открытии приложения.

/// Вид напоминания.
nonisolated enum ReminderKind: String, CaseIterable, Sendable {
    case morningStart
    case eveningStart
    case morningUnfinished
    case eveningUnfinished
    case hadithOfDay

    var section: AzkarSection? {
        switch self {
        case .morningStart, .morningUnfinished: .morning
        case .eveningStart, .eveningUnfinished: .evening
        case .hadithOfDay: nil
        }
    }

    var isUnfinished: Bool { self == .morningUnfinished || self == .eveningUnfinished }

    static func start(_ section: AzkarSection) -> ReminderKind {
        section == .morning ? .morningStart : .eveningStart
    }

    static func unfinished(_ section: AzkarSection) -> ReminderKind {
        section == .morning ? .morningUnfinished : .eveningUnfinished
    }
}

/// Какие напоминания включены и когда хадис дня.
nonisolated struct ReminderPreferences: Hashable, Sendable {
    var azkarStart: Set<AzkarSection> = []
    var azkarUnfinished = false
    var hadithOfDay = false
    var hadithTime = ReminderPreferences.defaultHadithTime

    static let defaultHadithTime = DayTime(hour: 20, minute: 0)
}

/// Запланированное напоминание: `windowDay` — день окна азкаров (ключ истории), `hadithDay` — номер дня хадиса.
nonisolated struct PlannedReminder: Hashable, Sendable {
    let kind: ReminderKind
    let at: Date
    var windowDay: String?
    var hadithDay: Int?

    /// Постоянный идентификатор запроса: вид и момент — повтор плана даёт те же идентификаторы.
    var identifier: String {
        "\(ReminderPlan.identifierPrefix)\(kind.rawValue).\(Int(at.timeIntervalSince1970))"
    }
}

nonisolated enum ReminderPlan {
    static let identifierPrefix = "reminder."
    /// На сколько дней вперёд ставятся уведомления: до 35 запросов при лимите iOS 64.
    static let horizonDays = 7
    /// За сколько до конца окна — «ещё не прочитаны» (как на Android: там будильники неточные и запас нужен
    /// на опоздание; на iOS время точное, значение то же — одинаково на обеих платформах).
    static let unfinishedLead: TimeInterval = 2 * 60 * 60

    /// Ближайшие напоминания строго после `now` на `days` дней. «Ещё не прочитаны» — только для разделов
    /// с включённым «в начале» и только на окна, где раздел ещё не выполнен (`isCompleted(section, windowDay)`).
    static func plan(
        preferences: ReminderPreferences,
        windows: [AzkarSection: AzkarWindow],
        now: Date,
        calendar: Calendar,
        days: Int = horizonDays,
        isCompleted: (AzkarSection, String) -> Bool
    ) -> [PlannedReminder] {
        var result: [PlannedReminder] = []
        for section in AzkarSection.allCases where preferences.azkarStart.contains(section) {
            guard let window = windows[section], window.isValid else { continue }
            let schedule = AzkarWindowSchedule(window: window, calendar: calendar)
            var start = schedule.nextStart(after: now)
            for _ in 0..<days {
                result.append(PlannedReminder(kind: .start(section), at: start, windowDay: schedule.dayKey(of: start)))
                start = schedule.nextStart(after: start)
            }
            if preferences.azkarUnfinished {
                var windowStart = schedule.currentOrNextStart(now)
                var added = 0
                while added < days {
                    let reminder = unfinished(section, schedule: schedule, windowStart: windowStart)
                    if reminder.at > now {
                        added += 1
                        if let day = reminder.windowDay, !isCompleted(section, day) { result.append(reminder) }
                    }
                    windowStart = schedule.nextStart(after: windowStart)
                }
            }
        }
        if preferences.hadithOfDay {
            // Время хадиса не зависит от окон: расписание — только ради правил календаря.
            let anyWindow = AzkarWindow(start: preferences.hadithTime, end: DayTime(minutesSinceMidnight: preferences.hadithTime.minutesSinceMidnight + 1))
            let clock = AzkarWindowSchedule(window: anyWindow, calendar: calendar)
            var at = clock.nextOccurrence(ofTime: preferences.hadithTime, after: now)
            for day in 0..<days {
                result.append(PlannedReminder(kind: .hadithOfDay, at: at, hadithDay: day))
                at = clock.nextOccurrence(ofTime: preferences.hadithTime, after: at)
            }
        }
        return result.sorted { $0.at < $1.at }
    }

    /// За `unfinishedLead` до конца окна; окно короче — в его середине.
    private static func unfinished(_ section: AzkarSection, schedule: AzkarWindowSchedule, windowStart: Date) -> PlannedReminder {
        let end = schedule.windowEnd(startingAt: windowStart)
        let beforeEnd = end.addingTimeInterval(-unfinishedLead)
        let at = beforeEnd > windowStart ? beforeEnd : windowStart.addingTimeInterval(end.timeIntervalSince(windowStart) / 2)
        return PlannedReminder(kind: .unfinished(section), at: at, windowDay: schedule.dayKey(of: windowStart))
    }

    /// Показывать ли напоминание азкаров `kind` в момент `now` — как на Android (`isAzkarReminderDue`):
    /// только пока окно открыто; «ещё не прочитаны» — если раздел в этом окне не выполнен. `record` — запись
    /// истории за день окна (дату его начала: после полуночи — вчерашнюю). Пустой раздел (`total == 0`)
    /// выполненным не считается. На iOS этим решается, ставить ли «ещё не прочитаны» на текущее окно.
    static func isAzkarReminderDue(
        kind: ReminderKind,
        window: AzkarWindow,
        now: Date,
        calendar: Calendar,
        record: (String) -> AzkarDayRecord?
    ) -> Bool {
        let period = AzkarWindowSchedule(window: window, calendar: calendar).period(at: now)
        guard period.isWindow else { return false }
        guard kind.isUnfinished else { return true }
        return !isCompleted(record(period.day))
    }

    static func isCompleted(_ record: AzkarDayRecord?) -> Bool {
        guard let record else { return false }
        return record.total > 0 && record.completed >= record.total
    }

    // MARK: - Хадис дня

    /// Хадис дня: первый непрочитанный по порядку сборников с главной, внутри — по номеру. Всё прочитано —
    /// следующий после последнего показанного (`last`), по кругу; `last` не найден — первый; хадисов нет — `nil`.
    static func pickHadithOfDay(
        order: [HadithCollection],
        hadiths: [HadithCollection: [Hadith]],
        isRead: (HadithID) -> Bool,
        last: HadithID?
    ) -> Hadith? {
        let all = order.flatMap { (hadiths[$0] ?? []).sorted { $0.number < $1.number } }
        guard !all.isEmpty else { return nil }
        if let unread = all.first(where: { !isRead($0.id) }) { return unread }
        let lastIndex = all.firstIndex { $0.id == last } ?? -1
        return all[(lastIndex + 1) % all.count]
    }

    /// Хадисы на `count` дней подряд: каждый следующий — как если бы предыдущий уже был прочитан и показан.
    static func pickHadiths(
        count: Int,
        order: [HadithCollection],
        hadiths: [HadithCollection: [Hadith]],
        isRead: (HadithID) -> Bool,
        last: HadithID?
    ) -> [Hadith] {
        var picked: [Hadith] = []
        var shown = Set<HadithID>()
        var previous = last
        for _ in 0..<count {
            guard let hadith = pickHadithOfDay(
                order: order,
                hadiths: hadiths,
                isRead: { isRead($0) || shown.contains($0) },
                last: previous
            ) else { break }
            picked.append(hadith)
            shown.insert(hadith.id)
            previous = hadith.id
        }
        return picked
    }
}

// MARK: - Нажатие на уведомление

/// Куда ведёт нажатие на напоминание. Только навигация — данные не меняются (аудит Android, Б7).
nonisolated enum ReminderTarget: Hashable, Sendable {
    case azkar(AzkarSection)
    case hadith(HadithID)
}

nonisolated enum ReminderPayload {
    static let kindKey = "kind"
    static let collectionKey = "collection"
    static let numberKey = "number"

    static func userInfo(kind: ReminderKind, hadith: HadithID? = nil) -> [String: String] {
        var info = [kindKey: kind.rawValue]
        if let hadith {
            info[collectionKey] = hadith.collection.rawValue
            info[numberKey] = String(hadith.number)
        }
        return info
    }

    /// Разбор `userInfo` (аудит Android, Т7): неизвестный вид или сборник, номер < 1 — `nil`.
    static func target(from userInfo: [AnyHashable: Any]) -> ReminderTarget? {
        guard let kind = (userInfo[kindKey] as? String).flatMap(ReminderKind.init(rawValue:)) else { return nil }
        if let section = kind.section { return .azkar(section) }
        guard let collection = (userInfo[collectionKey] as? String).flatMap(HadithCollection.init(rawValue:)),
              let number = (userInfo[numberKey] as? String).flatMap(Int.init), number >= 1 else { return nil }
        return .hadith(HadithID(collection: collection, number: number))
    }

    /// Ключ хадиса в настройках (`"<сборник>_<n>"`), как на Android.
    static func key(_ id: HadithID) -> String { "\(id.collection.rawValue)_\(id.number)" }

    static func hadithID(fromKey key: String?) -> HadithID? {
        guard let key, let separator = key.lastIndex(of: "_"),
              let collection = HadithCollection(rawValue: String(key[..<separator])),
              let number = Int(key[key.index(after: separator)...]), number >= 1 else { return nil }
        return HadithID(collection: collection, number: number)
    }
}
