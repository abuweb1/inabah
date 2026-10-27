import Foundation
import Testing
@testable import Inabah

/// Состояние экрана: доступ, местоположение, компас; подтверждение только при надёжных данных (Android `QiblaUiStateTest`).
@Suite("Къибла: состояние экрана")
struct QiblaUIStateTests {
    private static let now: TimeInterval = 10_000
    private static let grozny = LocationFix(latitude: 43.3178, longitude: 45.6986, time: now)
    private static let found = LocationStatus.found(grozny)
    private static let sample = HeadingSample(trueHeading: 194, accuracyDegrees: 5, lowAccuracy: false, tiltDegrees: 3, time: now)

    private static func pointer(
        aligned: Bool,
        side: TurnSide = .right,
        tilted: Bool = false,
        lowAccuracy: Bool = false
    ) -> QiblaPointer {
        QiblaPointer(
            offset: aligned ? 1 : 40,
            angle: 0,
            side: side,
            aligned: aligned,
            alignedEntries: 0,
            tilted: tilted,
            lowAccuracy: lowAccuracy
        )
    }

    private static func state(
        access: LocationAccess = .precise,
        location: LocationStatus = found,
        heading: HeadingStatus = .reading(sample),
        pointer: QiblaPointer? = pointer(aligned: false)
    ) -> QiblaUIState {
        .make(access: access, location: location, heading: heading, pointer: pointer, now: now)
    }

    private static func guidance(_ state: QiblaUIState) -> QiblaGuidance? {
        if case .compass(_, _, _, let guidance) = state { guidance } else { nil }
    }

    private static func note(_ state: QiblaUIState) -> LocationNote? {
        if case .compass(_, let note, _, _) = state { note } else { nil }
    }

    @Test("Доступ — загрузка, просьба, отказ")
    func access() {
        #expect(Self.state(access: .unknown) == .loading)
        #expect(Self.state(access: .notGranted) == .needsPermission(denied: false))
        #expect(Self.state(access: .denied) == .needsPermission(denied: true))
        // Выключенные «Службы геолокации» — не отказ: включаются не в настройках приложения.
        #expect(Self.state(access: .servicesOff) == .locationOff)
    }

    @Test("Местоположение — выключено, ищем, не нашлось")
    func location() {
        #expect(Self.state(location: .off) == .locationOff)
        #expect(Self.state(location: .searching) == .locating)
        #expect(Self.state(location: .failed) == .locationFailed)
    }

    @Test("Нет компаса — направление числом")
    func noCompass() throws {
        guard case .noCompass(let bearing, _) = Self.state(heading: .noSensor) else {
            Issue.record("ожидалось noCompass")
            return
        }
        #expect(abs(bearing - 194.45) < 0.05)
    }

    @Test("Компас ещё молчит — ожидание без метки")
    func waiting() {
        #expect(Self.guidance(Self.state(heading: .waiting, pointer: nil)) == .waiting)
    }

    @Test("Низкая точность и наклон — подсказка вместо подтверждения")
    func lowAccuracyAndTilt() {
        #expect(Self.guidance(Self.state(pointer: Self.pointer(aligned: false, lowAccuracy: true))) == .lowAccuracy)
        #expect(Self.guidance(Self.state(pointer: Self.pointer(aligned: false, side: .left, tilted: true))) == .tilted(.left, .far))
        #expect(
            Self.guidance(Self.state(pointer: Self.pointer(aligned: false, tilted: true, lowAccuracy: true))) == .lowAccuracy,
            "точность важнее"
        )
    }

    @Test("Надёжно — поворот или найдено")
    func turnOrAligned() {
        #expect(Self.guidance(Self.state(pointer: Self.pointer(aligned: false, side: .left))) == .turn(.left, .far))
        #expect(Self.guidance(Self.state(pointer: Self.pointer(aligned: true))) == .aligned)
    }

    @Test("Приблизительные и устаревшие координаты — пометки")
    func notes() {
        let approximate = Self.note(Self.state(access: .approximate))
        #expect(approximate == LocationNote(approximate: true, stale: false, nearKaaba: false))
        #expect(approximate?.confirmable == true, "в Грозном приблизительное направление верное")
        var old = Self.grozny
        old.time = Self.now - QiblaTuning.default.locationStale - 1
        let stale = Self.note(Self.state(location: .found(old)))
        #expect(stale?.stale == true)
        #expect(stale?.confirmable == false, "устаревшие — без подтверждения (решение пользователя 2026-10-24)")
    }

    @Test("Приблизительное у самой Мекки — нужно точное")
    func approximateNearMecca() {
        let mecca = LocationStatus.found(LocationFix(latitude: 21.45, longitude: 39.85, time: Self.now))
        let near = Self.note(Self.state(access: .approximate, location: mecca))
        #expect(near?.nearKaaba == true)
        #expect(near?.confirmable == false)
        #expect(Self.note(Self.state(access: .precise, location: mecca))?.confirmable == true, "точное у Мекки — можно")
    }

    @Test("Без метки — то же состояние, угол не влияет")
    func withoutPointer() {
        let near = Self.state(pointer: Self.pointer(aligned: false))
        var turned = Self.pointer(aligned: false)
        turned.offset = 41
        let far = Self.state(pointer: turned)
        #expect(near != far, "с меткой состояния разные")
        #expect(near.withoutPointer == far.withoutPointer, "без метки - одно")
        #expect(near.withoutPointer.pointer == nil)
        #expect(near.withoutPointer.guidance == near.guidance)
        #expect(Self.state(location: .off).withoutPointer == .locationOff)
    }

    @Test("На месте, но координатам нельзя доверить — отдельная подсказка")
    func locationUnconfirmed() {
        let held = QiblaPointer(offset: 1, angle: 1, side: .right, aligned: false, alignedEntries: 0, heldByLocation: true)
        #expect(Self.guidance(Self.state(pointer: held)) == .locationUnconfirmed)
    }
}
