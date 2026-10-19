import Foundation
import Testing
@testable import Inabah

/// «Учимся читать»: пройденные уроки, счёт слов, зикры итога (как Android `ReadingProgressTest`).
@MainActor
@Suite("Курс «Учимся читать»: прогресс")
struct ReadingProgressTests {
    private static let morning6 = ZikrID(section: .morning, number: 6)
    private static let evening1 = ZikrID(section: .evening, number: 1)

    private let storage: IsolatedDefaults
    private var defaults: UserDefaults { storage.defaults }

    init() throws {
        storage = try IsolatedDefaults("ReadingProgressTests")
    }

    @Test("Пройденный урок переживает перезапуск в ключе reading.step.N.done")
    func lessonDone() {
        let progress = ReadingProgress(defaults: defaults)

        progress.markLessonDone(2)
        progress.markLessonDone(2)
        progress.markLessonDone(1)

        #expect(progress.lessonsDone == [1, 2])
        #expect(defaults.object(forKey: "reading.step.2.done") as? Bool == true)
        #expect(ReadingProgress(defaults: defaults).lessonsDone == [1, 2], "после перезапуска")
    }

    @Test("Слово считается до repeat раз и переживает перезапуск")
    func readWord() {
        let progress = ReadingProgress(defaults: defaults)

        (0..<12).forEach { _ in progress.readWord(step: 2, index: 1, total: 3, repeat: 10) }
        progress.readWord(step: 2, index: 0, total: 3, repeat: 10)
        progress.readWord(step: 2, index: 7, total: 3, repeat: 10)

        #expect(progress.wordCounts[2] == [1, 10, 0])
        #expect(defaults.string(forKey: "reading.words.2") == "1,10,0")
        #expect(ReadingProgress(defaults: defaults).wordCounts[2] == [1, 10, 0], "после перезапуска")
    }

    @Test("Сброс удаляет уроки и слова")
    func resetLessonsAndWords() {
        let progress = ReadingProgress(defaults: defaults)
        progress.markLessonDone(2)
        progress.readWord(step: 2, index: 0, total: 2, repeat: 10)

        progress.reset()

        #expect(progress.lessonsDone.isEmpty)
        #expect(progress.wordCounts.isEmpty)
        #expect(defaults.object(forKey: "reading.step.2.done") == nil)
        #expect(defaults.object(forKey: "reading.words.2") == nil)
    }

    @Test("Зикр итога - по разделу и номеру, слова по одному разу, «прочитал целиком», переживает перезапуск")
    func zikrProgress() {
        let progress = ReadingProgress(defaults: defaults)

        progress.readZikrWord(Self.morning6, word: 2)
        progress.readZikrWord(Self.morning6, word: 2)
        progress.readZikrWord(Self.morning6, word: 0)
        progress.readZikrWord(Self.morning6, word: -1)
        progress.markZikrRead(Self.morning6)

        let expected = ZikrProgress(words: [0, 2], isRead: true)
        #expect(progress.zikrs[Self.morning6] == expected)
        #expect(progress.zikrs[Self.evening1] == nil, "другой зикр не задет")
        #expect(ReadingProgress(defaults: defaults).zikrs[Self.morning6] == expected, "после перезапуска")
        #expect(defaults.string(forKey: "reading.zikr.morning.6.words") == "0,2")
        #expect(defaults.object(forKey: "reading.zikr.morning.6.read") as? Bool == true)
    }

    @Test("Сброс удаляет и прогресс зикров итога")
    func resetZikrs() {
        let progress = ReadingProgress(defaults: defaults)
        progress.readZikrWord(Self.evening1, word: 0)
        progress.markZikrRead(Self.evening1)

        progress.reset()

        #expect(progress.zikrs.isEmpty)
        #expect(defaults.object(forKey: "reading.zikr.evening.1.words") == nil)
        #expect(defaults.object(forKey: "reading.zikr.evening.1.read") == nil)
    }

    @Test("Ключи зикров по номеру в шаге и неизвестный раздел не читаются, сброс их удаляет")
    func legacyZikrKeys() {
        defaults.set("0,1", forKey: "reading.zikr.0.words")
        defaults.set(true, forKey: "reading.zikr.0.read")
        defaults.set("0", forKey: "reading.zikr.night.3.words")
        defaults.set("0", forKey: "reading.zikr.morning.0.words")
        let progress = ReadingProgress(defaults: defaults)
        #expect(progress.zikrs.isEmpty)

        progress.reset()

        #expect(!defaults.dictionaryRepresentation().keys.contains { $0.hasPrefix("reading.zikr.") })
    }

    @Test("Испорченные значения: число слова - 0, не «true» - урок не пройден")
    func corruptValues() {
        defaults.set("3,x,-2, 4", forKey: "reading.words.5")
        defaults.set(false, forKey: "reading.step.4.done")
        defaults.set(true, forKey: "reading.step.0.done")

        let progress = ReadingProgress(defaults: defaults)

        #expect(progress.wordCounts[5] == [3, 0, 0, 4])
        #expect(progress.lessonsDone.isEmpty)
    }

    @Test("Сброс шага удаляет только его урок, слова и зикры, замок откатывается к нему")
    func resetStep() {
        let progress = ReadingProgress(defaults: defaults)
        progress.markLessonDone(2)
        progress.readWord(step: 2, index: 0, total: 2, repeat: 10)
        progress.markLessonDone(3)
        progress.readWord(step: 3, index: 1, total: 2, repeat: 10)
        progress.markLessonDone(13)
        progress.readZikrWord(Self.morning6, word: 0)
        progress.markZikrRead(Self.evening1)
        progress.markUnlockSeen(5)

        progress.resetStep(3)
        progress.resetStep(13, zikrs: [Self.morning6])

        #expect(progress.lessonsDone == [2])
        #expect(Set(progress.wordCounts.keys) == [2])
        #expect(Set(progress.zikrs.keys) == [Self.evening1], "зикр другого шага не задет")
        #expect(progress.unlockSeen == 3)
        let restarted = ReadingProgress(defaults: defaults)
        #expect(restarted.lessonsDone == [2], "после перезапуска")
        #expect(Set(restarted.wordCounts.keys) == [2])
        #expect(Set(restarted.zikrs.keys) == [Self.evening1])
        #expect(restarted.unlockSeen == 3)
        #expect(defaults.object(forKey: "reading.zikr.morning.6.words") == nil)
    }

    @Test("Сброс шага без прогресса ничего не меняет")
    func resetEmptyStep() {
        let progress = ReadingProgress(defaults: defaults)
        progress.markUnlockSeen(5)

        progress.resetStep(2, zikrs: [Self.morning6])

        #expect(progress.unlockSeen == 5)
        #expect(ReadingProgress(defaults: defaults).unlockSeen == 5)
    }

    @Test("Замок «отщёлкнулся» - только растёт, по умолчанию 1, полный сброс возвращает 1")
    func unlockSeen() {
        let progress = ReadingProgress(defaults: defaults)
        #expect(progress.unlockSeen == 1)

        progress.markUnlockSeen(4)
        progress.markUnlockSeen(2)
        #expect(progress.unlockSeen == 4)
        #expect(defaults.integer(forKey: "reading.unlockSeen") == 4)

        progress.markLessonDone(1)
        progress.reset()
        #expect(progress.unlockSeen == 1)
        #expect(defaults.object(forKey: "reading.unlockSeen") == nil)
    }
}
