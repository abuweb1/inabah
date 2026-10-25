import CoreLocation
import Foundation
import Testing
@testable import Inabah

/// Переводы данных Core Location / Core Motion для логики «Къиблы»: доступ, время координат, наклон.
@Suite("Къибла: данные iOS")
struct QiblaPlatformTests {
    @Test(
        "Статус Core Location — доступ экрана",
        arguments: [
            (CLAuthorizationStatus.notDetermined, CLAccuracyAuthorization.fullAccuracy, LocationAccess.notGranted),
            (.denied, .fullAccuracy, .denied),
            (.restricted, .fullAccuracy, .denied),
            (.authorizedWhenInUse, .fullAccuracy, .precise),
            (.authorizedWhenInUse, .reducedAccuracy, .approximate),
            (.authorizedAlways, .fullAccuracy, .precise),
            (.authorizedAlways, .reducedAccuracy, .approximate),
        ]
    )
    func access(status: CLAuthorizationStatus, accuracy: CLAccuracyAuthorization, expected: LocationAccess) {
        #expect(QiblaLocationAccess.resolve(status: status, accuracy: accuracy) == expected)
    }

    @Test("Время координат — в монотонных часах модели")
    func fixTime() {
        let now = Date(timeIntervalSinceReferenceDate: 1_000_000)
        let location = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 43.3178, longitude: 45.6986),
            altitude: 0,
            horizontalAccuracy: 10,
            verticalAccuracy: 10,
            timestamp: now.addingTimeInterval(-5)
        )
        let fix = LocationFix(location: location, uptime: 500, now: now)
        #expect(fix.time == 495, "получены 5 с назад")
        #expect(fix.latitude == 43.3178)
        #expect(fix.longitude == 45.6986)
    }

    @Test(
        "Наклон по тяжести: экраном вверх — 0, стоя — 90, экраном вниз — 180",
        arguments: [(-1.0, 0.0), (0, 90), (1, 180), (-1.0001, 0), (1.0001, 180)]
    )
    func tilt(gravityZ: Double, degrees: Double) {
        let tilt = QiblaTilt.degrees(gravityZ: gravityZ)
        #expect(!tilt.isNaN, "погрешность датчика за пределами ±1 g")
        #expect(abs(tilt - degrees) < 1e-9)
    }
}
