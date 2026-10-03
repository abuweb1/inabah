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
    let appIconSettings: AppIconSettings
    let audioPlayer: AudioPlayerController
    /// Палитра оформления — тема в окружении берётся из неё (`themed()`).
    let appearanceSettings: AppearanceSettings

    init(
        repository: any ContentRepository = BundleContentRepository(),
        defaults: UserDefaults = .standard,
        audioPlayer: AudioPlayerController? = nil,
        appIconSwitcher: (any AppIconSwitching)? = nil
    ) {
        router = AppRouter()
        readingSettings = ReadingSettings(defaults: defaults)
        playlistSettings = PlaylistSettings(defaults: defaults)
        azkarResetSettings = AzkarResetSettings(defaults: defaults)
        azkarStore = AzkarStore(repository: repository, defaults: defaults, resetSettings: azkarResetSettings)
        hadithStore = HadithStore(repository: repository)
        hadithProgress = HadithProgress(defaults: defaults)
        hadithCollectionOrder = HadithCollectionOrder(defaults: defaults)
        appIconSettings = AppIconSettings(switcher: appIconSwitcher ?? SystemAppIconSwitcher())
        self.audioPlayer = audioPlayer ?? AudioPlayerController(
            engine: AVAudioEngineAdapter(),
            session: AudioSessionController(),
            nowPlaying: NowPlayingCoordinator()
        )
        appearanceSettings = AppearanceSettings(defaults: defaults)
    }

    /// Превью: контент в памяти, отметки — в отдельном наборе `UserDefaults`; плеер без
    /// аудиосессии и экрана блокировки, иконка не меняется — превью не трогают систему.
    static var preview: AppEnvironment {
        AppEnvironment(
            repository: InMemoryContentRepository.preview,
            defaults: previewDefaults(),
            audioPlayer: AudioPlayerController(engine: AVAudioEngineAdapter()),
            appIconSwitcher: PreviewAppIconSwitcher()
        )
    }

    /// Набор превью очищается при каждом создании и засевается парой отметок хадисов:
    /// превью всегда показывают одно и то же, а не накопленное прошлыми запусками.
    private static func previewDefaults() -> UserDefaults {
        let suiteName = "preview"
        guard let defaults = UserDefaults(suiteName: suiteName) else { return .standard }
        defaults.removePersistentDomain(forName: suiteName)
        let read = (1...3).map { HadithID(collection: .nawawi, number: $0) }
        read.forEach { defaults.set("1", forKey: HadithProgress.readKey($0)) }
        defaults.set("1", forKey: HadithProgress.memorizedKey(read[0]))
        return defaults
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
            .environment(environment.appIconSettings)
            .environment(environment.audioPlayer)
            .environment(environment.appearanceSettings)
            .modifier(ThemedModifier(settings: environment.appearanceSettings))
    }
}

/// Тема в окружении — из выбранной палитры. Только этот модификатор следит за выбором:
/// смена палитры один раз перерисовывает дерево с новой темой.
private struct ThemedModifier: ViewModifier {
    let settings: AppearanceSettings

    func body(content: Content) -> some View {
        content.environment(\.theme, settings.style.theme)
    }
}
