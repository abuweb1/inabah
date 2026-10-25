import Foundation
import Testing
@testable import Inabah

/// Направление на Каабу (Android `QiblaMathTest`). Ожидания посчитаны независимо — через векторы на сфере —
/// и совпадают с опубликованными: Нью-Йорк 58,48°, Лондон 118,99°, Джакарта 295,15°.
@Suite("Къибла: направление")
struct QiblaMathTests {
    struct City: CustomTestStringConvertible, Sendable {
        let name: String
        let latitude: Double
        let longitude: Double
        let bearing: Double

        var testDescription: String { name }
    }

    /// Контрольные города и города по регионам пользователей (`docs/android/03-domain-logic.md` 3.10).
    static let cities = [
        City(name: "Нью-Йорк", latitude: 40.7128, longitude: -74.0060, bearing: 58.48),
        City(name: "Лондон", latitude: 51.5074, longitude: -0.1278, bearing: 118.99),
        City(name: "Джакарта", latitude: -6.2088, longitude: 106.8456, bearing: 295.15),
        City(name: "Москва", latitude: 55.7558, longitude: 37.6173, bearing: 176.36),
        City(name: "Грозный", latitude: 43.3178, longitude: 45.6986, bearing: 194.45),
        City(name: "Берлин", latitude: 52.5200, longitude: 13.4050, bearing: 136.68),
        City(name: "Франкфурт", latitude: 50.1109, longitude: 8.6821, bearing: 128.07),
        City(name: "Мюнхен", latitude: 48.1351, longitude: 11.5820, bearing: 129.79),
        City(name: "Париж", latitude: 48.8566, longitude: 2.3522, bearing: 119.16),
        City(name: "Страсбург", latitude: 48.5734, longitude: 7.7521, bearing: 125.28),
        City(name: "Брюссель", latitude: 50.8503, longitude: 4.3517, bearing: 123.48),
        City(name: "Вена", latitude: 48.2082, longitude: 16.3738, bearing: 136.71),
        City(name: "Стамбул", latitude: 41.0082, longitude: 28.9784, bearing: 151.62),
        City(name: "Анкара", latitude: 39.9334, longitude: 32.8597, bearing: 160.17),
        City(name: "Казань", latitude: 55.7963, longitude: 49.1088, bearing: 195.15),
        City(name: "Махачкала", latitude: 42.9849, longitude: 47.5047, bearing: 198.97),
        City(name: "Алматы", latitude: 43.2389, longitude: 76.8897, bearing: 246.60),
        City(name: "Астана", latitude: 51.1694, longitude: 71.4491, bearing: 231.48),
        City(name: "Ташкент", latitude: 41.2995, longitude: 69.2401, bearing: 240.30),
        City(name: "Сидней", latitude: -33.8688, longitude: 151.2093, bearing: 277.50),
        City(name: "Кейптаун", latitude: -33.9249, longitude: 18.4241, bearing: 23.35),
        City(name: "Токио", latitude: 35.6762, longitude: 139.6503, bearing: 293.00),
    ]

    @Test("Направление из городов", arguments: cities)
    func bearing(city: City) {
        #expect(abs(QiblaMath.bearing(latitude: city.latitude, longitude: city.longitude) - city.bearing) < 0.05)
    }

    @Test("В километре к северу от Каабы — строго на юг")
    func justNorthOfKaaba() {
        #expect(abs(QiblaMath.bearing(latitude: 21.4315, longitude: Kaaba.longitude) - 180) < 0.05)
    }

    @Test("Расстояние до Каабы")
    func distance() {
        #expect(abs(QiblaMath.distanceToKaabaKm(latitude: 43.3178, longitude: 45.6986) - 2495) < 5)
        #expect(QiblaMath.distanceToKaabaKm(latitude: Kaaba.latitude, longitude: Kaaba.longitude) < 1e-6)
    }

    @Test("Нормализация угла в 0–360")
    func normalize() {
        #expect(QiblaMath.normalize(360) == 0)
        #expect(QiblaMath.normalize(-1) == 359)
        #expect(QiblaMath.normalize(730) == 10)
        #expect(QiblaMath.normalize(-180) == 180)
    }

    @Test("Кратчайший поворот через 0 и 360")
    func signedDelta() {
        #expect(abs(QiblaMath.signedDelta(from: 359, to: 1) - 2) < 1e-9, "через север по часовой")
        #expect(abs(QiblaMath.signedDelta(from: 1, to: 359) + 2) < 1e-9, "через север против часовой")
        #expect(abs(QiblaMath.signedDelta(from: 90, to: 0) + 90) < 1e-9)
        #expect(QiblaMath.signedDelta(from: 0, to: 180) == 180, "противоположное — +180")
        #expect(abs(QiblaMath.signedDelta(from: 10, to: 200) + 170) < 1e-9)
    }
}
