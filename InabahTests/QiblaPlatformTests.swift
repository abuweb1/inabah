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

    @Test(
        "Службы геолокации выключены — iOS сообщает отказ, экран — «Геолокация выключена»",
        arguments: [
            (CLAuthorizationStatus.denied, LocationAccess.servicesOff),
            (.restricted, .servicesOff),
            (.notDetermined, .notGranted),
            (.authorizedWhenInUse, .precise),
        ]
    )
    func servicesOff(status: CLAuthorizationStatus, expected: LocationAccess) {
        #expect(QiblaLocationAccess.resolve(status: status, accuracy: .fullAccuracy, servicesEnabled: false) == expected)
    }

    @Test(
        "Показание компаса: нет истинного азимута — магнитный и «неточен», отрицательная точность — «неточен»",
        arguments: [
            // trueHeading, magneticHeading, headingAccuracy → азимут, оценка точности, неточен
            (120.0, 115.0, 10.0, 120.0, 10.0 as Double?, false),
            (-1, 115, 10, 115, 10, true),
            (120, 115, -1, 120, nil, true),
            (0, 359, 0, 0, 0, false),
        ]
    )
    func rawHeading(
        trueHeading: Double, magneticHeading: Double, accuracy: Double,
        azimuth: Double, accuracyDegrees: Double?, low: Bool
    ) {
        let sample = QiblaRawHeading(
            trueHeading: trueHeading, magneticHeading: magneticHeading, headingAccuracy: accuracy, tiltDegrees: 0, time: 1
        ).sample
        #expect(sample.trueHeading == azimuth)
        #expect(sample.accuracyDegrees == accuracyDegrees)
        #expect(sample.lowAccuracy == low)
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
