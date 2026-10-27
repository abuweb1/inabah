import Foundation
import Testing
@testable import Inabah

/// Один отклик на вход в совпадение — и после ↻ / возврата на экран; градусы для VoiceOver; качество компаса iOS.
@Suite("Къибла: отклик и озвучка")
struct QiblaHapticsTests {
    private static func pointer(entries: Int, generation: Int) -> QiblaPointer {
        QiblaPointer(offset: 0, angle: 0, side: .right, aligned: entries > 0, alignedEntries: entries, generation: generation)
    }

    /// Обёртка: `#expect` не принимает изменяющий вызов на локальной переменной.
    private final class Gate {
        private var haptics = AlignmentHaptics()

        func shouldVibrate(_ pointer: QiblaPointer) -> Bool {
            haptics.shouldVibrate(pointer)
        }
    }

    @Test("Отклик — на рост счёта, повтор того же значения молчит")
    func vibratesOnGrowth() {
        let gate = Gate()
        #expect(!gate.shouldVibrate(Self.pointer(entries: 0, generation: 1)))
        #expect(gate.shouldVibrate(Self.pointer(entries: 1, generation: 1)))
        #expect(!gate.shouldVibrate(Self.pointer(entries: 1, generation: 1)), "дрожание внутри совпадения")
        #expect(gate.shouldVibrate(Self.pointer(entries: 2, generation: 1)), "новый вход")
    }

    @Test("После перезапуска старое значение не даёт отклик, первый вход новой подписки — даёт")
    func newSubscription() {
        let gate = Gate()
        #expect(gate.shouldVibrate(Self.pointer(entries: 1, generation: 1)))
        // ↻: экран ещё видит прежнее значение той же подписки.
        #expect(!gate.shouldVibrate(Self.pointer(entries: 1, generation: 1)))
        // Новая подписка начинается с нуля и сразу в совпадении.
        #expect(gate.shouldVibrate(Self.pointer(entries: 1, generation: 2)))
        #expect(!gate.shouldVibrate(Self.pointer(entries: 1, generation: 2)))
    }

    @Test("Градусы для VoiceOver — шагом 5, не 0, без прыжков у границы")
    func spokenDegrees() {
        #expect(QiblaSpokenTurn.degrees(distance: 41, previous: nil) == 40)
        #expect(QiblaSpokenTurn.degrees(distance: 1, previous: nil) == 5, "не «поверните на 0°»")
        let first = QiblaSpokenTurn.degrees(distance: 12.4, previous: nil)
        #expect(first == 10)
        #expect(QiblaSpokenTurn.degrees(distance: 12.6, previous: first) == 10, "у 12,5° держится прежнее")
        #expect(QiblaSpokenTurn.degrees(distance: 13.5, previous: first) == 10)
        #expect(QiblaSpokenTurn.degrees(distance: 14, previous: first) == 15, "ушло на три четверти шага — новое значение")
    }

    @Test("Компас iOS: нет истинного азимута или точность отрицательная — неточен")
    func headingQuality() {
        #expect(QiblaHeadingQuality.isLow(trueHeading: -1, headingAccuracy: 5), "только магнитный")
        #expect(QiblaHeadingQuality.isLow(trueHeading: 120, headingAccuracy: -1), "ненадёжно")
        #expect(!QiblaHeadingQuality.isLow(trueHeading: 120, headingAccuracy: 5))
    }

    @Test("Показание iOS: без истинного — магнитный, отрицательная точность — без оценки")
    func rawSample() {
        let raw = QiblaRawHeading(trueHeading: -1, magneticHeading: 100, headingAccuracy: -1, tiltDegrees: 3, time: 1)
        #expect(raw.sample.trueHeading == 100)
        #expect(raw.sample.accuracyDegrees == nil)
        #expect(raw.sample.lowAccuracy)
        #expect(raw.sample.expectedFieldMicroTesla == nil, "мировой модели поля на iOS нет")
    }
}
