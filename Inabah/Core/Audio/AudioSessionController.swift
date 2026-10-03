import AVFoundation
import os

/// События аудиосистемы, на которые должен реагировать плеер.
nonisolated enum AudioSessionEvent: Equatable, Sendable {
    /// Звук прервали (звонок, другое приложение) — пауза.
    case interruptionBegan
    /// Прерывание закончилось; `shouldResume` — система разрешает продолжить.
    case interruptionEnded(shouldResume: Bool)
    /// Отключили наушники / колонку — пауза, чтобы звук не пошёл из динамика.
    case outputLost
    /// Медиасервисы перезапущены — плееры недействительны, запись нужно загрузить заново.
    case mediaServicesReset
}

/// Аудиосессия приложения. Протокол — чтобы плеер тестировался без AVAudioSession.
protocol AudioSessionHandling: AnyObject {
    var onEvent: ((AudioSessionEvent) -> Void)? { get set }
    func activate() async throws
    func deactivate() async throws
}

/// `AVAudioSession` категории `.playback`: звук идёт и при беззвучном режиме,
/// а с `UIBackgroundModes = audio` — и в фоне. Категория задаётся при первой активации,
/// а не при создании объекта — превью и тесты не трогают аудиосистему процесса.
final class AudioSessionController: AudioSessionHandling {
    var onEvent: ((AudioSessionEvent) -> Void)?

    /// Безопасность: массив заполняется только в `init` на главном акторе и читается только
    /// в `deinit`, когда других ссылок на объект уже нет, — одновременного доступа не бывает.
    nonisolated(unsafe) private var observers: [any NSObjectProtocol] = []
    private var isConfigured = false
    private let logger = Logger(subsystem: "app.inabah.ios", category: "audio")

    init() {
        observe()
    }

    deinit {
        observers.forEach(NotificationCenter.default.removeObserver)
    }

    // Активация — синхронный вызов, в первый раз заметно долгий (система готовит аудиотракт),
    // поэтому вне главного актора, чтобы не тормозила анимация плеера.
    func activate() async throws {
        let needsCategory = !isConfigured
        do {
            try await Self.activateSession(configureCategory: needsCategory)
            isConfigured = true
        } catch {
            logger.error("Не удалось активировать аудиосессию: \(error.localizedDescription, privacy: .public)")
            throw AudioEngineError.sessionUnavailable
        }
    }

    func deactivate() async throws {
        try await Self.deactivateSession()
    }

    @concurrent
    private nonisolated static func activateSession(configureCategory: Bool) async throws {
        let session = AVAudioSession.sharedInstance()
        if configureCategory {
            try session.setCategory(.playback, mode: .spokenAudio)
        }
        try session.setActive(true)
    }

    @concurrent
    private nonisolated static func deactivateSession() async throws {
        try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    /// Уведомления приходят на главную очередь (`queue: .main`), поэтому обработчики
    /// выполняются на главном акторе.
    private func observe() {
        let center = NotificationCenter.default
        let session = AVAudioSession.sharedInstance()

        observers.append(center.addObserver(
            forName: AVAudioSession.interruptionNotification, object: session, queue: .main
        ) { [weak self] notification in
            let info = notification.userInfo
            let type = (info?[AVAudioSessionInterruptionTypeKey] as? UInt).flatMap(AVAudioSession.InterruptionType.init)
            let options = (info?[AVAudioSessionInterruptionOptionKey] as? UInt)
                .map(AVAudioSession.InterruptionOptions.init) ?? []
            let event: AudioSessionEvent? = switch type {
            case .began: .interruptionBegan
            case .ended: .interruptionEnded(shouldResume: options.contains(.shouldResume))
            default: nil
            }
            guard let event else { return }
            MainActor.assumeIsolated { self?.onEvent?(event) }
        })

        observers.append(center.addObserver(
            forName: AVAudioSession.routeChangeNotification, object: session, queue: .main
        ) { [weak self] notification in
            let reason = (notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt)
                .flatMap(AVAudioSession.RouteChangeReason.init)
            guard reason == .oldDeviceUnavailable else { return }
            MainActor.assumeIsolated { self?.onEvent?(.outputLost) }
        })

        observers.append(center.addObserver(
            forName: AVAudioSession.mediaServicesWereResetNotification, object: session, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated {
                // После сброса категорию нужно задать заново.
                self?.isConfigured = false
                self?.onEvent?(.mediaServicesReset)
            }
        })
    }
}
