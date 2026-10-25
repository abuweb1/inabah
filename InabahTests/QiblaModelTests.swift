import Foundation
import Testing
@testable import Inabah

/// Модель экрана «Къибла»: источники — по жизни задачи экрана, новый поиск координат, новый трекер на запуск
/// (Android `QiblaModelTest`; поправки на склонение нет — азимут iOS уже истинный).
@MainActor
@Suite("Къибла: модель экрана")
struct QiblaModelTests {
    private static let grozny = LocationFix(latitude: 43.3178, longitude: 45.6986, time: 0)
    /// Къибла из Грозного — 194,45° от истинного севера.
    private static let groznyBearing = 194.45

    private final class FakeLocation: QiblaLocationSource {
        let statuses: [LocationStatus]
        var searches = 0
        var active = 0

        init(_ statuses: [LocationStatus]) { self.statuses = statuses }

        func locate() -> AsyncStream<LocationStatus> {
            searches += 1
            active += 1
            return AsyncStream { continuation in
                statuses.forEach { continuation.yield($0) }
                continuation.onTermination = { _ in
                    Task { @MainActor in self.active -= 1 }
                }
            }
        }
    }

    private final class FakeHeading: QiblaHeadingSource {
        let isAvailable: Bool
        let fails: Bool
        var listeners = 0
        private var continuation: AsyncThrowingStream<QiblaRawHeading, any Error>.Continuation?

        init(isAvailable: Bool = true, fails: Bool = false) {
            self.isAvailable = isAvailable
            self.fails = fails
        }

        func headings() -> AsyncThrowingStream<QiblaRawHeading, any Error> {
            AsyncThrowingStream { continuation in
                if fails {
                    continuation.finish(throwing: CancellationError())
                    return
                }
                listeners += 1
                self.continuation = continuation
                continuation.onTermination = { _ in
                    Task { @MainActor in self.listeners -= 1 }
                }
            }
        }

        func emit(azimuth: Double, time: TimeInterval = 1) {
            continuation?.yield(QiblaRawHeading(
                trueHeading: azimuth, magneticHeading: azimuth, headingAccuracy: 5, tiltDegrees: 2, time: time
            ))
        }
    }

    /// Ждёт условие, пока задачи модели обрабатывают события.
    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<400 where !condition() {
            try? await Task.sleep(for: .milliseconds(5))
        }
    }

    private static func guidance(_ model: QiblaModel) -> QiblaGuidance? {
        if case .compass(_, _, _, let guidance) = model.state { guidance } else { nil }
    }

    private static func pointer(_ model: QiblaModel) -> QiblaPointer? {
        if case .compass(_, _, let pointer, _) = model.state { pointer } else { nil }
    }

    @Test("Без доступа компас и поиск не запускаются")
    func noAccess() async {
        let location = FakeLocation([.found(Self.grozny)])
        let heading = FakeHeading()
        let model = QiblaModel(location: location, heading: heading, now: { 0 })

        await model.run(access: .notGranted)

        #expect(model.state == .needsPermission(denied: false))
        #expect(location.searches == 0)
        #expect(heading.listeners == 0)
    }

    @Test("Источники снимаются с задачей экрана, новый запуск ищет координаты заново")
    func sourcesFollowTask() async {
        let location = FakeLocation([.searching, .found(Self.grozny)])
        let heading = FakeHeading()
        let model = QiblaModel(location: location, heading: heading, now: { 0 })

        let first = Task { await model.run(access: .precise) }
        await waitUntil { Self.guidance(model) == .waiting && heading.listeners == 1 }
        #expect(location.active == 1)
        first.cancel()
        await first.value
        await waitUntil { heading.listeners == 0 && location.active == 0 }
        #expect(heading.listeners == 0, "ушли с экрана — компас отпущен")
        #expect(location.active == 0, "и поиск остановлен")

        let second = Task { await model.run(access: .precise) }
        await waitUntil { location.searches == 2 }
        #expect(location.searches == 2, "вернулись — координаты ищутся заново")
        second.cancel()
        await second.value
    }

    @Test("Обновить — новый поиск координат")
    func refresh() async {
        let location = FakeLocation([.found(Self.grozny)])
        let model = QiblaModel(location: location, heading: FakeHeading(), now: { 0 })
        let task = Task { await model.run(access: .precise) }
        await waitUntil { location.searches == 1 }

        model.refreshLocation()
        await waitUntil { location.searches == 2 }

        #expect(location.searches == 2)
        task.cancel()
        await task.value
    }

    @Test("Каждый запуск — новый трекер: метка без сглаживания, счёт и номер новые")
    func newTrackerPerRun() async throws {
        let heading = FakeHeading()
        let model = QiblaModel(location: FakeLocation([.found(Self.grozny)]), heading: heading, now: { 0 })

        let first = Task { await model.run(access: .precise) }
        await waitUntil { heading.listeners == 1 && Self.guidance(model) == .waiting }
        heading.emit(azimuth: Self.groznyBearing - 60, time: 1)
        await waitUntil { Self.pointer(model) != nil }
        let firstPointer = try #require(Self.pointer(model))
        first.cancel()
        await first.value

        let second = Task { await model.run(access: .precise) }
        await waitUntil { heading.listeners == 1 && Self.guidance(model) == .waiting }
        heading.emit(azimuth: Self.groznyBearing, time: 2)
        await waitUntil { Self.guidance(model) == .aligned }
        let pointer = try #require(Self.pointer(model))
        #expect(abs(pointer.offset) < 0.05, "не сглажено от прежних 60°")
        #expect(pointer.alignedEntries == 1)
        #expect(pointer.generation != firstPointer.generation)
        second.cancel()
        await second.value
    }

    @Test("Устаревшие координаты — на месте, но без подтверждения")
    func staleLocation() async {
        let heading = FakeHeading()
        let model = QiblaModel(
            location: FakeLocation([.found(Self.grozny)]),
            heading: heading,
            now: { QiblaTuning.default.locationStale + 1 }
        )
        let task = Task { await model.run(access: .precise) }
        await waitUntil { heading.listeners == 1 && Self.guidance(model) == .waiting }
        heading.emit(azimuth: Self.groznyBearing)
        await waitUntil { Self.guidance(model) == .locationUnconfirmed }

        #expect(Self.guidance(model) == .locationUnconfirmed)
        task.cancel()
        await task.value
    }

    @Test("Не удалось подписаться на компас — как без компаса")
    func headingFails() async {
        let model = QiblaModel(location: FakeLocation([.found(Self.grozny)]), heading: FakeHeading(fails: true), now: { 0 })
        let task = Task { await model.run(access: .precise) }
        await waitUntil { if case .noCompass = model.state { true } else { false } }

        guard case .noCompass = model.state else {
            Issue.record("ожидалось noCompass, получено \(model.state)")
            return
        }
        task.cancel()
        await task.value
    }

    @Test("Без компаса — направление числом")
    func noCompass() async {
        let model = QiblaModel(location: FakeLocation([.found(Self.grozny)]), heading: FakeHeading(isAvailable: false), now: { 0 })
        let task = Task { await model.run(access: .precise) }
        await waitUntil { if case .noCompass = model.state { true } else { false } }

        guard case .noCompass(let bearing, _) = model.state else {
            Issue.record("ожидалось noCompass, получено \(model.state)")
            return
        }
        #expect(abs(bearing - Self.groznyBearing) < 0.05)
        task.cancel()
        await task.value
    }
}
