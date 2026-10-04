import Synchronization

/// Флаг «наблюдатель уведомлён»: `onChange` у `withObservationTracking` — `@Sendable`.
final class NotificationFlag: Sendable {
    private let state = Mutex(false)

    var value: Bool { state.withLock { $0 } }

    func set() {
        state.withLock { $0 = true }
    }
}
