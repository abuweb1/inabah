import Testing
@testable import Inabah

@Suite("Барабан выбора времени")
struct WheelRowsTests {
    private let minutes = WheelRows(count: 60, looping: true)
    private let hours = WheelRows(count: 24, looping: true)
    private let dayPeriods = WheelRows(count: 2, looping: false)

    @Test("Значение по кругу: строка выбранного — в середине списка", arguments: [0, 5, 59])
    func startsInMiddle(value: Int) {
        let index = minutes.index(of: value)

        #expect(minutes.value(at: index) == value)
        #expect(index > minutes.size / 3 && index < minutes.size * 2 / 3)
    }

    @Test("По кругу за 59 идёт 00, перед 00 — 59")
    func wrapsAround() {
        let index = minutes.index(of: 59)

        #expect(minutes.value(at: index + 1) == 0)
        #expect(minutes.value(at: minutes.index(of: 0) - 1) == 59)
    }

    @Test("Кратчайший путь по кругу", arguments: [
        (23, 1, 2),
        (1, 23, -2),
        (5, 10, 5),
        (10, 10, 0),
    ])
    func shortestDistance(from: Int, to: Int, expected: Int) {
        #expect(hours.distance(from: from, to: to) == expected)
    }

    @Test("AM / PM без круга: сдвиг не дальше крайних")
    func clampsWithoutLoop() {
        #expect(dayPeriods.size == 2)
        #expect(dayPeriods.shifted(1, by: 1) == 1)
        #expect(dayPeriods.shifted(0, by: -1) == 0)
        #expect(dayPeriods.distance(from: 0, to: 1) == 1)
        #expect(dayPeriods.value(at: 5) == 1)
    }
}
