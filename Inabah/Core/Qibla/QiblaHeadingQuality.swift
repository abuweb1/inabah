import Foundation

/// Показание компаса iOS (`CLHeading` и наклон из Core Motion) до решений трекера.
nonisolated struct QiblaRawHeading: Hashable, Sendable {
    /// `CLHeading.trueHeading`: отрицательный — истинного нет (нет координат у системы).
    var trueHeading: Double
    /// `CLHeading.magneticHeading` — запасной, пока истинного нет.
    var magneticHeading: Double
    /// `CLHeading.headingAccuracy`: отрицательная — показание ненадёжно.
    var headingAccuracy: Double
    var tiltDegrees: Double
    var time: TimeInterval

    /// Показание для трекера. На iOS силы поля Земли по мировой модели нет — помехи видны по точности.
    var sample: HeadingSample {
        HeadingSample(
            trueHeading: trueHeading >= 0 ? trueHeading : magneticHeading,
            accuracyDegrees: headingAccuracy >= 0 ? headingAccuracy : nil,
            lowAccuracy: QiblaHeadingQuality.isLow(trueHeading: trueHeading, headingAccuracy: headingAccuracy),
            tiltDegrees: tiltDegrees,
            time: time
        )
    }
}

/// Компас iOS сам сообщает о низкой точности (вместо статусов датчика Android `sensorReportsLowAccuracy`).
nonisolated enum QiblaHeadingQuality {
    /// Нет истинного азимута (только магнитный) или точность отрицательная — «Компас неточен».
    static func isLow(trueHeading: Double, headingAccuracy: Double) -> Bool {
        trueHeading < 0 || headingAccuracy < 0
    }
}
