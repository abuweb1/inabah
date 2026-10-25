import Foundation

/// Доступ к местоположению.
nonisolated enum LocationAccess: Hashable, Sendable {
    /// Ещё не проверен.
    case unknown
    /// Не спрашивали: можно спросить.
    case notGranted
    /// Отказ — только через настройки телефона (на iOS отказ виден сразу, «больше не спрашивать» не нужно угадывать).
    case denied
    /// Пользователь выключил «Точную геопозицию»: направление верное, пометка в строке.
    case approximate
    case precise

    var isGranted: Bool { self == .approximate || self == .precise }
}

/// Координаты; `time` — когда получены, по тем же монотонным часам, что `now` в `QiblaUIState.make`.
nonisolated struct LocationFix: Hashable, Sendable {
    var latitude: Double
    var longitude: Double
    var time: TimeInterval
}

/// Что известно о местоположении.
nonisolated enum LocationStatus: Hashable, Sendable {
    /// Геолокация выключена в телефоне.
    case off
    /// Ищем, координат ещё нет.
    case searching
    /// Не нашлось за `QiblaTuning.locationTimeout`.
    case failed
    case found(LocationFix)
}

/// Показание датчика направления.
nonisolated struct HeadingSample: Hashable, Sendable {
    /// Куда смотрит верх телефона, градусы от истинного севера.
    var trueHeading: Double
    /// Оценка точности от датчика; `nil` — датчик её не даёт.
    var accuracyDegrees: Double?
    /// Датчик сам сообщил о низкой точности (`QiblaHeadingQuality`).
    var lowAccuracy: Bool
    /// Наклон экрана от горизонтали.
    var tiltDegrees: Double
    var time: TimeInterval
    /// Сила магнитного поля у телефона; `nil` — неизвестна.
    var fieldMicroTesla: Double?
    /// Сила поля Земли в этой точке по мировой модели; `nil` — неизвестна (на iOS — всегда).
    var expectedFieldMicroTesla: Double?
}

/// Что известно о направлении телефона.
nonisolated enum HeadingStatus: Hashable, Sendable {
    /// Датчика направления нет.
    case noSensor
    /// Датчик есть, показаний ещё нет.
    case waiting
    case reading(HeadingSample)
}

/// Пометки плитки местоположения. `nearKaaba` — «приблизительное», а до Каабы ближе `QiblaTuning.approximateNearKm`.
nonisolated struct LocationNote: Hashable, Sendable {
    var approximate: Bool
    var stale: Bool
    var nearKaaba = false

    /// Координатам можно доверить «Направление найдено» (решение пользователя 2026-10-24, (б)+(в)).
    var confirmable: Bool { !stale && !nearKaaba }

    init(approximate: Bool, stale: Bool, nearKaaba: Bool = false) {
        self.approximate = approximate
        self.stale = stale
        self.nearKaaba = nearKaaba
    }

    /// Пометки координат `fix` при доступе `access` на момент `now`.
    init(access: LocationAccess, fix: LocationFix, now: TimeInterval, tuning: QiblaTuning = .default) {
        let approximate = access == .approximate
        self.init(
            approximate: approximate,
            stale: now - fix.time > tuning.locationStale,
            nearKaaba: approximate
                && QiblaMath.distanceToKaabaKm(latitude: fix.latitude, longitude: fix.longitude) <= tuning.approximateNearKm
        )
    }
}

/// Что подсказать, когда компас работает.
nonisolated enum QiblaGuidance: Hashable, Sendable {
    /// Первое показание ещё не пришло.
    case waiting
    case lowAccuracy
    case tilted(TurnSide, TurnAmount)
    case turn(TurnSide, TurnAmount)
    /// Метка у указателя, но координатам нельзя доверить подтверждение: устарели или приблизительные у Мекки.
    case locationUnconfirmed
    case aligned
}

/// Состояние экрана «Къибла» (`docs/android/06-screens.md`, «Къибла»; Android `QiblaUiState`).
nonisolated enum QiblaUIState: Hashable, Sendable {
    case loading
    /// Нужен доступ к местоположению; `denied` — спросить уже нельзя, только настройки телефона.
    case needsPermission(denied: Bool)
    case locationOff
    case locating
    case locationFailed
    /// Компаса нет: направление — числом от севера.
    case noCompass(bearing: Double, location: LocationNote)
    /// Кольцо работает; `pointer` — `nil`, пока нет показаний.
    case compass(bearing: Double, location: LocationNote, pointer: QiblaPointer?, guidance: QiblaGuidance)

    /// Состояние по доступу, местоположению и датчику. `pointer` — последний результат `QiblaTracker` для текущего
    /// показания; наклон и точность — уже с гистерезисом трекера. Подтверждение — только при надёжных данных.
    static func make(
        access: LocationAccess,
        location: LocationStatus,
        heading: HeadingStatus,
        pointer: QiblaPointer?,
        now: TimeInterval,
        tuning: QiblaTuning = .default
    ) -> QiblaUIState {
        switch access {
        case .unknown: return .loading
        case .notGranted: return .needsPermission(denied: false)
        case .denied: return .needsPermission(denied: true)
        case .approximate, .precise: break
        }
        let fix: LocationFix
        switch location {
        case .off: return .locationOff
        case .searching: return .locating
        case .failed: return .locationFailed
        case .found(let found): fix = found
        }
        let bearing = QiblaMath.bearing(latitude: fix.latitude, longitude: fix.longitude)
        let note = LocationNote(access: access, fix: fix, now: now, tuning: tuning)
        switch heading {
        case .noSensor: return .noCompass(bearing: bearing, location: note)
        case .waiting: return .compass(bearing: bearing, location: note, pointer: nil, guidance: .waiting)
        case .reading: break
        }
        guard let pointer else { return .compass(bearing: bearing, location: note, pointer: nil, guidance: .waiting) }
        let guidance: QiblaGuidance =
            if pointer.lowAccuracy { .lowAccuracy }
            else if pointer.tilted { .tilted(pointer.side, pointer.amount) }
            else if pointer.aligned { .aligned }
            else if pointer.heldByLocation { .locationUnconfirmed }
            else { .turn(pointer.side, pointer.amount) }
        return .compass(bearing: bearing, location: note, pointer: pointer, guidance: guidance)
    }
}
