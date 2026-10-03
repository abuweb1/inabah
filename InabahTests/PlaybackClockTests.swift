import Foundation
import Testing
@testable import Inabah

@Suite("Часы позиции")
struct PlaybackClockTests {
    private let start = ContinuousClock.now

    private func at(_ seconds: Double) -> ContinuousClock.Instant {
        start + .milliseconds(Int(seconds * 1000))
    }

    private func running(duration: TimeInterval = 60, from position: TimeInterval = 0) -> PlaybackClock {
        var clock = PlaybackClock()
        clock.reset(duration: duration)
        clock.seek(to: position, at: at(0))
        clock.start(at: at(0))
        return clock
    }

    @Test("Идущие часы отсчитывают прошедшее время")
    func runningAdvances() {
        let clock = running(from: 5)
        #expect(clock.isRunning)
        #expect(clock.time(at: at(2)) == 7)
    }

    @Test("Пауза замораживает позицию")
    func pauseFreezes() {
        var clock = running()
        clock.pause(at: at(3))
        #expect(!clock.isRunning)
        #expect(clock.time(at: at(10)) == 3)
    }

    @Test("Отложенный старт: до паузы между записями позиция не растёт")
    func delayedStart() {
        var clock = PlaybackClock()
        clock.reset(duration: 60)
        clock.start(after: 3, at: at(0))
        #expect(clock.isRunning)
        #expect(clock.time(at: at(2)) == 0)
        #expect(clock.time(at: at(5)) == 2)
    }

    @Test("Скорость учитывается и меняется без скачка позиции")
    func rateChange() {
        var clock = running()
        clock.setRate(1.5, at: at(2))
        #expect(clock.time(at: at(2)) == 2)
        #expect(clock.time(at: at(4)) == 5)
    }

    @Test("Перемотка на ходу продолжает отсчёт от новой позиции")
    func seekWhileRunning() {
        var clock = running()
        clock.seek(to: 20, at: at(1))
        #expect(clock.time(at: at(3)) == 22)
    }

    @Test("Позиция не выходит за длительность")
    func clampedToDuration() {
        let clock = running(duration: 10)
        #expect(clock.time(at: at(30)) == 10)
    }

    @Test("Подстройка по плееру переякоривает часы")
    func syncReanchors() {
        var clock = running()
        clock.sync(position: 1.5, at: at(2))
        #expect(clock.time(at: at(3)) == 2.5)
    }

    @Test("Подстройка на момент фактического отложенного старта")
    func syncToActualDelayedStart() {
        var clock = PlaybackClock()
        clock.reset(duration: 60)
        clock.start(after: 3, at: at(0))
        // Плеер начал звук на секунду позже, чем рассчитывал главный актор.
        clock.sync(position: 0, at: at(4))
        #expect(clock.time(at: at(3.5)) == 0)
        #expect(clock.time(at: at(5)) == 1)
    }

    @Test("Стоп — в начало, длительность сохраняется")
    func stopResets() {
        var clock = running(duration: 42)
        clock.stop()
        #expect(!clock.isRunning)
        #expect(clock.time(at: at(5)) == 0)
        #expect(clock.duration == 42)
    }
}
