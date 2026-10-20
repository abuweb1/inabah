import Foundation
import Observation

/// Зикр итога «Читаем зикр»: номера прочитанных слов и «прочитал целиком».
nonisolated struct ZikrProgress: Hashable, Sendable {
    var words: Set<Int> = []
    var isRead = false
}

/// Прогресс «Учимся читать» (раздел «Махрадж»), ключи - как на Android (`ReadingProgress.kt`):
/// - урок шага пройден - даны ответы на все вопросы проверки (правильность не важна) или нажато «Готово» у
///   урока без вопросов; `reading.step.<N>.done` = `true`;
/// - «Читаем слова» - сколько раз прочитано каждое слово шага: `reading.words.<N>` = `"10,10,3,0"`;
/// - итог «Читаем зикр» - зикр по разделу и номеру в «Азкарах» (не по месту в шаге): прочитанные слова
///   `reading.zikr.morning.6.words` = `"0,1,4"` и «прочитал целиком» `reading.zikr.morning.6.read` = `true`;
/// - последний шаг, открытие которого уже показано (замок «отщёлкнулся»): `reading.unlockSeen`;
/// - день, когда последний раз показано окно «Шаг пройден» (`ГГГГ-ММ-ДД`, григорианский календарь, пояс
///   устройства): `reading.stepFinishedDay` - «Пора на урок» в этот день больше не зовёт.
/// Пройден ли шаг целиком (урок и все слова) - решает `ReadingLogic.completedSteps`.
@Observable
final class ReadingProgress {
    private enum Key {
        static let prefix = "reading."
        static let unlockSeen = "reading.unlockSeen"
        static let stepFinishedDay = "reading.stepFinishedDay"
        static let zikrPrefix = "reading.zikr."

        static func lesson(_ step: Int) -> String { "reading.step.\(step).done" }
        static func words(_ step: Int) -> String { "reading.words.\(step)" }
        static func zikrWords(_ zikr: ZikrID) -> String { "\(zikrPrefix)\(zikr.section.rawValue).\(zikr.number).words" }
        static func zikrRead(_ zikr: ZikrID) -> String { "\(zikrPrefix)\(zikr.section.rawValue).\(zikr.number).read" }
    }

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let calendar: Calendar
    @ObservationIgnored private let now: () -> Date

    /// Номера шагов с пройденным уроком.
    private(set) var lessonsDone: Set<Int>
    /// Шаг → сколько раз прочитано каждое слово (по порядку слов шага); нет шага - ничего не читали.
    private(set) var wordCounts: [Int: [Int]]
    /// Зикр итога → прочитанные слова и прочитан ли целиком; нет зикра - ничего не читали.
    private(set) var zikrs: [ZikrID: ZikrProgress]
    /// Последний открытый шаг, открытие которого уже показано; по умолчанию 1.
    private(set) var unlockSeen: Int
    /// День последнего пройденного шага (`ГГГГ-ММ-ДД`); испорченное значение - `nil`.
    private(set) var stepFinishedDay: String?

    /// `calendar` - пояс «сегодня» (в приложении - пояс устройства, как у азкаров), `now` - часы для тестов.
    init(defaults: UserDefaults = .standard, calendar: Calendar = .current, now: @escaping () -> Date = Date.init) {
        self.defaults = defaults
        self.calendar = calendar
        self.now = now
        let stored = defaults.dictionaryRepresentation().filter { $0.key.hasPrefix(Key.prefix) }
        lessonsDone = Self.readLessons(stored)
        wordCounts = Self.readWords(stored)
        zikrs = Self.readZikrs(stored)
        unlockSeen = defaults.object(forKey: Key.unlockSeen) as? Int ?? 1
        stepFinishedDay = defaults.string(forKey: Key.stepFinishedDay).flatMap { AzkarCalendarDay(key: $0)?.key }
    }

    /// Григорианская дата `date` (`ГГГГ-ММ-ДД`) в поясе `calendar` - не в календаре пользователя (хиджра, японский).
    nonisolated static func dayKey(of date: Date, in calendar: Calendar) -> String {
        var gregorian = Calendar(identifier: .gregorian)
        gregorian.timeZone = calendar.timeZone
        let components = gregorian.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year ?? 0, components.month ?? 0, components.day ?? 0)
    }

    /// Показано окно «Шаг пройден» - сегодня «Пора на урок» больше не зовёт (Android `markStepFinished`).
    func markStepFinished() {
        let today = Self.dayKey(of: now(), in: calendar)
        guard today != stepFinishedDay else { return }
        stepFinishedDay = today
        defaults.set(today, forKey: Key.stepFinishedDay)
    }

    /// Замок шага `step` «отщёлкнулся»; только растёт.
    func markUnlockSeen(_ step: Int) {
        guard step > unlockSeen else { return }
        unlockSeen = step
        defaults.set(step, forKey: Key.unlockSeen)
    }

    func markLessonDone(_ step: Int) {
        guard !lessonsDone.contains(step) else { return }
        lessonsDone.insert(step)
        defaults.set(true, forKey: Key.lesson(step))
    }

    /// Прочитал слово `index` шага `step` ещё раз; больше `repeat` не считается. `total` - слов в шаге.
    func readWord(step: Int, index: Int, total: Int, repeat: Int) {
        let stored = wordCounts[step] ?? []
        var counts = (0..<Swift.max(total, 0)).map { stored.indices.contains($0) ? stored[$0] : 0 }
        guard counts.indices.contains(index), counts[index] < `repeat` else { return }
        counts[index] += 1
        wordCounts[step] = counts
        defaults.set(counts.map(String.init).joined(separator: ","), forKey: Key.words(step))
    }

    /// Прочитал слово `word` зикра `zikr`; повторное касание не считается.
    func readZikrWord(_ zikr: ZikrID, word: Int) {
        var current = zikrs[zikr] ?? ZikrProgress()
        guard word >= 0, !current.words.contains(word) else { return }
        current.words.insert(word)
        zikrs[zikr] = current
        defaults.set(current.words.sorted().map(String.init).joined(separator: ","), forKey: Key.zikrWords(zikr))
    }

    /// Прочитал зикр `zikr` целиком.
    func markZikrRead(_ zikr: ZikrID) {
        var current = zikrs[zikr] ?? ZikrProgress()
        guard !current.isRead else { return }
        current.isRead = true
        zikrs[zikr] = current
        defaults.set(true, forKey: Key.zikrRead(zikr))
    }

    /// Сбросить шаг `step`: урок, прочитанные слова и зикры `zikrs` (зикры итога - у итога). Шаги после него
    /// снова закрыты, поэтому и «отщёлкнутый» замок - не дальше `step`: при новом открытии он отщёлкнется снова.
    func resetStep(_ step: Int, zikrs stepZikrs: [ZikrID] = []) {
        let started = stepZikrs.filter { zikrs[$0] != nil }
        guard lessonsDone.contains(step) || wordCounts[step] != nil || !started.isEmpty else { return }
        lessonsDone.remove(step)
        wordCounts[step] = nil
        defaults.removeObject(forKey: Key.lesson(step))
        defaults.removeObject(forKey: Key.words(step))
        for zikr in started {
            zikrs[zikr] = nil
            defaults.removeObject(forKey: Key.zikrWords(zikr))
            defaults.removeObject(forKey: Key.zikrRead(zikr))
        }
        let seen = Swift.min(unlockSeen, step)
        if seen != unlockSeen { unlockSeen = seen }
        defaults.set(seen, forKey: Key.unlockSeen)
    }

    /// Сбросить весь прогресс - все ключи `reading.step.…`, `reading.words.…` и любые `reading.zikr.…` (и
    /// старые, по номеру в шаге) и день пройденного шага. Настройки курса (`ReadingSettings`) не трогает.
    func reset() {
        let keys = defaults.dictionaryRepresentation().keys.filter(Self.isProgressKey)
        let hasDay = defaults.object(forKey: Key.stepFinishedDay) != nil
        guard !lessonsDone.isEmpty || !wordCounts.isEmpty || !zikrs.isEmpty || !keys.isEmpty || hasDay else { return }
        if !lessonsDone.isEmpty { lessonsDone = [] }
        if !wordCounts.isEmpty { wordCounts = [:] }
        if !zikrs.isEmpty { zikrs = [:] }
        if unlockSeen != 1 { unlockSeen = 1 }
        if stepFinishedDay != nil { stepFinishedDay = nil }
        keys.forEach(defaults.removeObject)
        defaults.removeObject(forKey: Key.unlockSeen)
        defaults.removeObject(forKey: Key.stepFinishedDay)
    }

    // MARK: - Разбор ключей

    private static func isProgressKey(_ key: String) -> Bool {
        lessonStep(key) != nil || wordsStep(key) != nil || key.hasPrefix(Key.zikrPrefix)
    }

    /// `reading.step.<N>.done` → N ≥ 1.
    private static func lessonStep(_ key: String) -> Int? {
        let parts = key.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4, parts[0] == "reading", parts[1] == "step", parts[3] == "done" else { return nil }
        return positive(parts[2])
    }

    /// `reading.words.<N>` → N ≥ 1.
    private static func wordsStep(_ key: String) -> Int? {
        let parts = key.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0] == "reading", parts[1] == "words" else { return nil }
        return positive(parts[2])
    }

    /// `reading.zikr.<раздел>.<N>.<words|read>` → зикр и вид; неизвестный раздел или номер < 1 - `nil`.
    private static func zikrKey(_ key: String) -> (zikr: ZikrID, kind: Substring)? {
        guard key.hasPrefix(Key.zikrPrefix) else { return nil }
        let parts = key.dropFirst(Key.zikrPrefix.count).split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3, let section = AzkarSection(rawValue: String(parts[0])), let number = positive(parts[1]) else {
            return nil
        }
        return (ZikrID(section: section, number: number), parts[2])
    }

    private static func positive(_ text: Substring) -> Int? {
        guard !text.isEmpty, text.allSatisfy(\.isASCIIDigit), let number = Int(text), number >= 1 else { return nil }
        return number
    }

    /// Значение не `true`, номер < 1 - пропустить.
    private static func readLessons(_ stored: [String: Any]) -> Set<Int> {
        Set(stored.compactMap { key, value in (value as? Bool) == true ? lessonStep(key) : nil })
    }

    /// Испорченное число - 0, отрицательное - 0; номер < 1 или не строка - пропустить.
    private static func readWords(_ stored: [String: Any]) -> [Int: [Int]] {
        var result: [Int: [Int]] = [:]
        for (key, value) in stored {
            guard let step = wordsStep(key), let text = value as? String else { continue }
            result[step] = text.split(separator: ",", omittingEmptySubsequences: false)
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
                .map { Swift.max(Int($0) ?? 0, 0) }
        }
        return result
    }

    /// Испорченный номер слова - пропустить; «прочитан» - только `true`.
    private static func readZikrs(_ stored: [String: Any]) -> [ZikrID: ZikrProgress] {
        var result: [ZikrID: ZikrProgress] = [:]
        for (key, value) in stored {
            guard let (zikr, kind) = zikrKey(key) else { continue }
            switch kind {
            case "words":
                let words = (value as? String ?? "").split(separator: ",")
                    .compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
                    .filter { $0 >= 0 }
                result[zikr, default: ZikrProgress()].words = Set(words)
            case "read" where (value as? Bool) == true:
                result[zikr, default: ZikrProgress()].isRead = true
            default:
                continue
            }
        }
        return result
    }
}

private nonisolated extension Character {
    var isASCIIDigit: Bool { isASCII && isNumber }
}
