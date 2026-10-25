import Foundation
import Testing
@testable import Inabah

/// Сглаживание метки, гистерезис совпадения, наклона и точности, сторона поворота, однократный виброотклик
/// (Android `QiblaTrackerTest`; время — секунды).
@Suite("Къибла: метка")
struct QiblaTrackerTests {
    private static let instant: QiblaTuning = {
        var tuning = QiblaTuning.default
        tuning.smoothing = 0
        return tuning
    }()

    private static func sample(
        _ time: TimeInterval,
        tilt: Double = 2,
        accuracy: Double? = 5,
        low: Bool = false,
        field: Double? = nil,
        expected: Double? = nil
    ) -> HeadingSample {
        HeadingSample(
            trueHeading: 0,
            accuracyDegrees: accuracy,
            lowAccuracy: low,
            tiltDegrees: tilt,
            time: time,
            fieldMicroTesla: field,
            expectedFieldMicroTesla: expected
        )
    }

    /// Показания с шагом `step`, как от датчика.
    private struct Feed {
        var tracker: QiblaTracker
        var step: TimeInterval = 0.02
        var time: TimeInterval = 0

        mutating func next(_ target: Double, tilt: Double = 2, accuracy: Double? = 5, low: Bool = false) -> QiblaPointer {
            time += step
            return tracker.update(target: target, sample: QiblaTrackerTests.sample(time, tilt: tilt, accuracy: accuracy, low: low))
        }
    }

    @Test("Первое показание — метка встаёт сразу")
    func firstReadingSnaps() {
        var tracker = QiblaTracker()
        let pointer = tracker.update(target: 40, sample: Self.sample(0))
        #expect(abs(pointer.offset - 40) < 1e-9)
        #expect(pointer.side == .right)
    }

    @Test("Через 0 и 360 метка идёт коротким путём, без оборота")
    func wrapsThroughNorth() {
        var feed = Feed(tracker: QiblaTracker())
        _ = feed.next(355)
        var previous = feed.next(355).angle
        for _ in 0..<50 {
            let angle = feed.next(5).angle
            #expect(abs(angle - previous) < 10, "шаг метки без скачка: \(previous) → \(angle)")
            previous = angle
        }
        #expect(abs(previous - 5) < 0.5, "дошла до 5° через 0°, угол без перескока")
    }

    @Test("Через 180 метка идёт коротким путём — за спиной, а не через указатель")
    func wrapsBehind() {
        var feed = Feed(tracker: QiblaTracker())
        _ = feed.next(170)
        _ = feed.next(170)
        for _ in 0..<50 {
            let offset = feed.next(-170).offset
            #expect(abs(offset) >= 169.9, "метка за спиной, не у указателя: \(offset)")
        }
        #expect(abs(feed.next(-170).offset + 170) < 0.5)
    }

    @Test("Сглаживание не зависит от частоты датчика")
    func smoothingIgnoresRate() {
        var slow = Feed(tracker: QiblaTracker(), step: 0.1)
        var fast = Feed(tracker: QiblaTracker(), step: 0.02)
        _ = slow.tracker.update(target: 0, sample: Self.sample(0))
        _ = fast.tracker.update(target: 0, sample: Self.sample(0))
        var a = slow.next(60)
        for _ in 0..<4 { a = slow.next(60) }
        var b = fast.next(60)
        for _ in 0..<24 { b = fast.next(60) }
        #expect(abs(a.offset - b.offset) < 1e-6, "за те же 500 мс — то же положение")
    }

    @Test("После паузы метка встаёт сразу")
    func snapsAfterPause() {
        var tracker = QiblaTracker()
        _ = tracker.update(target: 0, sample: Self.sample(0))
        let pointer = tracker.update(target: 120, sample: Self.sample(QiblaTuning.default.snapAfter + 0.001))
        #expect(abs(pointer.offset - 120) < 1e-9)
    }

    @Test("Вход в совпадение по порогу входа, выход — по порогу выхода")
    func alignmentHysteresis() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant))
        #expect(!feed.next(6).aligned, "6° — ещё не найдено")
        #expect(feed.next(4.9).aligned, "внутри 5° — найдено")
        #expect(feed.next(7.5).aligned, "до 8° держится")
        #expect(!feed.next(8.5).aligned, "за 8° — погасло")
        #expect(!feed.next(6).aligned, "между порогами снова не загорается")
    }

    @Test("Дрожание у границы даёт один вход и один виброотклик")
    func jitterSingleEntry() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant))
        var pointer = feed.next(4)
        #expect(pointer.alignedEntries == 1)
        for jitter in [7.0, 3, 7.9, 2, -7, 6.5, 4] { pointer = feed.next(jitter) }
        #expect(pointer.aligned)
        #expect(pointer.alignedEntries == 1, "дрожание внутри 8° не повторяет вход")
    }

    @Test("Новый вход — новый отклик, только после настоящего ухода")
    func rearmAfterRealLeave() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant), step: 0.1)
        #expect(feed.next(0).alignedEntries == 1)
        _ = feed.next(20)
        #expect(feed.next(0).alignedEntries == 1, "ушёл на 100 мс — отклик не повторяется")
        for _ in 0..<12 { _ = feed.next(30) }
        #expect(feed.next(0).alignedEntries == 2, "ушёл больше чем на секунду — новый отклик")
    }

    @Test("Ненадёжное показание не подтверждает и гасит совпадение")
    func unreliableReading() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant))
        #expect(!feed.next(0, low: true).aligned, "компас сам сообщил о низкой точности")
        #expect(feed.next(0).aligned)
        let pointer = feed.next(0, accuracy: 30)
        #expect(!pointer.aligned)
        #expect(pointer.lowAccuracy)
    }

    @Test("Наклон около 30 градусов не мигает и не повторяет отклик")
    func tiltHysteresis() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant), step: 0.1)
        #expect(feed.next(0, tilt: 28).aligned)
        let states = [31.0, 29, 30.5, 27, 26, 31, 24].map { feed.next(0, tilt: $0) }
        #expect(states.map(\.tilted) == [true, true, true, true, true, true, false], "наклонён с 31°, ровно — только ниже 25°")
        #expect(states.last?.aligned == true)
        #expect(states.last?.alignedEntries == 1, "за всё время — один отклик")
    }

    @Test("Оценка точности у порога не мигает")
    func accuracyHysteresis() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant))
        let flags = [21.0, 19, 18, 16, 14, 19].map { feed.next(0, accuracy: $0).lowAccuracy }
        #expect(flags == [true, true, true, true, false, false], "неточен хуже 20°, снова точен лучше 15°")
    }

    @Test("Степень поворота — Поверните, Правее, Немного правее — не мигает у границ")
    func turnAmountHysteresis() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant))
        #expect(feed.next(60).amount == .far)
        #expect(feed.next(44).amount == .far, "у 45° — запас 2°")
        #expect(feed.next(42).amount == .mid)
        #expect(feed.next(46).amount == .mid, "назад — тоже с запасом")
        #expect(feed.next(-30).amount == .mid, "слева — те же границы")
        #expect(feed.next(14).amount == .mid)
        #expect(feed.next(12).amount == .near)
        #expect(feed.next(16).amount == .near)
        #expect(feed.next(18).amount == .mid)
    }

    @Test("Помехи — сила поля далеко от ожидаемой в точке, с гистерезисом")
    func interference() {
        var tracker = QiblaTracker(tuning: Self.instant)
        var time: TimeInterval = 0
        func at(_ field: Double, expected: Double = 50) -> QiblaPointer {
            time += 0.001
            return tracker.update(target: 0, sample: Self.sample(time, field: field, expected: expected))
        }
        #expect(at(50).aligned)
        #expect(at(70).lowAccuracy, "+40% — металл или магнит рядом")
        #expect(at(64).lowAccuracy, "+28% — ещё помеха (снимается ниже 25%)")
        #expect(!at(58).lowAccuracy, "+16% — чисто")
        #expect(!at(23, expected: 23).lowAccuracy, "слабое поле Южной Атлантики — не помеха")
        #expect(!at(80, expected: 0).lowAccuracy, "ожидаемое неизвестно — не судим")
    }

    @Test("Координатам нельзя доверить — на месте, но без подтверждения и отклика")
    func heldByLocation() {
        var tracker = QiblaTracker(tuning: Self.instant, generation: 7)
        var pointer = tracker.update(target: 0, sample: Self.sample(0), locationConfirmable: false)
        #expect(!pointer.aligned)
        #expect(pointer.heldByLocation)
        #expect(pointer.alignedEntries == 0)
        #expect(pointer.generation == 7)
        pointer = tracker.update(target: 0, sample: Self.sample(0.001), locationConfirmable: true)
        #expect(pointer.aligned, "координаты обновились — подтверждение")
        #expect(pointer.alignedEntries == 1)
        #expect(!pointer.heldByLocation)
    }

    @Test("Около противоположного направления сторона не скачет")
    func sideHoldsNearOpposite() {
        var feed = Feed(tracker: QiblaTracker(tuning: Self.instant))
        #expect(feed.next(160).side == .right)
        for jitter in [178.0, -179, 175, -172, 179.5] {
            #expect(feed.next(jitter).side == .right, "у 180° (\(jitter)) — прежняя сторона")
        }
        #expect(feed.next(-165).side == .left, "ушла из зоны — сторона сменилась")
    }
}
