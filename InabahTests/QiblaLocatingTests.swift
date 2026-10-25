import Foundation
import Testing
@testable import Inabah

/// Один поиск координат: выключено, старые → свежие, таймаут со старыми и без (Android `LocateFlowTest`).
@Suite("Къибла: поиск координат")
struct QiblaLocatingTests {
    private static let old = LocationFix(latitude: 43.3, longitude: 45.7, time: 1)
    private static let fresh = LocationFix(latitude: 43.31, longitude: 45.69, time: 2)
    /// Короткий таймаут теста; «вечный» поиск — сон, который отменяется вместе с поиском.
    private static let timeout = Duration.milliseconds(50)
    private static let forever: @Sendable () async -> LocationFix? = {
        try? await Task.sleep(for: .seconds(60))
        return nil
    }

    private static func collect(_ stream: AsyncStream<LocationStatus>) async -> [LocationStatus] {
        var statuses: [LocationStatus] = []
        for await status in stream { statuses.append(status) }
        return statuses
    }

    @Test("Геолокация выключена")
    func off() async {
        let statuses = await Self.collect(QiblaLocating.statuses(enabled: false, lastKnown: Self.old, timeout: Self.timeout) {
            Self.fresh
        })
        #expect(statuses == [.off])
    }

    @Test("Сразу старые координаты, потом свежие")
    func oldThenFresh() async {
        let statuses = await Self.collect(QiblaLocating.statuses(enabled: true, lastKnown: Self.old, timeout: .seconds(5)) {
            try? await Task.sleep(for: .milliseconds(10))
            return Self.fresh
        })
        #expect(statuses == [.found(Self.old), .found(Self.fresh)])
    }

    @Test("Свежих нет — старые остаются, без ошибки")
    func oldStays() async {
        let statuses = await Self.collect(
            QiblaLocating.statuses(enabled: true, lastKnown: Self.old, timeout: Self.timeout, current: Self.forever)
        )
        #expect(statuses == [.found(Self.old)])
    }

    @Test("Ни старых, ни свежих — ищем, потом не нашлось")
    func searchingThenFailed() async {
        let statuses = await Self.collect(
            QiblaLocating.statuses(enabled: true, lastKnown: nil, timeout: Self.timeout, current: Self.forever)
        )
        #expect(statuses == [.searching, .failed])
    }
}
