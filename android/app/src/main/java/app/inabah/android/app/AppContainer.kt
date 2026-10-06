package app.inabah.android.app

import android.content.Context
import android.util.Log
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.inabah.android.core.audio.AudioPlayerController
import app.inabah.android.core.audio.MediaControllerEngine
import app.inabah.android.core.content.AssetContentRepository
import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.settings.AppIconSettings
import app.inabah.android.core.settings.AppearanceSettings
import app.inabah.android.core.settings.AzkarResetSettings
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.PackageManagerAppIconSwitcher
import app.inabah.android.core.settings.PlaylistSettings
import app.inabah.android.core.settings.PreferencesStorage
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.core.settings.TextSizeSettings
import app.inabah.android.feature.azkar.AzkarStore
import app.inabah.android.feature.hadith.HadithStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Зависимости экранов — появляются после первого чтения настроек (аналог iOS `AppEnvironment`). */
class AppServices(
    val router: AppRouter,
    val readingSettings: ReadingSettings,
    val playlistSettings: PlaylistSettings,
    val azkarResetSettings: AzkarResetSettings,
    val azkarStore: AzkarStore,
    val hadithStore: HadithStore,
    val hadithProgress: HadithProgress,
    val hadithCollectionOrder: HadithCollectionOrder,
    val appearanceSettings: AppearanceSettings,
    val textSizeSettings: TextSizeSettings,
    val appIconSettings: AppIconSettings,
    val audioPlayer: AudioPlayerController,
)

/**
 * Создаёт и держит зависимости приложения — без синглтонов (docs/android/02-architecture.md).
 * Живёт столько же, сколько процесс; создаётся в [InabahApplication.onCreate].
 *
 * [start] читает DataStore один раз (заставка держится, пока [services] — `null`), затем
 * собирает [AppServices], запускает запись настроек и таймер обнуления азкаров
 * и загружает контент фоном.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Область приложения: работа на всё время жизни процесса, на главном потоке. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val storage = PreferencesStorage(
        dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { error ->
                Log.e(TAG, "Файл настроек повреждён — начинаем с пустого", error)
                emptyPreferences()
            },
        ) { appContext.preferencesDataStoreFile(PREFERENCES_NAME) },
        onError = { error -> Log.e(TAG, "Ошибка хранилища настроек", error) },
    )

    private val repository: ContentRepository = AssetContentRepository(
        source = appContext.assets::open,
        priority = ContentLanguagePriority.fromLanguageTags(systemLanguageTags()),
        ioDispatcher = Dispatchers.IO,
    )

    private val _services = MutableStateFlow<AppServices?>(null)
    val services: StateFlow<AppServices?> = _services.asStateFlow()

    private var started = false

    fun start() {
        check(!started) { "AppContainer уже запущен" }
        started = true
        appScope.launch {
            storage.load()
            // Запись и таймер — отдельные дети области приложения: сбой загрузки контента их не отменит.
            appScope.launch { storage.runWriter() }
            val services = createServices()
            _services.value = services
            appScope.launch { services.azkarStore.runResetTimer() }
            appScope.launch { services.audioPlayer.run() }
            appScope.launch { services.appIconSettings.refresh() }
            services.azkarStore.loadAll()
            services.hadithStore.loadAll()
            logContentFailures(services)
        }
    }

    /** Ошибка контента — дефект сборки (файлы ассетов): на экране «Повторить», в логе — причина. */
    private fun logContentFailures(services: AppServices) {
        val states = AzkarSection.entries.map { it.key to services.azkarStore.state(it).value } +
            HadithCollection.entries.map { it.key to services.hadithStore.state(it) }
        for ((name, state) in states) {
            if (state is Loadable.Failed) Log.e(TAG, "Не загружен контент «$name»", state.error)
        }
    }

    // Порядок — как в iOS AppEnvironment: настройки, затем сторы, которые от них зависят.
    private fun createServices(): AppServices {
        val azkarResetSettings = AzkarResetSettings(storage)
        return AppServices(
            router = AppRouter(),
            readingSettings = ReadingSettings(storage),
            playlistSettings = PlaylistSettings(storage),
            azkarResetSettings = azkarResetSettings,
            azkarStore = AzkarStore(
                repository = repository,
                storage = storage,
                resetSettings = azkarResetSettings,
                now = Instant::now,
                zone = ZoneId::systemDefault,
                onUnreadableProgress = { section, error ->
                    Log.w(TAG, "Прогресс «${section.key}» не читается — новый период", error)
                },
            ),
            hadithStore = HadithStore(repository),
            hadithProgress = HadithProgress(storage),
            hadithCollectionOrder = HadithCollectionOrder(storage),
            appearanceSettings = AppearanceSettings(storage),
            textSizeSettings = TextSizeSettings(storage),
            appIconSettings = AppIconSettings(
                switcher = PackageManagerAppIconSwitcher(appContext),
                ioDispatcher = Dispatchers.IO,
                onUnreadable = { error -> Log.e(TAG, "Не прочитана включённая иконка — отмечена «Классическая»", error) },
            ),
            // Служба воспроизведения подключается при первом звуке, не при запуске приложения.
            audioPlayer = AudioPlayerController(MediaControllerEngine(appContext)),
        )
    }

    private companion object {
        const val TAG = "Inabah"
        const val PREFERENCES_NAME = "inabah"

        /** Языки системы по порядку (`ru-RU`, `en-US`). */
        fun systemLanguageTags(): List<String> {
            val locales = LocaleListCompat.getAdjustedDefault()
            return (0 until locales.size()).mapNotNull { locales[it]?.toLanguageTag() }
        }
    }
}
