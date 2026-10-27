import Foundation

/// Кааба: 21°25′21″ с. ш., 39°49′34″ в. д. — Википедия «Kaaba»; совпадает с Kulturenvanteri (21.422500, 39.826167)
/// и LatLong.net (21.422487, 39.826206), расхождение — десятки метров (сверено 2026-10-24).
nonisolated enum Kaaba {
    static let latitude = 21.4225
    static let longitude = 39.8262
}

/// Направление на Каабу и углы (Android `QiblaMath.kt`).
nonisolated enum QiblaMath {
    /// Средний радиус Земли, км.
    private static let earthRadiusKm = 6371.0
    private static let fullTurn = 360.0
    private static let halfTurn = 180.0

    /// Начальный азимут большого круга на Каабу из точки, градусы по часовой стрелке от истинного севера, 0…360.
    static func bearing(latitude: Double, longitude: Double) -> Double {
        let from = radians(latitude)
        let to = radians(Kaaba.latitude)
        let dLon = radians(Kaaba.longitude - longitude)
        let y = sin(dLon) * cos(to)
        let x = cos(from) * sin(to) - sin(from) * cos(to) * cos(dLon)
        return normalize(degrees(atan2(y, x)))
    }

    /// Расстояние до Каабы по большому кругу, км (гаверсинус).
    static func distanceToKaabaKm(latitude: Double, longitude: Double) -> Double {
        let from = radians(latitude)
        let to = radians(Kaaba.latitude)
        let dLat = to - from
        let dLon = radians(Kaaba.longitude - longitude)
        let a = pow(sin(dLat / 2), 2) + cos(from) * cos(to) * pow(sin(dLon / 2), 2)
        return 2 * earthRadiusKm * asin(sqrt(min(max(a, 0), 1)))
    }

    /// Угол в 0…360 (360 → 0).
    static func normalize(_ degrees: Double) -> Double {
        let turned = degrees.truncatingRemainder(dividingBy: fullTurn)
        return turned < 0 ? turned + fullTurn : turned
    }

    /// Кратчайший поворот от `from` к `to`: −180…180, плюс — по часовой стрелке. Ровно противоположное — +180.
    static func signedDelta(from: Double, to: Double) -> Double {
        let delta = normalize(to - from)
        return delta > halfTurn ? delta - fullTurn : delta
    }

    private static func radians(_ degrees: Double) -> Double { degrees * .pi / halfTurn }
    private static func degrees(_ radians: Double) -> Double { radians * halfTurn / .pi }
}
