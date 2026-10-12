import Testing
@testable import Inabah

/// Окно «Машаа Аллах!»: о каком разделе напомнить (решение пользователя 2026-10-11, Android `completionReminder`).
private let full = AzkarDayRecord(completed: 16, total: 16)
private let started = AzkarDayRecord(completed: 3, total: 16)

@MainActor
@Suite("Окно завершения: напоминание о втором разделе")
struct AzkarCompletionTests {
    @Test("После утренних — о вечерних, пока они не прочитаны полностью", arguments: [
        (AzkarDay?.none, AzkarSection?.some(.evening)),
        (AzkarDay(morning: full), .evening),
        (AzkarDay(morning: full, evening: started), .evening),
        (AzkarDay(morning: full, evening: full), nil),
    ])
    func afterMorning(day: AzkarDay?, expected: AzkarSection?) {
        #expect(AzkarSection.morning.completionReminder(day: day) == expected)
    }

    @Test("После вечерних — об утренних («завтра»), если они за этот день не прочитаны", arguments: [
        (AzkarDay?.none, AzkarSection?.some(.morning)),
        (AzkarDay(morning: started, evening: full), .morning),
        (AzkarDay(morning: full, evening: full), nil),
    ])
    func afterEvening(day: AzkarDay?, expected: AzkarSection?) {
        #expect(AzkarSection.evening.completionReminder(day: day) == expected)
    }

    @Test("Строки напоминания: вечерние — «не забудьте», утренние — «завтра»")
    func messages() {
        #expect(AzkarSection.evening.completionReminderMessage.key == "azkar.completion.nextEvening")
        #expect(AzkarSection.morning.completionReminderMessage.key == "azkar.completion.nextMorning")
    }
}
