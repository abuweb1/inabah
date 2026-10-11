package app.inabah.android.feature.settings

import android.Manifest
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.CollectionInfo
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithFavorites
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.core.settings.ReminderSettings
import app.inabah.android.core.updates.NoUpdateSource
import app.inabah.android.core.settings.TestDataStoreRule
import app.inabah.android.feature.azkar.AzkarHomeScreen
import app.inabah.android.feature.azkar.AzkarStore
import app.inabah.android.feature.hadith.HadithStore
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** «Напоминания»: предложение на главной азкаров, переключатели в настройках азкаров и хадисов. */
@RunWith(AndroidJUnit4::class)
class RemindersTest {
    @get:Rule(order = 0)
    val dataStore = TestDataStoreRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val storage get() = dataStore.storage
    private val reminders by lazy { ReminderSettings(storage) }
    private val windows by lazy { AzkarWindowSettings(storage) }

    @Before
    fun setUp() {
        compose.enableAccessibilityChecks()
        // Системный диалог разрешения в тесте не нажать — разрешение выдаётся заранее.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun azkarStore() = AzkarStore(
        repository = NoContentRepository,
        storage = storage,
        windowSettings = windows,
        history = AzkarHistory(storage) { throw AssertionError(it) },
        now = { ZonedDateTime.of(2026, 10, 10, 8, 0, 0, 0, BERLIN).toInstant() },
        zone = { BERLIN },
        onUnreadableProgress = { _, error -> throw AssertionError(error) },
    )

    private fun switch(label: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and hasText(label)

    @Test
    fun offerEnableTurnsOnMorningAndEveningAndHidesCard() {
        compose.setContent {
            InabahTheme { AzkarHomeScreen(azkarStore(), windows, reminders, NoUpdateSource, onOpenSection = {}, contentPadding = PaddingValues()) }
        }
        compose.onNodeWithText("Включить").performScrollTo().performClick()

        compose.runOnIdle {
            check(reminders.preferences.value.azkarStart == AzkarSection.entries.toSet())
            check(reminders.offerAnswered.value)
        }
        compose.onAllNodesWithText("Напоминать об азкарах?").assertCountEquals(0)
    }

    @Test
    fun offerLaterHidesCardWithoutEnabling() {
        compose.setContent {
            InabahTheme { AzkarHomeScreen(azkarStore(), windows, reminders, NoUpdateSource, onOpenSection = {}, contentPadding = PaddingValues()) }
        }
        compose.onNodeWithText("Не сейчас").performScrollTo().performClick()

        compose.runOnIdle {
            check(reminders.preferences.value.azkarStart.isEmpty())
            check(reminders.offerAnswered.value)
        }
        compose.onAllNodesWithText("Напоминать об азкарах?").assertCountEquals(0)
    }

    @Test
    fun azkarSwitchesSaveAndUnfinishedNeedsStart() {
        compose.setContent {
            InabahTheme { AzkarSettingsScreen(azkarStore(), windows, reminders, onBack = {}, contentPadding = PaddingValues()) }
        }
        compose.onNode(switch("Если ещё не прочитаны")).performScrollTo().assertIsNotEnabled()

        compose.onNode(switch("Вечерние азкары")).performScrollTo().performClick()
        compose.onNode(switch("Вечерние азкары")).assertIsOn()
        compose.onNode(switch("Если ещё не прочитаны")).assertIsEnabled().performClick()

        compose.runOnIdle {
            check(reminders.preferences.value.azkarStart == setOf(AzkarSection.Evening))
            check(reminders.preferences.value.azkarUnfinished)
        }
    }

    @Test
    fun hadithOfDaySwitchShowsTimeAndSaves() {
        compose.setContent {
            InabahTheme {
                HadithSettingsScreen(HadithStore(NoContentRepository), HadithProgress(storage), HadithFavorites(storage),
                    HadithCollectionOrder(storage), reminders, ReadingSettings(storage), onOpenOrder = {}, onBack = {}, contentPadding = PaddingValues())
            }
        }
        compose.onAllNodesWithText("Время").assertCountEquals(0)

        compose.onNode(switch("Напоминать")).performScrollTo().performClick()

        compose.onNodeWithText("Время").assertExists()
        compose.runOnIdle { check(reminders.preferences.value.hadithOfDay) }
    }

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}

private object NoContentRepository : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> = emptyList()

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> = emptyList()

    override suspend fun collectionInfo(collection: HadithCollection): CollectionInfo = CollectionInfo.Empty
}
