package app.inabah.android.feature.azkar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithText
import app.inabah.android.core.audio.AudioPlayerController
import app.inabah.android.core.audio.TestAudioEngine
import app.inabah.android.core.audio.ui.AudioPlayerHost
import app.inabah.android.core.settings.PlaylistSettings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import app.inabah.android.feature.settings.AzkarSettingsScreen
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.DayTime
import app.inabah.android.core.settings.PreferencesStorage
import app.inabah.android.core.settings.ReadingSettings
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_MILLIS = 5_000L
private val FIXED_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
private val FIXED_NOW: Instant = Instant.parse("2026-10-03T06:00:00Z")

/** 13:00 в Берлине — после утреннего окна 5:00–12:00. */
private val OUTSIDE_WINDOW: Instant = Instant.parse("2026-10-03T11:00:00Z")

/** Экран раздела целиком: стор, настройки и DataStore во временном файле, без остального приложения. */
@RunWith(AndroidJUnit4::class)
class AzkarListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var directory: File
    private lateinit var storage: PreferencesStorage

    @Before
    fun openStorage() {
        // Этап 7: каждое действие теста заодно проверяет экран на доступность (ATF).
        compose.enableAccessibilityChecks()
        directory = File(context.cacheDir, "azkar-list-test-${System.nanoTime()}").apply { mkdirs() }
        val dataStore = PreferenceDataStoreFactory.create(scope = ioScope) { File(directory, "test.preferences_pb") }
        storage = PreferencesStorage(dataStore) { throw AssertionError("Ошибка хранилища", it) }
        runBlocking { storage.load() }
        ioScope.launch { storage.runWriter() }
    }

    @After
    fun closeStorage() {
        ioScope.cancel()
        directory.deleteRecursively()
    }

    private fun store(vararg repetitions: Int): AzkarStore {
        val azkar = repetitions.mapIndexed { index, count ->
            Zikr(ZikrId(AzkarSection.Morning, index + 1), "سُبْحَانَ اللَّهِ", count, "morning_01.mp3", translation = null)
        }
        return AzkarStore(
            repository = FixedRepository(azkar),
            storage = storage,
            windowSettings = windowSettings,
            history = AzkarHistory(storage),
            // По умолчанию — 08:00 в Берлине, внутри утреннего окна 5:00–12:00: не зависит от часов устройства.
            now = { now },
            zone = { FIXED_ZONE },
            onUnreadableProgress = { _, error -> throw AssertionError(error) },
        )
    }

    /** «Сейчас» для стора; тест может перенести его вне окна. */
    private var now: Instant = FIXED_NOW

    private val windowSettings by lazy { AzkarWindowSettings(storage) }
    private val defaultReading by lazy { ReadingSettings(storage) }
    private val playlistSettings by lazy { PlaylistSettings(storage) }
    private val defaultPlayer by lazy { AudioPlayerController(TestAudioEngine()) }

    /** Экран раздела и плеер под ним, как в приложении; отражение движка — пока экран на месте. */
    private fun showWithPlayer(store: AzkarStore, player: AudioPlayerController) {
        compose.setContent {
            LaunchedEffect(player) { player.run() }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { Screen(store, player = player) }
                InabahTheme { AudioPlayerHost(player) }
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun playOnCardOpensPlayerAndCloseReturnsPlayButton() {
        val player = AudioPlayerController(TestAudioEngine())
        showWithPlayer(store(1, 3), player)

        compose.onAllNodesWithContentDescription("Прослушать")[0].performClick()
        // Карточка — волна «Открыть плеер», плеер открыт на этом зикре.
        compose.onNodeWithContentDescription("Открыть плеер").assertExists()
        compose.onNodeWithText("Зикр №1").assertExists()

        compose.onNodeWithContentDescription("Закрыть плеер").performClick()
        compose.onNodeWithContentDescription("Открыть плеер").assertDoesNotExist()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText("Зикр №1").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun listenStartsPlaylistAndTurnsIntoOpenPlayer() {
        val player = AudioPlayerController(TestAudioEngine())
        showWithPlayer(store(1, 3), player)

        compose.onNodeWithText("Слушать").performScrollTo().performClick()
        compose.onNodeWithText("Зикр 1 из 2").assertExists()
        compose.onNodeWithText("Открыть плеер").assertExists()
        compose.runOnIdle { check(player.state.value.isPlaylistActive("azkar.morning")) }
    }

    @Test
    fun playAllControlsChangeSettings() {
        showWithPlayer(store(1, 3), AudioPlayerController(TestAudioEngine()))

        compose.onNodeWithText("3 с").performScrollTo().performClick()
        // Без MainActivity язык конфигурации — системный (эмулятор en-US): разделитель «.» вместо «,».
        compose.onNode(hasText("1,5×") or hasText("1.5×")).performScrollTo().performClick()
        compose.onNodeWithText("Повторять по числу раз").performScrollTo().performClick()

        compose.runOnIdle {
            check(playlistSettings.pauseBetween.value == 3.0)
            check(playlistSettings.rate.value == 1.5f)
            check(!playlistSettings.repeatsByCount.value)
        }
    }

    @Test
    fun scrubBarSeeksThroughAccessibility() {
        val player = AudioPlayerController(TestAudioEngine())
        showWithPlayer(store(1, 3), player)
        compose.onAllNodesWithContentDescription("Прослушать")[0].performClick()

        compose.onNodeWithContentDescription("Позиция воспроизведения")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        compose.runOnIdle { check(player.positionMs.value == 15_000L) { "позиция ${player.positionMs.value}" } }
    }

    private fun counters() = compose.onAllNodesWithContentDescription("Счётчик")

    private fun waitForNode(description: String) = compose.waitUntil(TIMEOUT_MILLIS) {
        compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun countingToMaxCollapsesCardAndUpdatesHeader() {
        val store = store(1, 3)
        compose.setContent { Screen(store) }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 2 }

        counters()[0].performClick()

        waitForNode("Развернуть")
        compose.onNodeWithContentDescription("Выполнено: 1 из 2").assertExists()
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 1 }
    }

    @Test
    fun completionOverlayShowsOnceAndNotAfterScreenIsRecreated() {
        val store = store(1, 1)
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) Screen(store) }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 2 }

        // Тест ждёт простоя Compose — выполненная карточка успевает свернуться, счётчик остаётся один.
        counters()[0].performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 1 }
        counters()[0].performClick()
        waitForNode("Машаа Аллах!")

        // Экран создаётся заново (как после пересоздания активности): стор помнит, что оверлей показан.
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("Развернуть").fetchSemanticsNodes().size == 2 }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Машаа Аллах!").assertDoesNotExist()
    }

    @Test
    fun outsideWindowShowsTimeNoticeAndNoCompletionOverlay() {
        now = OUTSIDE_WINDOW
        val store = store(1)
        compose.setContent { Screen(store) }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 1 }

        // Время — в формате устройства, поэтому только начало фразы.
        compose.onNodeWithText("Время утренних азкаров", substring = true).assertExists()
        compose.onNodeWithContentDescription("Выполнено: 0 из 1").assertDoesNotExist()

        // Счёт работает, но поздравления вне времени азкаров нет.
        counters()[0].performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().isEmpty() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Машаа Аллах!").assertDoesNotExist()
    }

    @Test
    fun newWindowAppliesToProgressOnlyWhenSettingsScreenLeaves() {
        val store = store(1, 3)
        compose.runOnIdle {
            runBlocking { store.loadAll() }
            store.sessions(AzkarSection.Morning)[1].increment()
        }
        var shown by mutableStateOf(true)
        compose.setContent {
            InabahTheme {
                if (shown) AzkarSettingsScreen(store, windowSettings, onBack = {}, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithContentDescription("Начало утренних азкаров").assertExists()
        compose.onNodeWithContentDescription("Конец вечерних азкаров").assertExists()

        // Конец утренних — на уже прошедшие 07:00: пока экран открыт, прочитанное на месте.
        compose.runOnIdle { windowSettings.setEnd(DayTime.of(7, 0), AzkarSection.Morning) }
        compose.runOnIdle { check(store.hasProgress(AzkarSection.Morning)) }

        // Ушли с экрана — окно закончилось, счётчики обнулены.
        shown = false
        compose.runOnIdle { check(!store.hasProgress(AzkarSection.Morning)) }
    }

    @Test
    fun increasingFontSizeToMaximumDisablesIncrease() {
        val store = store(1)
        val reading = ReadingSettings(storage)
        compose.setContent { Screen(store, reading) }

        // Жать, пока кнопка активна (не больше, чем шагов во всём диапазоне), — граница должна её выключить.
        val increase = compose.onNodeWithContentDescription("Увеличить арабский шрифт")
        val maxSteps = ((ReadingSettings.MAX_SIZE - ReadingSettings.MIN_SIZE) / ReadingSettings.STEP).toInt() + 1
        var steps = 0
        while (increase.fetchSemanticsNode().config.contains(SemanticsProperties.Disabled).not() && steps < maxSteps) {
            increase.performClick()
            steps++
        }

        increase.assertIsNotEnabled()
        check(reading.arabicFontSize.value == ReadingSettings.MAX_SIZE)
    }

    @Test
    fun resetOnCollapsedRowRestoresFullCard() {
        val store = store(1, 3)
        compose.setContent { Screen(store) }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 2 }
        counters()[0].performClick()
        waitForNode("Развернуть")

        compose.onAllNodes(hasContentDescription("Сбросить счёт") and isEnabled())[0].performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 2 }
        compose.onNodeWithContentDescription("Выполнено: 0 из 2").assertExists()
        compose.onNodeWithContentDescription("Развернуть").assertDoesNotExist()
    }

    @Test
    fun settingsResetNeedsConfirmationAndDismissKeepsProgress() {
        val store = store(1, 3)
        compose.runOnIdle {
            runBlocking { store.loadAll() }
            store.sessions(AzkarSection.Morning)[1].increment()
        }
        compose.setContent {
            InabahTheme {
                AzkarSettingsScreen(store, windowSettings, onBack = {}, contentPadding = PaddingValues())
            }
        }

        // «Утренние азкары» — и в строке времени, и в строке сброса: нужна строка-кнопка.
        val resetRow = compose.onNode(hasText("Утренние азкары") and hasClickAction())

        // Закрыли подтверждение «Назад» — прогресс на месте.
        resetRow.performClick()
        compose.onNodeWithText("Сбросить").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("Сбросить").assertDoesNotExist()
        compose.runOnIdle { check(store.hasProgress(AzkarSection.Morning)) }

        // Подтвердили — счёт обнулён, строка выключена.
        resetRow.performClick()
        compose.onNodeWithText("Сбросить").performClick()
        compose.runOnIdle { check(!store.hasProgress(AzkarSection.Morning)) }
        resetRow.assertIsNotEnabled()
    }

    @androidx.compose.runtime.Composable
    private fun Screen(
        store: AzkarStore,
        reading: ReadingSettings = defaultReading,
        player: AudioPlayerController = defaultPlayer,
    ) {
        InabahTheme {
            AzkarListScreen(
                section = AzkarSection.Morning,
                store = store,
                windowSettings = windowSettings,
                readingSettings = reading,
                player = player,
                playlistSettings = playlistSettings,
                onBack = {},
                onGoHome = {},
                contentPadding = PaddingValues(),
            )
        }
    }
}

private class FixedRepository(private val azkar: List<Zikr>) : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> = azkar

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> = emptyList()
}
