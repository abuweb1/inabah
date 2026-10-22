import Foundation
import Observation

/// Настройки чтения, общие для азкаров и хадисов. Сохраняются между запусками.
@Observable
final class ReadingSettings {
    /// Верхняя граница — на 4 шага больше прототипа (32): по просьбе пользователя для крупного чтения.
    static let arabicFontSizeRange: ClosedRange<Double> = 14...40
    static let arabicFontSizeStep: Double = 2
    static let defaultArabicFontSize: Double = 21

    private enum Key {
        static let arabicFontSize = "reading.arabicFontSize"
        static let hadithArabicCollapsed = "reading.hadithArabicCollapsed"
        static let transliterationHidden = "reading.transliterationHidden"
        static let allStepsOpen = "reading.allStepsOpen"
        static let introPrefix = "reading.intro."
        static let zoomLearned = "reading.zoomLearned"
    }

    @ObservationIgnored private let defaults: UserDefaults

    private(set) var arabicFontSize: Double {
        didSet { defaults.set(arabicFontSize, forKey: Key.arabicFontSize) }
    }

    /// Хадис открывается со свёрнутым арабским текстом (как на Android, ключ тот же). Задаёт только
    /// начальное состояние страницы: свернуть и раскрыть можно всегда нажатием на строку с номером.
    private(set) var hadithArabicCollapsed: Bool {
        didSet { defaults.set(hadithArabicCollapsed, forKey: Key.hadithArabicCollapsed) }
    }

    /// «Учимся читать»: транскрипция слов скрыта (глаз в «Читаем слова», переключатель в настройках «Махраджа»).
    private(set) var transliterationHidden: Bool {
        didSet { defaults.set(transliterationHidden, forKey: Key.transliterationHidden) }
    }

    /// «Учимся читать»: все шаги открыты, без замков (настройки «Махраджа»); прогресс не трогает.
    private(set) var allStepsOpen: Bool {
        didSet { defaults.set(allStepsOpen, forKey: Key.allStepsOpen) }
    }

    /// «Учимся читать»: слово уже увеличивали долгим нажатием - плитки больше не «зовут» в тишине.
    private(set) var zoomLearned: Bool {
        didSet { defaults.set(zoomLearned, forKey: Key.zoomLearned) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let stored = defaults.object(forKey: Key.arabicFontSize) as? Double
        arabicFontSize = (stored ?? Self.defaultArabicFontSize).clamped(to: Self.arabicFontSizeRange)
        hadithArabicCollapsed = defaults.bool(forKey: Key.hadithArabicCollapsed)
        transliterationHidden = defaults.bool(forKey: Key.transliterationHidden)
        allStepsOpen = defaults.bool(forKey: Key.allStepsOpen)
        zoomLearned = defaults.bool(forKey: Key.zoomLearned)
    }

    /// Надпись-подсказку экрана курса (`lesson.N`, `words`…) уже показывали. Читается один раз при входе -
    /// не наблюдается.
    func introSeen(_ key: String) -> Bool {
        defaults.bool(forKey: Key.introPrefix + key)
    }

    func markIntroSeen(_ key: String) {
        guard !introSeen(key) else { return }
        defaults.set(true, forKey: Key.introPrefix + key)
    }

    func markZoomLearned() {
        guard !zoomLearned else { return }
        zoomLearned = true
    }

    /// «Показать подсказки снова»: все `reading.intro.…` и `reading.zoomLearned`. Прогресс не трогает.
    func resetIntros() {
        defaults.dictionaryRepresentation().keys
            .filter { $0.hasPrefix(Key.introPrefix) }
            .forEach(defaults.removeObject)
        if zoomLearned { zoomLearned = false }
        defaults.removeObject(forKey: Key.zoomLearned)
    }

    func setHadithArabicCollapsed(_ value: Bool) {
        guard value != hadithArabicCollapsed else { return }
        hadithArabicCollapsed = value
    }

    func setTransliterationHidden(_ value: Bool) {
        guard value != transliterationHidden else { return }
        transliterationHidden = value
    }

    func setAllStepsOpen(_ value: Bool) {
        guard value != allStepsOpen else { return }
        allStepsOpen = value
    }

    var canIncreaseArabicFontSize: Bool { arabicFontSize < Self.arabicFontSizeRange.upperBound }
    var canDecreaseArabicFontSize: Bool { arabicFontSize > Self.arabicFontSizeRange.lowerBound }

    func increaseArabicFontSize() { changeArabicFontSize(by: Self.arabicFontSizeStep) }
    func decreaseArabicFontSize() { changeArabicFontSize(by: -Self.arabicFontSizeStep) }

    private func changeArabicFontSize(by delta: Double) {
        arabicFontSize = (arabicFontSize + delta).clamped(to: Self.arabicFontSizeRange)
    }
}
