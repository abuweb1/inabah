package app.inabah.android.feature.azkar

import android.text.format.DateFormat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import app.inabah.android.feature.settings.AzkarSettingsScreen
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import app.inabah.android.core.settings.TestDataStoreRule
import app.inabah.android.core.settings.ReadingSettings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
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
    // Хранилище — внешнее правило: закрывается после Compose (экран при уходе ещё пишет в него).
    @get:Rule(order = 0)
    val dataStore = TestDataStoreRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val storage: PreferencesStorage get() = dataStore.storage

    @Before
    fun enableChecks() {
        // Этап 7: каждое действие теста заодно проверяет экран на доступность (ATF).
        compose.enableAccessibilityChecks()
    }

    private fun store(vararg repetitions: Int): AzkarStore {
        val azkar = repetitions.mapIndexed { index, count ->
            Zikr(ZikrId(AzkarSection.Morning, index + 1), "سُبْحَانَ اللَّهِ", count, audioFile, translation = null)
        }
        return AzkarStore(
            repository = FixedRepository(azkar),
            storage = storage,
            windowSettings = windowSettings,
            history = AzkarHistory(storage) { throw AssertionError(it) },
            // По умолчанию — 08:00 в Берлине, внутри утреннего окна 5:00–12:00: не зависит от часов устройства.
            now = { now },
            zone = { FIXED_ZONE },
            onUnreadableProgress = { _, error -> throw AssertionError(error) },
        )
    }

    /** «Сейчас» для стора; тест может перенести его вне окна. */
    private var now: Instant = FIXED_NOW

    /**
     * Файл записи у зикров стора: тесты плеера (движок-подделка) — «есть запись»; `null` — как сейчас
     * в приложении, записей нет (2026-10-06).
     */
    private var audioFile: String? = "test-tone-1.wav"

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
        // «Сделать напоминание» на оверлее — кнопка с текстом (в шапке — значок с той же подписью для TalkBack).
        compose.onNodeWithText("Сделать напоминание").assertIsEnabled()

        // Экран создаётся заново (как после пересоздания активности): стор помнит, что оверлей показан.
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("Развернуть").fetchSemanticsNodes().size == 2 }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Машаа Аллах!").assertDoesNotExist()
    }

    // Аудит 2026-10-06: правило «ушли раньше 0,6 с — поздравим при следующем открытии» не проверялось.
    @Test
    fun leavingBeforeCompletionDelayShowsOverlayNextTime() {
        val store = store(1)
        var shown by mutableStateOf(true)
        compose.setContent { if (shown) Screen(store) }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 1 }

        // Выполнили и ушли через 0,3 с — оверлей ещё не появился, раздел не «поздравлен».
        compose.mainClock.autoAdvance = false
        counters()[0].performClick()
        compose.mainClock.advanceTimeBy(300)
        shown = false
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { check(store.shouldPresentCompletion(AzkarSection.Morning)) { "раздел выполнен и ещё не поздравлен" } }

        shown = true
        waitForNode("Машаа Аллах!")
    }

    // Аудит 2026-10-06: главная вне времени азкаров проверялась только вручную.
    @Test
    fun homeOutsideWindowShowsWindowTimeInsteadOfRing() {
        now = OUTSIDE_WINDOW
        val store = store(1, 3)
        compose.runOnIdle { runBlocking { store.loadAll() } }
        compose.setContent {
            InabahTheme { AzkarHomeScreen(store, windowSettings, onOpenSection = {}, contentPadding = PaddingValues()) }
        }

        // 13:00 — вне окна оба раздела: у утренних время окна (в любом формате системы есть «12:00»),
        // колец с процентом нет ни у одного.
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("12:00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Выполнено на", substring = true).assertCountEquals(0)
    }

    // 2026-10-06: записей в приложении нет — ▶ неактивна, вместо «Прослушать все» заглушка.
    @Test
    fun withoutRecordingsPlayIsDisabledAndPlayAllIsPlaceholder() {
        audioFile = null
        val store = store(1, 3)
        compose.setContent { Screen(store) }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 2 }

        compose.onAllNodesWithContentDescription("Аудио скоро").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Аудио скоро")[0].assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("Прослушать").assertCountEquals(0)
        compose.onNodeWithText("Прослушать все азкары", substring = true).performScrollTo()
        compose.onAllNodesWithText("Слушать").assertCountEquals(0)
    }

    @Test
    fun everyCardHasShareButton() {
        val store = store(1, 3)
        compose.setContent { Screen(store) }
        compose.waitUntil(TIMEOUT_MILLIS) { counters().fetchSemanticsNodes().size == 2 }

        // Четвёртая кнопка ряда; прокрутка — действие, заодно проверка доступности.
        compose.onAllNodesWithContentDescription("Поделиться").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Поделиться")[1].performScrollTo()
        // В шапке — «Сделать напоминание» (значок без текста, подпись для TalkBack).
        compose.onNodeWithContentDescription("Сделать напоминание").assertIsEnabled()
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

    /** Экран настроек азкаров; [lifecycle] — свой владелец жизненного цикла, чтобы проверить уход в фон. */
    private fun showSettings(store: AzkarStore, shown: () -> Boolean, lifecycle: LifecycleOwner? = null) {
        compose.setContent {
            InabahTheme {
                val owner = lifecycle ?: LocalLifecycleOwner.current
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    if (shown()) AzkarSettingsScreen(store, windowSettings, onBack = {}, contentPadding = PaddingValues())
                }
            }
        }
    }

    /**
     * Барабан капсулы [chip] — на [hour]:00 через TalkBack-действия. Индекс часа зависит от формата системы:
     * в 12-часовом — час половины дня, а половину («До или после полудня») переключает третий барабан.
     */
    private fun pickHour(chip: String, hour: Int) {
        compose.onNodeWithContentDescription(chip).performClick()
        val is24Hour = DateFormat.is24HourFormat(InstrumentationRegistry.getInstrumentation().targetContext)
        val hours = compose.onNodeWithContentDescription("Часы")
        hours.performSemanticsAction(SemanticsActions.SetProgress) { it((if (is24Hour) hour else hour % 12).toFloat()) }
        compose.waitForIdle()
        if (!is24Hour) {
            val period = compose.onNodeWithContentDescription("До или после полудня")
            // AM — первое значение барабана, PM — второе; шаг за край (уже нужная половина) ничего не делает.
            val toward = if (hour < 12) "Меньше" else "Больше"
            val action = period.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == toward }
            compose.runOnUiThread { action.action() }
        }
        compose.waitForIdle()
    }

    @Test
    fun windowDraftIsSavedAndAppliedOnlyWhenSettingsScreenLeaves() {
        val store = store(1, 3)
        compose.runOnIdle {
            runBlocking { store.loadAll() }
            store.sessions(AzkarSection.Morning)[1].increment()
        }
        var shown by mutableStateOf(true)
        showSettings(store, shown = { shown })
        compose.onNodeWithContentDescription("Конец вечерних азкаров").assertExists()

        // Начало утренних — на 09:00, позже нынешних 08:00: пока экран открыт, это только черновик.
        pickHour("Начало утренних азкаров", 9)
        compose.runOnIdle {
            check(windowSettings.window(AzkarSection.Morning).start == DayTime.of(5, 0)) { "черновик сохранён раньше времени" }
            check(store.hasProgress(AzkarSection.Morning))
        }

        // Ушли с экрана — время сохранено, окно ещё не началось, счётчики обнулены.
        shown = false
        compose.runOnIdle {
            check(windowSettings.window(AzkarSection.Morning).start == DayTime.of(9, 0))
            check(!store.hasProgress(AzkarSection.Morning))
        }
    }

    @Test
    fun windowDraftIsSavedWhenAppGoesToBackground() {
        val store = store(1)
        compose.runOnIdle { runBlocking { store.loadAll() } }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            override val lifecycle: Lifecycle get() = registry
        }
        showSettings(store, shown = { true }, lifecycle = owner)

        pickHour("Начало утренних азкаров", 4)
        compose.runOnIdle { check(windowSettings.window(AzkarSection.Morning).start == DayTime.of(5, 0)) }

        // Шторка уведомлений / другое приложение — ON_STOP: черновик сохраняется, экран ещё открыт.
        compose.runOnIdle { owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.runOnIdle { check(windowSettings.window(AzkarSection.Morning).start == DayTime.of(4, 0)) }
    }

    @Test
    fun sameStartAndEndShowsNoticeAndKeepsPreviousWindow() {
        val store = store(1)
        compose.runOnIdle { runBlocking { store.loadAll() } }
        var shown by mutableStateOf(true)
        showSettings(store, shown = { shown })
        val notice = "Начало и конец совпадают — для этого раздела останется прежнее время."
        compose.onNodeWithText(notice).assertDoesNotExist()

        // Конец утренних 12:00 → 05:00, как начало.
        pickHour("Конец утренних азкаров", 5)
        compose.onNodeWithText(notice).assertExists()

        shown = false
        compose.runOnIdle { check(windowSettings.window(AzkarSection.Morning) == AzkarWindowSettings.defaultWindow(AzkarSection.Morning)) }
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
