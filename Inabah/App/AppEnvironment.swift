import SwiftUI
import UserNotifications

/// Корень композиции: создаёт зависимости приложения один раз и передаёт их в окружение.
///
/// Чтобы подменить источник контента (тесты, превью, сервер), достаточно передать
/// другой `ContentRepository` в инициализатор.
struct AppEnvironment {
    let router: AppRouter
    let readingSettings: ReadingSettings
    let playlistSettings: PlaylistSettings
    let azkarWindowSettings: AzkarWindowSettings
    let azkarHistory: AzkarHistory
    /// «Добавьте ещё азкаров»: когда предлагали последний раз.
    let azkarMotivation: AzkarMotivation
    let azkarStore: AzkarStore
    let hadithStore: HadithStore
    let hadithProgress: HadithProgress
    let hadithFavorites: HadithFavorites
    let hadithCollectionOrder: HadithCollectionOrder
    /// «Учимся читать» (раздел «Махрадж»): курс и прогресс (пройденные уроки, счёт слов, зикры итога).
    let readingStore: ReadingStore
    let readingProgress: ReadingProgress
    let appIconSettings: AppIconSettings
    let audioPlayer: AudioPlayerController
    /// Короткие записи «Учимся читать»; с плеером азкаров - по очереди.
    let clipPlayer: ClipPlayer
    /// Палитра оформления — тема в окружении берётся из неё (`themed()`).
    let appearanceSettings: AppearanceSettings
    /// Фон под арабским текстом — накладывается на тему палитры (`Theme.withParchment`).
    let parchmentSettings: ParchmentSettings
    /// «Фон перевода» — накладывается на тему после пергамента (`Theme.withTranslationShade`).
    let translationShadeSettings: TranslationShadeSettings
    /// Размер переводов и интерфейса — применяется к окружению в `appEnvironment`.
    let textSizeSettings: TextSizeSettings
    /// Напоминания: настройки и их планировщик (центр уведомлений iOS).
    let reminderSettings: ReminderSettings
    let reminderScheduler: ReminderScheduler
    /// Делегат центра уведомлений — центр держит его слабой ссылкой; ставится `installNotificationDelegate()`.
    let notificationDelegate: ReminderNotificationDelegate
    /// Окно «Что нового» после обновления.
    let whatsNew: WhatsNew
    /// Есть ли новая версия в App Store — в 1.1.1 без сети (`NoUpdateSource`).
    let updateSource: any UpdateSource
    /// «Къибла»: доступ к местоположению, координаты, компас.
    let qibla: QiblaServices

    init(
        repository: any ContentRepository = BundleContentRepository(),
        defaults: UserDefaults = .standard,
        audioPlayer: AudioPlayerController? = nil,
        clipPlayer: ClipPlayer? = nil,
        appIconSwitcher: (any AppIconSwitching)? = nil,
        notificationCenter: (any NotificationCentering)? = nil,
        updateSource: any UpdateSource = NoUpdateSource()
    ) {
        // Первым: «новая установка» узнаётся до того, как сторы запишут свои ключи.
        whatsNew = WhatsNew(defaults: defaults, repository: repository)
        self.updateSource = updateSource
        qibla = .system(defaults: defaults)
        router = AppRouter()
        readingSettings = ReadingSettings(defaults: defaults)
        playlistSettings = PlaylistSettings(defaults: defaults)
        azkarWindowSettings = AzkarWindowSettings(defaults: defaults)
        azkarHistory = AzkarHistory(defaults: defaults)
        azkarMotivation = AzkarMotivation(defaults: defaults)
        azkarStore = AzkarStore(
            repository: repository,
            defaults: defaults,
            windowSettings: azkarWindowSettings,
            history: azkarHistory
        )
        hadithStore = HadithStore(repository: repository)
        hadithProgress = HadithProgress(defaults: defaults)
        hadithFavorites = HadithFavorites(defaults: defaults)
        hadithCollectionOrder = HadithCollectionOrder(defaults: defaults)
        readingStore = ReadingStore(repository: repository)
        readingProgress = ReadingProgress(defaults: defaults, calendar: azkarStore.calendar)
        appIconSettings = AppIconSettings(switcher: appIconSwitcher ?? SystemAppIconSwitcher())
        self.audioPlayer = audioPlayer ?? AudioPlayerController(
            engine: AVAudioEngineAdapter(),
            session: AudioSessionController(),
            nowPlaying: NowPlayingCoordinator()
        )
        self.clipPlayer = clipPlayer ?? ClipPlayer()
        // Звучит что-то одно (как аудиофокус на Android): запись курса - азкары на паузу, и наоборот.
        self.clipPlayer.onWillPlay = { [weak player = self.audioPlayer] in player?.pause() }
        self.audioPlayer.onWillStart = { [weak clips = self.clipPlayer] in clips?.stop() }
        appearanceSettings = AppearanceSettings(defaults: defaults)
        parchmentSettings = ParchmentSettings(defaults: defaults)
        translationShadeSettings = TranslationShadeSettings(defaults: defaults)
        textSizeSettings = TextSizeSettings(defaults: defaults)
        reminderSettings = ReminderSettings(defaults: defaults)
        reminderScheduler = ReminderScheduler(
            center: notificationCenter ?? SystemNotificationCenter(),
            settings: reminderSettings,
            windowSettings: azkarWindowSettings,
            history: azkarHistory,
            hadithStore: hadithStore,
            progress: hadithProgress,
            order: hadithCollectionOrder,
            readingProgress: readingProgress,
            repository: repository,
            calendar: azkarStore.calendar
        )
        notificationDelegate = ReminderNotificationDelegate(router: router)
    }

    /// Нажатия на напоминания ведут через роутер приложения — вызывается один раз при запуске,
    /// не в превью и тестах (там центр уведомлений не трогается).
    func installNotificationDelegate() {
        UNUserNotificationCenter.current().delegate = notificationDelegate
    }

    /// Превью: контент в памяти, отметки — в отдельном наборе `UserDefaults`; плеер без
    /// аудиосессии и экрана блокировки, иконка не меняется — превью не трогают систему.
    /// Блоки `#Preview` компилируются и в релизной сборке, поэтому не под `#if DEBUG`.
    static var preview: AppEnvironment {
        AppEnvironment(
            repository: InMemoryContentRepository.preview,
            defaults: previewDefaults(),
            audioPlayer: AudioPlayerController(engine: AVAudioEngineAdapter()),
            clipPlayer: ClipPlayer(session: nil),
            appIconSwitcher: PreviewAppIconSwitcher(),
            notificationCenter: InertNotificationCenter()
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

/// Центр уведомлений для превью: ничего не спрашивает и не ставит.
private final class InertNotificationCenter: NotificationCentering {
    func authorization() async -> ReminderAuthorization { .notDetermined }
    func requestAuthorization() async -> Bool { false }
    func pendingIdentifiers() async -> [String] { [] }
    func removePending(_ identifiers: [String]) {}
    func add(_ request: UNNotificationRequest) async throws {}
    func removeDelivered(thread: String) async {}
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
            .environment(environment.azkarWindowSettings)
            .environment(environment.azkarHistory)
            .environment(environment.azkarMotivation)
            .environment(environment.azkarStore)
            .environment(environment.hadithStore)
            .environment(environment.hadithProgress)
            .environment(environment.readingStore)
            .environment(environment.readingProgress)
            .environment(environment.hadithFavorites)
            .environment(environment.hadithCollectionOrder)
            .environment(environment.appIconSettings)
            .environment(environment.audioPlayer)
            .environment(environment.clipPlayer)
            .environment(environment.appearanceSettings)
            .environment(environment.parchmentSettings)
            .environment(environment.translationShadeSettings)
            .environment(environment.textSizeSettings)
            .environment(environment.reminderSettings)
            .environment(environment.reminderScheduler)
            .environment(environment.whatsNew)
            .environment(\.updateSource, environment.updateSource)
            .environment(\.qibla, environment.qibla)
            .modifier(ThemedModifier(
                settings: environment.appearanceSettings,
                parchment: environment.parchmentSettings,
                translationShade: environment.translationShadeSettings
            ))
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

/// Тема в окружении — из выбранной палитры, фона арабского текста и фона перевода. Только этот
/// модификатор следит за выбором: смена одного из них один раз перерисовывает дерево с новой темой.
/// Окнам — акцент палитры, чтобы системные диалоги тоже были в её цвет.
private struct ThemedModifier: ViewModifier {
    let settings: AppearanceSettings
    let parchment: ParchmentSettings
    let translationShade: TranslationShadeSettings

    func body(content: Content) -> some View {
        content
            .environment(
                \.theme,
                settings.style.theme.withParchment(parchment.style).withTranslationShade(translationShade.value)
            )
            .onChange(of: settings.style, initial: true) { _, style in
                WindowTint.apply(style.theme.palette.accentLight)
            }
    }
}
