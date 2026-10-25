import CoreLocation

/// Координаты через Core Location (Android `AndroidLocationSource`): последние известные, затем одни свежие
/// (`CLLocationUpdate.liveUpdates`, отмена задачи останавливает обновления). Вызывается только при выданном
/// доступе; координаты не сохраняются и не пишутся в журнал.
final class CoreLocationSource: QiblaLocationSource {
    private let manager = CLLocationManager()
    private let timeout: Duration

    init(timeout: TimeInterval = QiblaTuning.default.locationTimeout) {
        self.timeout = .seconds(timeout)
    }

    func locate() -> AsyncStream<LocationStatus> {
        let lastKnown = manager.location.map { LocationFix(location: $0) }
        let timeout = timeout
        return AsyncStream { continuation in
            // Вне главного актора: `locationServicesEnabled` на главном потоке iOS считает риском подвисания.
            let task = Task { @concurrent in
                let enabled = CLLocationManager.locationServicesEnabled()
                let statuses = QiblaLocating.statuses(enabled: enabled, lastKnown: lastKnown, timeout: timeout) {
                    await CoreLocationSource.current()
                }
                for await status in statuses {
                    continuation.yield(status)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Первые свежие координаты; `nil` — поиск отменён или обновления кончились.
    nonisolated private static func current() async -> LocationFix? {
        do {
            for try await update in CLLocationUpdate.liveUpdates() {
                if let location = update.location, location.horizontalAccuracy >= 0 {
                    return LocationFix(location: location)
                }
            }
        } catch {
            return nil
        }
        return nil
    }
}

extension LocationFix {
    /// Время координат — по монотонным часам модели (`systemUptime`): `CLLocation.timestamp` — дата.
    nonisolated init(location: CLLocation, uptime: TimeInterval = ProcessInfo.processInfo.systemUptime, now: Date = .now) {
        self.init(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            time: uptime - now.timeIntervalSince(location.timestamp)
        )
    }
}
