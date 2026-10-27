import Foundation

/// Один поиск координат без Core Location (Android `locateFlow`): геолокация выключена — `.off`; иначе сразу
/// последние известные (или «ищем»), затем свежие; не нашлось за `timeout` — старые остаются, а без старых —
/// `.failed`. Остановка чтения потока отменяет поиск.
nonisolated enum QiblaLocating {
    static func statuses(
        enabled: Bool,
        lastKnown: LocationFix?,
        timeout: Duration,
        current: @escaping @Sendable () async -> LocationFix?
    ) -> AsyncStream<LocationStatus> {
        AsyncStream { continuation in
            guard enabled else {
                continuation.yield(.off)
                continuation.finish()
                return
            }
            continuation.yield(lastKnown.map(LocationStatus.found) ?? .searching)
            let search = Task {
                let fresh = await firstFix(timeout: timeout, current: current)
                if let fresh {
                    continuation.yield(.found(fresh))
                } else if lastKnown == nil, !Task.isCancelled {
                    continuation.yield(.failed)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in search.cancel() }
        }
    }

    /// Свежие координаты или `nil`, если их нет за `timeout` (поиск отменяется).
    private static func firstFix(
        timeout: Duration,
        current: @escaping @Sendable () async -> LocationFix?
    ) async -> LocationFix? {
        await withTaskGroup(of: LocationFix?.self) { group in
            group.addTask { await current() }
            group.addTask {
                try? await Task.sleep(for: timeout)
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }
}
