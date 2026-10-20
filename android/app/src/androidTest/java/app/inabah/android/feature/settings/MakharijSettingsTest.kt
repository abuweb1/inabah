package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.content.AssetContentRepository
import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.core.settings.ReminderSettings
import app.inabah.android.core.settings.TestDataStoreRule
import app.inabah.android.feature.makharij.ReadingStepsScreen
import app.inabah.android.feature.makharij.ReadingStore
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_MILLIS = 5_000L

/** Настройки «Махраджа»: транскрипция, «Открыть все шаги», сброс шага и всего курса (настоящий reading.json). */
@RunWith(AndroidJUnit4::class)
class MakharijSettingsTest {
    // Хранилище — внешнее правило: закрывается после Compose.
    @get:Rule(order = 0)
    val dataStore = TestDataStoreRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val store = ReadingStore(
        AssetContentRepository(
            InstrumentationRegistry.getInstrumentation().targetContext.assets::open,
            ContentLanguagePriority(listOf(ContentLanguage.Base)),
            Dispatchers.IO,
        ),
    )

    @Before
    fun enableChecks() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun switchesChangeReadingSettings() {
        val settings = ReadingSettings(dataStore.storage)
        showSettings(ReadingProgress(dataStore.storage), settings)

        compose.onNodeWithText("Показывать транскрипцию").performClick()
        compose.onNodeWithText("Открыть все шаги").performClick()

        assertTrue(settings.transliterationHidden.value)
        assertTrue(settings.allStepsOpen.value)
    }

    @Test
    fun stepResetAsksAndClearsOnlyThatStep() {
        val progress = ReadingProgress(dataStore.storage)
        progress.markLessonDone(1)
        progress.markLessonDone(2)
        showSettings(progress, ReadingSettings(dataStore.storage))
        waitForText("Шаг 2 · Огласовки")

        compose.onNode(hasText("Шаг 2 · Огласовки") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Сбросить шаг?").assertExists()
        confirmButton().performClick()

        assertEquals(setOf(1), progress.lessonsDone.value)
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText("Шаг 2 · Огласовки").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun resetAllAsksAndClearsEverything() {
        val progress = ReadingProgress(dataStore.storage)
        progress.markLessonDone(1)
        progress.readWord(step = 3, index = 0, total = 2, repeat = 10)
        showSettings(progress, ReadingSettings(dataStore.storage))
        waitForText("Шаг 3 · Формы букв")

        compose.onNode(hasText("Сбросить весь прогресс") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Сбросить весь прогресс?").assertExists()
        confirmButton().performClick()

        assertTrue(progress.lessonsDone.value.isEmpty())
        assertTrue(progress.wordCounts.value.isEmpty())
        waitForText("Здесь появятся начатые и пройденные шаги - любой можно сбросить и пройти заново.")
    }

    @Test
    fun allStepsOpenUnlocksSteps() {
        val settings = ReadingSettings(dataStore.storage)
        var opened: Int? = null
        // Текущий шаг «дышит» без конца - Compose не бывает idle: кадры двигаем сами.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            InabahTheme {
                ReadingStepsScreen(store, ReadingProgress(dataStore.storage), settings, onBack = {},
                    onOpenStep = { opened = it }, contentPadding = PaddingValues())
            }
        }
        val step3 = hasContentDescription("Шаг 3 · ", substring = true) and hasClickAction()
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(step3).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(step3).performClick()
        compose.mainClock.advanceTimeByFrame()
        assertFalse("шаг 3 закрыт", opened == 3)

        settings.setAllStepsOpen(true)
        compose.mainClock.advanceTimeByFrame()
        compose.onNode(step3).performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(3, opened)
    }

    private fun showSettings(progress: ReadingProgress, settings: ReadingSettings) {
        compose.setContent {
            InabahTheme {
                MakharijSettingsScreen(store, progress, settings, ReminderSettings(dataStore.storage), onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("Прогресс")
    }

    /** «Сбросить» в подтверждении, не одноимённая подпись строки шага. */
    private fun confirmButton(): SemanticsNodeInteraction =
        compose.onNode(hasText("Сбросить") and hasClickAction() and !hasText("Шаг", substring = true))

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
