package app.inabah.android.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inabah.android.core.content.model.CollectionInfo
import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.content.model.ZikrTranslation
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.AzkarComposition
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.TestDataStoreRule
import app.inabah.android.feature.azkar.AzkarStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val MORNING = AzkarSection.Morning

/** «Состав азкаров»: убрать, добавить, переставить, вернуть набор по умолчанию; применение при уходе с экрана. */
@RunWith(AndroidJUnit4::class)
class AzkarCompositionScreenTest {
    // Хранилище — внешнее правило: закрывается после Compose (экран при уходе ещё пишет в него).
    @get:Rule(order = 0)
    val dataStore = TestDataStoreRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // 1-3 - набор по умолчанию, 4 - добавлен позже (optional).
    private val all = (1..4).map { number ->
        Zikr(
            ZikrId(MORNING, number), "سُبْحَانَ اللَّهِ", repetitions = 1, translation = ZikrTranslation(ContentLanguage.Base, "Перевод $number", null, null),
            optional = number == 4,
        )
    }

    private val composition by lazy { AzkarComposition(dataStore.storage) }
    private val store by lazy {
        AzkarStore(
            repository = object : ContentRepository {
                override suspend fun azkar(section: AzkarSection): List<Zikr> = if (section == MORNING) all else emptyList()
                override suspend fun hadiths(collection: HadithCollection): List<Hadith> = emptyList()
                override suspend fun collectionInfo(collection: HadithCollection) = CollectionInfo.Empty
            },
            storage = dataStore.storage,
            windowSettings = AzkarWindowSettings(dataStore.storage),
            history = AzkarHistory(dataStore.storage) { throw AssertionError(it) },
            now = { Instant.parse("2026-10-03T06:00:00Z") },
            zone = { ZoneId.of("Europe/Berlin") },
            onUnreadableProgress = { _, error -> throw AssertionError(error) },
            composition = composition,
        ).also { runBlocking { it.load(MORNING) } }
    }

    private var shown by mutableStateOf(true)

    @Before
    fun enableChecks() = compose.enableAccessibilityChecks()

    private fun show() = compose.setContent {
        InabahTheme {
            if (shown) AzkarCompositionScreen(MORNING, store, composition, onBack = {}, contentPadding = PaddingValues())
        }
    }

    private fun numbers() = store.sessions(MORNING).map { it.id.number }

    @Test
    fun removeAndAddApplyOnlyWhenLeaving() {
        show()
        compose.onNodeWithText("В наборе · 3").assertExists()
        compose.onNodeWithText("Можно добавить · 1").assertExists()

        compose.onAllNodesWithContentDescription("Убрать")[0].performClick()
        // «Можно добавить» теперь: 1 (только что убран) и 4 - добавляется 4.
        compose.onNodeWithText("Можно добавить · 2").assertExists()
        compose.onAllNodesWithContentDescription("Добавить")[1].performClick()
        compose.onNodeWithText("В наборе · 3").assertExists()
        compose.runOnIdle { assertEquals("пока экран открыт - черновик", listOf(1, 2, 3), numbers()) }

        shown = false
        compose.runOnIdle { assertEquals(listOf(2, 3, 4), numbers()) }
    }

    @Test
    fun accessibilityActionMovesZikrDown() {
        show()
        val actions = compose.onNodeWithText("Перевод 1").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Ниже", "Убрать"), actions.map { it.label })
        compose.runOnIdle { actions.first().action() }

        shown = false
        compose.runOnIdle { assertEquals(listOf(2, 1, 3), numbers()) }
    }

    @Test
    fun lastZikrCannotBeRemoved() {
        composition.set(MORNING, listOf(2), all)
        store.applyComposition(MORNING)
        show()

        compose.onNodeWithText("В наборе · 1").assertExists()
        compose.onNodeWithContentDescription("Убрать").assertIsNotEnabled()
    }

    @Test
    fun restoreDefaultAsksAndAppliesAtOnce() {
        composition.set(MORNING, listOf(3, 4), all)
        store.applyComposition(MORNING)
        show()

        compose.onNodeWithContentDescription("Вернуть набор по умолчанию").performClick()
        compose.onNodeWithText("Вернуть набор по умолчанию?").assertExists()
        compose.onNodeWithText("Вернуть").performClick()

        compose.runOnIdle { assertEquals(listOf(1, 2, 3), numbers()) }
        compose.onNodeWithText("В наборе · 3").assertExists()
        compose.onNodeWithContentDescription("Вернуть набор по умолчанию").assertIsNotEnabled()
    }
}
