import CoreLocation
import Observation

/// Доступ к местоположению для «Къиблы» (Android `LocationPermission.kt`). Отказ iOS сообщает сразу
/// (`.denied`) — флаг «уже спрашивали» и угадывание «больше не спрашивать» не нужны. Изменение в настройках
/// телефона приходит делегату само — и при возврате в приложение.
@Observable
final class QiblaLocationAccess: NSObject, CLLocationManagerDelegate {
    private(set) var access = LocationAccess.unknown

    @ObservationIgnored private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        access = Self.resolve(status: manager.authorizationStatus, accuracy: manager.accuracyAuthorization)
    }

    /// Окно системы — только после нашего окна-пояснения.
    func request() {
        manager.requestWhenInUseAuthorization()
    }

    /// Статус Core Location → доступ экрана: «Точная геопозиция» выключена — приблизительное.
    nonisolated static func resolve(status: CLAuthorizationStatus, accuracy: CLAccuracyAuthorization) -> LocationAccess {
        switch status {
        case .notDetermined: .notGranted
        case .authorizedWhenInUse, .authorizedAlways: accuracy == .reducedAccuracy ? .approximate : .precise
        default: .denied
        }
    }

    // Менеджер создан на главном потоке — делегат вызывается там же.
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let next = Self.resolve(status: manager.authorizationStatus, accuracy: manager.accuracyAuthorization)
        MainActor.assumeIsolated {
            if access != next { access = next }
        }
    }
}
