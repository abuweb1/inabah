package app.inabah.android.feature.azkar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import app.inabah.android.feature.settings.AzkarSettingsScreen
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
import app.inabah.android.core.settings.AzkarResetSettings
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
            resetSettings = AzkarResetSettings(storage),
            // Утро в Берлине — далеко от обнулений (17:00 и 02:00), результат не зависит от часов устройства.
            now = { FIXED_NOW },
            zone = { FIXED_ZONE },
            onUnreadableProgress = { _, error -> throw AssertionError(error) },
        )
    }

    private val defaultReading by lazy { ReadingSettings(storage) }

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
                AzkarSettingsScreen(store, AzkarResetSettings(storage), onBack = {}, contentPadding = PaddingValues())
            }
        }

        // Закрыли подтверждение «Назад» — прогресс на месте.
        compose.onNodeWithText("Утренние азкары").performClick()
        compose.onNodeWithText("Сбросить").assertExists()
        Espresso.pressBack()
        compose.onNodeWithText("Сбросить").assertDoesNotExist()
        compose.runOnIdle { check(store.hasProgress(AzkarSection.Morning)) }

        // Подтвердили — счёт обнулён, строка выключена.
        compose.onNodeWithText("Утренние азкары").performClick()
        compose.onNodeWithText("Сбросить").performClick()
        compose.runOnIdle { check(!store.hasProgress(AzkarSection.Morning)) }
        compose.onNodeWithText("Утренние азкары").assertIsNotEnabled()
    }

    @androidx.compose.runtime.Composable
    private fun Screen(store: AzkarStore, reading: ReadingSettings = defaultReading) {
        InabahTheme {
            AzkarListScreen(
                section = AzkarSection.Morning,
                store = store,
                readingSettings = reading,
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
