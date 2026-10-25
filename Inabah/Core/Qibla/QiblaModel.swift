import Foundation
import Observation

/// Местоположение для «Къиблы»: только пока экран на виду, без фонового отслеживания.
protocol QiblaLocationSource {
    /// Один поиск (`QiblaLocating.statuses`); остановка чтения потока отменяет поиск.
    func locate() -> AsyncStream<LocationStatus>
}

/// Компас: показания идут, пока поток читают; остановка чтения снимает подписку. Не удалось подписаться —
/// поток завершается ошибкой (экран покажет «Компаса нет», а не вечное «Настраиваем компас»).
protocol QiblaHeadingSource {
    var isAvailable: Bool { get }
    func headings() -> AsyncThrowingStream<QiblaRawHeading, any Error>
}

/// Состояние экрана «Къибла» из местоположения и компаса (Android `QiblaModel`). Источники работают, пока идёт
/// `run(access:)` — экран запускает его задачей на время показа; новый запуск — координаты ищутся заново, трекер
/// новый (свой номер, счёт откликов с нуля). Координаты не сохраняются и не пишутся в журнал.
@Observable
final class QiblaModel {
    private(set) var state: QiblaUIState = .loading

    @ObservationIgnored private let location: any QiblaLocationSource
    @ObservationIgnored private let heading: any QiblaHeadingSource
    @ObservationIgnored private let now: () -> TimeInterval
    @ObservationIgnored private let tuning: QiblaTuning

    @ObservationIgnored private var generation = 0
    @ObservationIgnored private var tracker = QiblaTracker()
    @ObservationIgnored private var access = LocationAccess.unknown
    @ObservationIgnored private var locationStatus = LocationStatus.searching
    @ObservationIgnored private var headingStatus = HeadingStatus.waiting
    @ObservationIgnored private var pointer: QiblaPointer?
    @ObservationIgnored private var refreshes: AsyncStream<Void>.Continuation?

    /// - Parameter now: монотонные часы — те же, что время в `LocationFix` и `QiblaRawHeading`.
    init(
        location: any QiblaLocationSource,
        heading: any QiblaHeadingSource,
        now: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
        tuning: QiblaTuning = .default
    ) {
        self.location = location
        self.heading = heading
        self.now = now
        self.tuning = tuning
    }

    /// Работа экрана при доступе `access` — до отмены задачи. Без доступа источники не запускаются.
    func run(access: LocationAccess) async {
        self.access = access
        locationStatus = .searching
        headingStatus = .waiting
        pointer = nil
        guard access.isGranted else {
            publish()
            return
        }
        generation += 1
        tracker = QiblaTracker(tuning: tuning, generation: generation)
        headingStatus = heading.isAvailable ? .waiting : .noSensor
        publish()

        let (triggers, continuation) = AsyncStream.makeStream(of: Void.self, bufferingPolicy: .bufferingNewest(1))
        refreshes = continuation
        continuation.yield()
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.followLocation(triggers) }
            if heading.isAvailable {
                group.addTask { await self.followHeading() }
            }
        }
        refreshes = nil
    }

    /// «Обновить» / «Повторить»: новый поиск координат.
    func refreshLocation() {
        refreshes?.yield()
    }

    /// Каждый запрос — новый поиск; прежний отменяется.
    private func followLocation(_ triggers: AsyncStream<Void>) async {
        var search: Task<Void, Never>?
        for await _ in triggers {
            search?.cancel()
            search = Task {
                for await status in location.locate() {
                    locationStatus = status
                    publish()
                }
            }
        }
        search?.cancel()
    }

    private func followHeading() async {
        do {
            for try await raw in heading.headings() {
                headingChanged(raw)
            }
        } catch {
            // Не удалось подписаться на компас — как без компаса.
            headingStatus = .noSensor
            pointer = nil
            publish()
        }
    }

    private func headingChanged(_ raw: QiblaRawHeading) {
        let sample = raw.sample
        headingStatus = .reading(sample)
        if case .found(let fix) = locationStatus {
            let target = QiblaMath.signedDelta(
                from: sample.trueHeading,
                to: QiblaMath.bearing(latitude: fix.latitude, longitude: fix.longitude)
            )
            let note = LocationNote(access: access, fix: fix, now: now(), tuning: tuning)
            pointer = tracker.update(target: target, sample: sample, locationConfirmable: note.confirmable)
        }
        publish()
    }

    private func publish() {
        let next = QiblaUIState.make(
            access: access,
            location: locationStatus,
            heading: headingStatus,
            pointer: pointer,
            now: now(),
            tuning: tuning
        )
        if state != next { state = next }
    }
}
