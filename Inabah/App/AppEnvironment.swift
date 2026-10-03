import SwiftUI

/// Корень композиции: создаёт зависимости приложения один раз и передаёт их в окружение.
///
/// Чтобы подменить источник контента (тесты, превью, сервер), достаточно передать
/// другой `ContentRepository` в инициализатор.
struct AppEnvironment {
    let router: AppRouter
    let readingSettings: ReadingSettings
    let playlistSettings: PlaylistSettings
    let azkarResetSettings: AzkarResetSettings
    let azkarStore: AzkarStore
    let hadithStore: HadithStore
    let hadithProgress: HadithProgress
    let hadithCollectionOrder: HadithCollectionOrder
    let audioPlayer: AudioPlayerController
    let theme: Theme

    init(
        repository: any ContentRepository = BundleContentRepository(),
        defaults: UserDefaults = .standard,
        theme: Theme = .inabah,
        audioPlayer: AudioPlayerController? = nil
    ) {
        router = AppRouter()
        readingSettings = ReadingSettings(defaults: defaults)
        playlistSettings = PlaylistSettings(defaults: defaults)
        azkarResetSettings = AzkarResetSettings(defaults: defaults)
        azkarStore = AzkarStore(repository: repository, defaults: defaults, resetSettings: azkarResetSettings)
        hadithStore = HadithStore(repository: repository)
        hadithProgress = HadithProgress(defaults: defaults)
        hadithCollectionOrder = HadithCollectionOrder(defaults: defaults)
        self.audioPlayer = audioPlayer ?? AudioPlayerController(
            engine: AVAudioEngineAdapter(),
            session: AudioSessionController(),
            nowPlaying: NowPlayingCoordinator()
        )
        self.theme = theme
    }

    /// Превью: контент в памяти, отметки — в отдельном наборе `UserDefaults`; плеер без
    /// аудиосессии и экрана блокировки — превью не трогают аудиосистему процесса.
    static var preview: AppEnvironment {
        AppEnvironment(
            repository: InMemoryContentRepository.preview,
            defaults: UserDefaults(suiteName: "preview") ?? .standard,
            audioPlayer: AudioPlayerController(engine: AVAudioEngineAdapter())
        )
    }
}

extension View {
    func appEnvironment(_ environment: AppEnvironment) -> some View {
        self
            .environment(environment.router)
            .environment(environment.readingSettings)
            .environment(environment.playlistSettings)
            .environment(environment.azkarResetSettings)
            .environment(environment.azkarStore)
            .environment(environment.hadithStore)
            .environment(environment.hadithProgress)
            .environment(environment.hadithCollectionOrder)
            .environment(environment.audioPlayer)
            .environment(\.theme, environment.theme)
    }
}
