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
    /// Размер переводов и интерфейса — применяется к окружению в `appEnvironment`.
    let textSizeSettings: TextSizeSettings

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
        textSizeSettings = TextSizeSettings(defaults: defaults)
    }

    /// Превью: контент в памяти, отметки — в отдельном наборе `UserDefaults`; плеер без
    /// аудиосессии и экрана блокировки, иконка не меняется — превью не трогают систему.
    /// Блоки `#Preview` компилируются и в релизной сборке, поэтому не под `#if DEBUG`.
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

/// Смена иконки для превью: выбор только запоминается, иконка процесса не меняется.
private final class PreviewAppIconSwitcher: AppIconSwitching {
    private(set) var alternateIconName: String?
    var supportsAlternateIcons: Bool { true }

    func setAlternateIconName(_ alternateIconName: String?) async throws {
        self.alternateIconName = alternateIconName
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
            .environment(environment.textSizeSettings)
            .modifier(ThemedModifier(settings: environment.appearanceSettings))
            .modifier(TextSizeModifier(settings: environment.textSizeSettings))
    }
}

/// Размер текста: корень закреплён на стандартном размере — системный размер текста iOS не
/// влияет ни на что, навбары (их рисует UIKit по размеру своего контейнера) и таб-бар не растут.
/// Шаг интерфейса ставится на сами экраны вкладок (`interfaceTextSize()`), множитель
/// переводов — для `Font.content`.
private struct TextSizeModifier: ViewModifier {
    let settings: TextSizeSettings

    func body(content: Content) -> some View {
        content
            .fixedTextSize()
            .environment(\.contentTextScale, settings.content.scale)
    }
}

/// Шаг «Размер интерфейса» для содержимого экрана. Только этот модификатор следит за выбором.
private struct InterfaceTextSizeModifier: ViewModifier {
    @Environment(TextSizeSettings.self) private var settings

    func body(content: Content) -> some View {
        content.dynamicTypeSize(settings.interface.dynamicTypeSize)
    }
}

extension View {
    /// Содержимое экрана — по шагу «Размер интерфейса». Применяется к корню и к экранам стека
    /// каждой вкладки, а не к `NavigationStack`: иначе вместе с содержимым росли бы навбары.
    func interfaceTextSize() -> some View {
        modifier(InterfaceTextSizeModifier())
    }
}

/// Тема в окружении — из выбранной палитры. Только этот модификатор следит за выбором:
/// смена палитры один раз перерисовывает дерево с новой темой. Окнам — акцент палитры,
/// чтобы системные диалоги тоже были в её цвет.
private struct ThemedModifier: ViewModifier {
    let settings: AppearanceSettings

    func body(content: Content) -> some View {
        content
            .environment(\.theme, settings.style.theme)
            .onChange(of: settings.style, initial: true) { _, style in
                WindowTint.apply(style.theme.palette.accentLight)
            }
    }
}
