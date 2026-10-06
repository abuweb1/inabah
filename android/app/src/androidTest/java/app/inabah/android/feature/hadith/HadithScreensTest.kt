package app.inabah.android.feature.hadith

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.content.model.HadithTranslation
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.HadithStatus
import app.inabah.android.core.settings.PreferencesStorage
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.feature.settings.HadithOrderSettingsScreen
import app.inabah.android.feature.settings.HadithSettingsScreen
import java.io.File
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

private const val NAWAWI_COUNT = 3
private const val TIMEOUT_MILLIS = 5_000L

/** Экраны хадисов: отметки, листание, порядок сборников, сброс (критерии этапа 4, docs/android/09). */
@RunWith(AndroidJUnit4::class)
class HadithScreensTest {
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
        directory = File(context.cacheDir, "hadith-test-${System.nanoTime()}").apply { mkdirs() }
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

    private enum class Screen { Detail, List, Home }

    @Test
    fun memorizedOnDetailShowsHeartInListAndStatOnHome() {
        val store = HadithStore(FixedRepository())
        val progress = HadithProgress(storage)
        val order = HadithCollectionOrder(storage)
        var screen by mutableStateOf(Screen.Detail)
        compose.setContent {
            InabahTheme {
                when (screen) {
                    Screen.Detail -> HadithDetailScreen(HadithId(HadithCollection.Nawawi, 1), store, progress,
                        ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
                    Screen.List -> HadithListScreen(HadithCollection.Nawawi, store, progress, onBack = {},
                        onOpenHadith = {}, contentPadding = PaddingValues())
                    Screen.Home -> HadithHomeScreen(store, progress, order, onOpenCollection = {}, contentPadding = PaddingValues())
                }
            }
        }
        waitForNode("Выучен")
        // Пейджер заранее строит и соседнюю страницу — первая в дереве открытая.
        compose.onAllNodesWithContentDescription("Выучен")[0].performClick()
        // «Выучен» ⇒ «прочитан»; соседняя страница не отмечена.
        compose.onAllNodesWithContentDescription("Выучен")[0].assertIsOn()
        compose.onAllNodesWithContentDescription("Прочитан")[0].assertIsOn()
        compose.onAllNodesWithContentDescription("Выучен")[1].assertIsOff()

        screen = Screen.List
        val memorized = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Выучен")
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(memorized).fetchSemanticsNodes().size == 1 }

        screen = Screen.Home
        waitForNode("Выучено 1 из $NAWAWI_COUNT")
        compose.onNodeWithContentDescription("Прочитано 1 из $NAWAWI_COUNT").assertExists()
    }

    @Test
    fun chevronsPageThroughCollectionAndDisableAtEdges() {
        val store = HadithStore(FixedRepository())
        compose.setContent {
            InabahTheme {
                HadithDetailScreen(HadithId(HadithCollection.Nawawi, 1), store, HadithProgress(storage),
                    ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForNode("1 из $NAWAWI_COUNT")
        compose.onNodeWithContentDescription("Предыдущий хадис").assertIsNotEnabled()

        repeat(NAWAWI_COUNT - 1) { compose.onNodeWithContentDescription("Следующий хадис").performClick() }
        waitForNode("$NAWAWI_COUNT из $NAWAWI_COUNT")
        compose.onNodeWithContentDescription("Следующий хадис").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Предыдущий хадис").assertIsEnabled()
    }

    @Test
    fun detailHasShareButtonAtBottomOfTranslation() {
        val store = HadithStore(FixedRepository())
        compose.setContent {
            InabahTheme {
                HadithDetailScreen(HadithId(HadithCollection.Nawawi, 1), store, HadithProgress(storage),
                    ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForNode("1 из $NAWAWI_COUNT")

        // Прокрутка — действие: заодно проверка доступности (зона касания, подпись).
        compose.onAllNodesWithContentDescription("Поделиться")[0].performScrollTo().assertIsEnabled()
    }

    // Ревью: без источника и без перевода кнопка тоже на месте (линия над источником — только при нём).
    @Test
    fun shareButtonWithoutSourceAndWithoutTranslation() {
        val store = HadithStore(PartialRepository())
        compose.setContent {
            InabahTheme {
                HadithDetailScreen(HadithId(HadithCollection.Nawawi, 1), store, HadithProgress(storage),
                    ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForNode("1 из 2")
        compose.onAllNodesWithText("Приводится:", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Поделиться")[0].performScrollTo().assertIsEnabled()

        compose.onNodeWithContentDescription("Следующий хадис").performClick()
        waitForNode("2 из 2")
        // Пейджер может держать и соседнюю страницу — вторая страница в дереве последняя.
        compose.onAllNodesWithContentDescription("Поделиться").onLast().performScrollTo().assertIsEnabled()
    }

    @Test
    fun accessibilityActionsReorderAndRestoreRevertsOrder() {
        val order = HadithCollectionOrder(storage)
        compose.setContent {
            InabahTheme { HadithOrderSettingsScreen(order, onBack = {}, contentPadding = PaddingValues()) }
        }
        compose.onNodeWithContentDescription("Восстановить").assertIsNotEnabled()

        // TalkBack: «Ниже» у первого сборника; у первого нет «Выше».
        val first = compose.onNodeWithText("40 хадисов ан-Навави").fetchSemanticsNode()
        val actions = first.config[SemanticsActions.CustomActions]
        check(actions.map { it.label } == listOf("Ниже")) { "действия первого: ${actions.map { it.label }}" }
        compose.runOnIdle { actions.single().action() }
        compose.runOnIdle {
            check(order.collections.value == listOf(HadithCollection.Qudsi, HadithCollection.Nawawi, HadithCollection.Ajurri))
        }

        compose.onNodeWithContentDescription("Восстановить").assertIsEnabled().performClick()
        compose.runOnIdle { check(order.isDefault) }
        compose.onNodeWithContentDescription("Восстановить").assertIsNotEnabled()
    }

    @Test
    fun resetAfterConfirmationClearsOnlyThatCollection() {
        val store = HadithStore(FixedRepository())
        val progress = HadithProgress(storage)
        val nawawi = HadithId(HadithCollection.Nawawi, 1)
        val qudsi = HadithId(HadithCollection.Qudsi, 1)
        progress.toggleMemorized(nawawi)
        progress.toggleRead(qudsi)
        compose.setContent {
            InabahTheme {
                HadithSettingsScreen(store, progress, HadithCollectionOrder(storage), onOpenOrder = {}, onBack = {},
                    contentPadding = PaddingValues())
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Прочитано: 1 из $NAWAWI_COUNT · выучено: 1").fetchSemanticsNodes().isNotEmpty()
        }

        // Порядок по умолчанию: первая «Сбросить прогресс» — ан-Навави.
        compose.onAllNodesWithText("Сбросить прогресс")[0].performClick()
        compose.onNodeWithText("Сбросить").performClick()

        compose.runOnIdle {
            check(progress.status(nawawi) == HadithStatus.None)
            check(progress.status(qudsi) == HadithStatus.Read)
        }
    }

    private fun waitForNode(description: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            // Карточка главной объединяет обе подписи показателей — достаточно одной из них.
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

/** Два хадиса ан-Навави: первый — без источника, второй — без перевода. */
private class PartialRepository : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> = emptyList()

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> = listOf(
        Hadith(HadithId(collection, 1), "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ",
            HadithTranslation(ContentLanguage.Base, narrator = null, text = "Перевод 1", source = null)),
        Hadith(HadithId(collection, 2), "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ", translation = null),
    )
}

/** Три хадиса ан-Навави, по одному в других сборниках. */
private class FixedRepository : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> = emptyList()

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> {
        val count = if (collection == HadithCollection.Nawawi) NAWAWI_COUNT else 1
        return (1..count).map { number ->
            Hadith(
                id = HadithId(collection, number),
                arabic = "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ",
                translation = HadithTranslation(ContentLanguage.Base, "Умар ибн аль-Хаттаб", "Перевод $number", "аль-Бухари"),
            )
        }
    }
}
