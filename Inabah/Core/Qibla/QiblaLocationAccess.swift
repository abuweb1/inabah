import CoreLocation
import Observation

/// Доступ к местоположению для «Къиблы» (Android `LocationPermission.kt`). Отказ iOS сообщает сразу
/// (`.denied`) — флаг «уже спрашивали» и угадывание «больше не спрашивать» не нужны. Изменение в настройках
/// телефона приходит делегату само — и при возврате в приложение.
@Observable
final class QiblaLocationAccess: NSObject, CLLocationManagerDelegate {
    private(set) var access = LocationAccess.unknown

    @ObservationIgnored private let manager = CLLocationManager()
    /// Последняя проверка «Служб геолокации»; до неё считаем включёнными.
    @ObservationIgnored private var servicesEnabled = true
    @ObservationIgnored private var servicesCheck: Task<Void, Never>?

    override init() {
        super.init()
        manager.delegate = self
        update(status: manager.authorizationStatus, accuracy: manager.accuracyAuthorization)
    }

    /// Окно системы — только после нашего окна-пояснения.
    func request() {
        manager.requestWhenInUseAuthorization()
    }

    /// Статус Core Location → доступ экрана: «Точная геопозиция» выключена — приблизительное. Выключенные
    /// «Службы геолокации» iOS сообщает как отказ (`.denied`) — их отличает `servicesEnabled` (аудит 2026-10-27).
    nonisolated static func resolve(
        status: CLAuthorizationStatus,
        accuracy: CLAccuracyAuthorization,
        servicesEnabled: Bool = true
    ) -> LocationAccess {
        switch status {
        case .notDetermined: .notGranted
        case .authorizedWhenInUse, .authorizedAlways: accuracy == .reducedAccuracy ? .approximate : .precise
        default: servicesEnabled ? .denied : .servicesOff
        }
    }

    // Менеджер создан на главном потоке — делегат вызывается там же (и при переключении «Служб геолокации»).
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        let accuracy = manager.accuracyAuthorization
        MainActor.assumeIsolated {
            update(status: status, accuracy: accuracy)
        }
    }

    /// Отказ уточняется проверкой служб — она вне главного потока (там iOS считает её риском подвисания);
    /// до ответа доступ прежний (при открытии экрана — «проверяем»), чтобы не мелькал «Нет доступа».
    private func update(status: CLAuthorizationStatus, accuracy: CLAccuracyAuthorization) {
        servicesCheck?.cancel()
        guard status == .denied || status == .restricted else {
            assign(Self.resolve(status: status, accuracy: accuracy))
            return
        }
        servicesCheck = Task {
            let enabled = await Self.locationServicesEnabled()
            guard !Task.isCancelled else { return }
            servicesEnabled = enabled
            assign(Self.resolve(status: status, accuracy: accuracy, servicesEnabled: enabled))
        }
    }

    private func assign(_ next: LocationAccess) {
        if access != next { access = next }
    }

    @concurrent nonisolated private static func locationServicesEnabled() async -> Bool {
        CLLocationManager.locationServicesEnabled()
    }
}
