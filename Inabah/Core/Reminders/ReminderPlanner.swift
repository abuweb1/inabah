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
    /// «Если серия под угрозой» (Android-first 2026-10-11, `MorningStreak` / `EveningStreak`).
    case morningStreak
    case eveningStreak
    case hadithOfDay

    var section: AzkarSection? {
        switch self {
        case .morningStart, .morningUnfinished, .morningStreak: .morning
        case .eveningStart, .eveningUnfinished, .eveningStreak: .evening
        case .hadithOfDay: nil
        }
    }

    var isUnfinished: Bool { self == .morningUnfinished || self == .eveningUnfinished }

    static func streak(_ section: AzkarSection) -> ReminderKind {
        section == .morning ? .morningStreak : .eveningStreak
    }

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
    /// «Если серия под угрозой» — независимо от напоминаний в начале времени.
    var azkarStreak = false
    var hadithOfDay = false
    var hadithTime = ReminderPreferences.defaultHadithTime

    static let defaultHadithTime = DayTime(hour: 20, minute: 0)
}

/// Запланированное напоминание: `windowDay` — день окна азкаров (ключ истории), `hadithDay` — номер дня хадиса,
/// `streak` — серия, которая прервётся (для текста «серия под угрозой»).
nonisolated struct PlannedReminder: Hashable, Sendable {
    let kind: ReminderKind
    let at: Date
    var windowDay: String?
    var hadithDay: Int?
    var streak: Int?

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
    /// За сколько до конца окна — «серия под угрозой» (решение пользователя 2026-10-11: за час, позже
    /// «ещё не прочитаны»; оба приходят каждое в своё время).
    static let streakLead: TimeInterval = 60 * 60

    /// Ближайшие напоминания строго после `now` на `days` дней. «Ещё не прочитаны» — только для разделов
    /// с включённым «в начале» и только на окна, где раздел ещё не выполнен (`isCompleted(section, windowDay)`).
    ///
    /// «Серия под угрозой» — за `streakLead` до конца окна, независимо от «в начале» и от «не прочитаны»:
    /// правило Android (`streakAtRisk`) считается для будущего момента по истории `history` как есть — будто
    /// дальше ничего не прочитают. Прочтение пересобирает план (и снимает оба напоминания), а без него серия
    /// прерывается — дальше уведомлений нет, как и на Android.
    static func plan(
        preferences: ReminderPreferences,
        windows: [AzkarSection: AzkarWindow],
        now: Date,
        calendar: Calendar,
        days: Int = horizonDays,
        history: [AzkarCalendarDay: AzkarDay] = [:],
        isCompleted: (AzkarSection, String) -> Bool
    ) -> [PlannedReminder] {
        var result: [PlannedReminder] = []
        for section in AzkarSection.allCases {
            guard let window = windows[section], window.isValid else { continue }
            let schedule = AzkarWindowSchedule(window: window, calendar: calendar)
            let startsEnabled = preferences.azkarStart.contains(section)
            if startsEnabled {
                var start = schedule.nextStart(after: now)
                for _ in 0..<days {
                    result.append(PlannedReminder(kind: .start(section), at: start, windowDay: schedule.dayKey(of: start)))
                    start = schedule.nextStart(after: start)
                }
            }
            let wantsUnfinished = startsEnabled && preferences.azkarUnfinished
            if wantsUnfinished {
                for reminder in beforeEnd(.unfinished(section), lead: unfinishedLead, schedule: schedule, now: now, days: days) {
                    if let day = reminder.windowDay, !isCompleted(section, day) { result.append(reminder) }
                }
            }
            if preferences.azkarStreak {
                for moment in beforeEnd(.streak(section), lead: streakLead, schedule: schedule, now: now, days: days) {
                    guard let streak = streakAtRisk(section: section, windows: windows, days: history, at: moment.at, calendar: calendar)
                    else { continue }
                    var atRisk = moment
                    atRisk.streak = streak
                    result.append(atRisk)
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

    /// Напоминания `kind` за `lead` до конца ближайших `days` окон строго после `now`; окно короче `lead` —
    /// в его середине.
    private static func beforeEnd(
        _ kind: ReminderKind,
        lead: TimeInterval,
        schedule: AzkarWindowSchedule,
        now: Date,
        days: Int
    ) -> [PlannedReminder] {
        var result: [PlannedReminder] = []
        var windowStart = schedule.currentOrNextStart(now)
        while result.count < days {
            let end = schedule.windowEnd(startingAt: windowStart)
            let beforeEnd = end.addingTimeInterval(-lead)
            let at = beforeEnd > windowStart ? beforeEnd : windowStart.addingTimeInterval(end.timeIntervalSince(windowStart) / 2)
            if at > now {
                result.append(PlannedReminder(kind: kind, at: at, windowDay: schedule.dayKey(of: windowStart)))
            }
            windowStart = schedule.nextStart(after: windowStart)
        }
        return result
    }

    /// Показывать ли напоминание азкаров `kind` в момент `now` — как на Android (`isAzkarReminderDue`):
    /// только пока окно открыто; «ещё не прочитаны» — если раздел в этом окне не выполнен. `record` — запись
    /// истории за день окна (дату его начала: после полуночи — вчерашнюю). Пустой раздел (`total == 0`)
    /// выполненным не считается. В приложении не вызывается: на iOS триггер точный, а «ещё не прочитаны»
    /// не ставится на выполненные окна (`plan`, `isCompleted`). Зеркало Android — держит правила одинаковыми
    /// и проверяется тестами (аудит iOS 1.1.1, А17).
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

    /// Самая короткая серия, о которой напоминать (Android `STREAK_AT_RISK_MIN`).
    static let streakAtRiskMinimum = 1

    /// «Серия под угрозой» (решения пользователя 2026-10-11, Android `streakAtRisk`): длина серии, которая
    /// прервётся, если не дочитать `section` в окне, открытом в момент `at`, или `nil`. Утро — утренние дня
    /// не прочитаны полностью; вечер — вечерние не прочитаны, а утренние того же дня прочитаны (иначе серия
    /// уже прервана). Серия — на «сегодня» по вечернему окну (`AzkarStreak.today`): сегодняшний неполный
    /// день в неё не входит — это ровно то, что прервётся.
    static func streakAtRisk(
        section: AzkarSection,
        windows: [AzkarSection: AzkarWindow],
        days: [AzkarCalendarDay: AzkarDay],
        at moment: Date,
        calendar: Calendar
    ) -> Int? {
        guard let window = windows[section], window.isValid else { return nil }
        let period = AzkarWindowSchedule(window: window, calendar: calendar).period(at: moment)
        guard period.isWindow, let windowDay = AzkarCalendarDay(key: period.day) else { return nil }
        let day = days[windowDay]
        if day?.record(of: section)?.isComplete == true { return nil }
        if section == .evening, day?.morning?.isComplete != true { return nil }
        guard let evening = windows[.evening], evening.isValid,
              let today = AzkarStreak.today(evening: AzkarWindowSchedule(window: evening, calendar: calendar), at: moment)
        else { return nil }
        let streak = AzkarStreak(days: days, today: today).current
        return streak >= streakAtRiskMinimum ? streak : nil
    }

    static func isCompleted(_ record: AzkarDayRecord?) -> Bool {
        record?.isComplete == true
    }

    /// Раздел выполнен — по текущему прогрессу (то же правило, что у записи истории).
    static func isCompleted(_ progress: SectionProgress) -> Bool {
        progress.total > 0 && progress.completed >= progress.total
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

    /// Хадисы на `count` дней подряд — как на Android, где хадис выбирается в момент показа (решение
    /// пользователя 2026-10-10, «тот же, пока не прочтёшь»): пока есть непрочитанный, каждый день приходит
    /// первый непрочитанный — до тех пор, пока его не прочтут (прочтение — в приложении, после чего план
    /// пересобирается). Всё прочитано — по кругу: каждый день следующий после показанного накануне.
    static func pickHadiths(
        count: Int,
        order: [HadithCollection],
        hadiths: [HadithCollection: [Hadith]],
        isRead: (HadithID) -> Bool,
        last: HadithID?
    ) -> [Hadith] {
        var picked: [Hadith] = []
        var previous = last
        for _ in 0..<count {
            guard let hadith = pickHadithOfDay(order: order, hadiths: hadiths, isRead: isRead, last: previous) else { break }
            picked.append(hadith)
            previous = hadith.id
        }
        return picked
    }
}

// MARK: - Нажатие на уведомление

/// Куда ведёт нажатие на напоминание. Только навигация — данные не меняются (аудит Android, Б7).
nonisolated enum ReminderTarget: Hashable, Sendable {
    /// Главная азкаров — «серия под угрозой» (там капсула серии и календарь; решение пользователя 2026-10-13).
    case azkarHome
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
        if kind == .morningStreak || kind == .eveningStreak { return .azkarHome }
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
