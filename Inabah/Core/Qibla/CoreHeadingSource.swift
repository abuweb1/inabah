import CoreLocation
import CoreMotion

/// Направление телефона — `CLHeading` (истинный азимут, iOS сама сводит магнитометр, акселерометр и гироскоп),
/// наклон — Core Motion (Android `AndroidHeadingSource`). Датчики работают, только пока поток читают.
final class CoreHeadingSource: QiblaHeadingSource {
    var isAvailable: Bool { CLLocationManager.headingAvailable() }

    func headings() -> AsyncThrowingStream<QiblaRawHeading, any Error> {
        // Только последнее показание, как `conflate` Android.
        AsyncThrowingStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            // Не подписались — ошибка: экран покажет «Компаса нет», а не вечное «Настраиваем компас».
            guard CLLocationManager.headingAvailable() else {
                continuation.finish(throwing: QiblaHeadingError.unavailable)
                return
            }
            let session = HeadingSession { continuation.yield($0) }
            session.start()
            continuation.onTermination = { _ in
                Task { @MainActor in session.stop() }
            }
        }
    }
}

nonisolated enum QiblaHeadingError: Error {
    case unavailable
}

/// Наклон экрана от горизонтали по вектору тяжести Core Motion (в g): 0 — лежит экраном вверх
/// (`gravity.z = −1`), как `acos(R[8])` Android.
nonisolated enum QiblaTilt {
    static func degrees(gravityZ: Double) -> Double {
        acos(min(max(-gravityZ, -1), 1)) * 180 / .pi
    }
}

/// Одна подписка: свой менеджер компаса и Core Motion; `stop()` снимает оба.
private final class HeadingSession: NSObject, CLLocationManagerDelegate {
    private static let motionInterval: TimeInterval = 1.0 / 30

    private let manager = CLLocationManager()
    private let motion = CMMotionManager()
    private let emit: (QiblaRawHeading) -> Void
    /// Последний наклон; без Core Motion — 0 (ровно).
    private var tilt = 0.0

    init(emit: @escaping (QiblaRawHeading) -> Void) {
        self.emit = emit
    }

    func start() {
        manager.delegate = self
        manager.headingFilter = kCLHeadingFilterNone
        manager.headingOrientation = .portrait
        manager.startUpdatingHeading()
        guard motion.isDeviceMotionAvailable else { return }
        motion.deviceMotionUpdateInterval = Self.motionInterval
        motion.startDeviceMotionUpdates(to: .main) { [weak self] data, _ in
            guard let gravityZ = data?.gravity.z else { return }
            self?.tilt = QiblaTilt.degrees(gravityZ: gravityZ)
        }
    }

    func stop() {
        manager.stopUpdatingHeading()
        manager.delegate = nil
        motion.stopDeviceMotionUpdates()
    }

    // Менеджер создан на главном потоке — делегат вызывается там же.
    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let trueHeading = newHeading.trueHeading
        let magneticHeading = newHeading.magneticHeading
        let accuracy = newHeading.headingAccuracy
        MainActor.assumeIsolated {
            emit(QiblaRawHeading(
                trueHeading: trueHeading,
                magneticHeading: magneticHeading,
                headingAccuracy: accuracy,
                tiltDegrees: tilt,
                time: ProcessInfo.processInfo.systemUptime
            ))
        }
    }

    /// Системное окно калибровки не показываем: калибровку ведёт наша подсказка «Компас неточен»
    /// (решение пользователя 2026-10-25 — поведение задаём мы, не система).
    nonisolated func locationManagerShouldDisplayHeadingCalibration(_ manager: CLLocationManager) -> Bool {
        false
    }

    /// `CLError.headingFailure` (сильные помехи) — не конец: показания продолжатся, неточность видна по
    /// `headingAccuracy`.
    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: any Error) {}
}
