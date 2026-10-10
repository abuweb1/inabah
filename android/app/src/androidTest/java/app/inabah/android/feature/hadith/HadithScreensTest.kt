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
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.CollectionAuthor
import app.inabah.android.core.content.model.CollectionInfo
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.content.model.HadithNote
import app.inabah.android.core.content.model.HadithTranslation
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.HadithCollectionOrder
import app.inabah.android.core.settings.HadithFavorites
import app.inabah.android.core.settings.HadithProgress
import app.inabah.android.core.settings.HadithStatus
import app.inabah.android.core.settings.PreferencesStorage
import app.inabah.android.core.settings.ReminderSettings
import app.inabah.android.core.settings.TestDataStoreRule
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.feature.settings.HadithOrderSettingsScreen
import app.inabah.android.feature.settings.HadithSettingsScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val NAWAWI_COUNT = 3
private const val TIMEOUT_MILLIS = 5_000L
private const val NAWAWI_AUTHOR = "Имам ан-Навави"
private const val NAWAWI_YEARS = "631–676 гг. х."
private const val ABOUT_PARAGRAPH = "Абзац о сборнике."
private const val NOTE_TEXT = "Пояснение к слову."

/** Экраны хадисов: отметки, листание, порядок сборников, сброс (критерии этапа 4, docs/android/09). */
@RunWith(AndroidJUnit4::class)
class HadithScreensTest {
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
                    Screen.Detail -> HadithDetailScreen(HadithId(HadithCollection.Nawawi, 1), store, progress, HadithFavorites(storage),
                        ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
                    Screen.List -> HadithListScreen(HadithCollection.Nawawi, store, progress, onBack = {},
                        onOpenHadith = {}, onOpenAbout = {}, contentPadding = PaddingValues())
                    Screen.Home -> HadithHomeScreen(store, progress, order, onOpenCollection = {}, onOpenFavorites = {},
                        contentPadding = PaddingValues())
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
                    HadithFavorites(storage), ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
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
                    HadithFavorites(storage), ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
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
                    HadithFavorites(storage), ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
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
                HadithSettingsScreen(store, progress, HadithFavorites(storage), HadithCollectionOrder(storage), ReminderSettings(storage),
                    onOpenOrder = {}, onBack = {},
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

    @Test
    fun aboutButtonInListOpensCollectionAbout() {
        var opened = false
        compose.setContent {
            InabahTheme {
                HadithListScreen(HadithCollection.Nawawi, HadithStore(FixedRepository()), HadithProgress(storage),
                    onBack = {}, onOpenHadith = {}, onOpenAbout = { opened = true }, contentPadding = PaddingValues())
            }
        }
        waitForNode("О сборнике")
        compose.onNodeWithContentDescription("О сборнике").performClick()
        compose.runOnIdle { check(opened) }
    }

    @Test
    fun collectionAboutShowsAuthorBookAndProgress() {
        val progress = HadithProgress(storage)
        progress.toggleMemorized(HadithId(HadithCollection.Nawawi, 1))
        compose.setContent {
            InabahTheme {
                CollectionAboutScreen(HadithCollection.Nawawi, HadithStore(FixedRepository()), progress,
                    onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("Составитель")
        compose.onNodeWithText(NAWAWI_AUTHOR).assertExists()
        compose.onNodeWithText(NAWAWI_YEARS).assertExists()
        compose.onNodeWithText("О книге").assertExists()
        compose.onNodeWithText(ABOUT_PARAGRAPH).performScrollTo()
        compose.onNodeWithText("Прочитано: 1 из $NAWAWI_COUNT · выучено: 1").performScrollTo()
    }

    @Test
    fun collectionAboutWithoutAuthorsHasNoAuthorsGroup() {
        compose.setContent {
            InabahTheme {
                CollectionAboutScreen(HadithCollection.Qudsi, HadithStore(FixedRepository()), HadithProgress(storage),
                    onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("О книге")
        compose.onAllNodesWithText("Составитель").assertCountEquals(0)
        compose.onAllNodesWithText("Составители").assertCountEquals(0)
    }

    @Test
    fun collectionAboutRetriesAfterLoadError() {
        compose.setContent {
            InabahTheme {
                CollectionAboutScreen(HadithCollection.Nawawi, HadithStore(InfoFailsOnceRepository()), HadithProgress(storage),
                    onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("Повторить")
        compose.onNodeWithText("Повторить").performClick()
        waitForText("О книге")
    }

    @Test
    fun bookmarkOnDetailAddsHadithToFavoritesUnderItsCollection() {
        val store = HadithStore(FixedRepository())
        val progress = HadithProgress(storage)
        val favorites = HadithFavorites(storage)
        val order = HadithCollectionOrder(storage)
        favorites.toggle(HadithId(HadithCollection.Ajurri, 1))
        var showFavorites by mutableStateOf(false)
        compose.setContent {
            InabahTheme {
                if (showFavorites) {
                    FavoritesScreen(store, progress, favorites, order, onBack = {}, onOpenHadith = {},
                        contentPadding = PaddingValues())
                } else {
                    HadithDetailScreen(HadithId(HadithCollection.Nawawi, 1), store, progress, favorites,
                        ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
                }
            }
        }
        waitForNode("В избранном")
        // Пейджер заранее строит и соседнюю страницу — первая в дереве открытая.
        compose.onAllNodesWithContentDescription("В избранном")[0].performScrollTo().performClick()
        compose.onAllNodesWithContentDescription("В избранном")[0].assertIsOn()
        compose.onAllNodesWithContentDescription("В избранном")[1].assertIsOff()

        showFavorites = true
        waitForText("40 хадисов ан-Навави")
        // Группы — в порядке главной (по умолчанию ан-Навави выше аль-Аджурри).
        val nawawiTop = compose.onNodeWithText("40 хадисов ан-Навави").fetchSemanticsNode().boundsInRoot.top
        val ajurriTop = compose.onNodeWithText("40 хадисов аль-Аджурри").fetchSemanticsNode().boundsInRoot.top
        check(nawawiTop < ajurriTop) { "ан-Навави ($nawawiTop) должен быть выше аль-Аджурри ($ajurriTop)" }
        compose.onAllNodesWithContentDescription("Хадис 1", substring = true).assertCountEquals(2)
        compose.onAllNodesWithText("40 хадисов аль-Къудси").assertCountEquals(0)
    }

    @Test
    fun emptyFavoritesShowsNotice() {
        compose.setContent {
            InabahTheme {
                FavoritesScreen(HadithStore(FixedRepository()), HadithProgress(storage), HadithFavorites(storage),
                    HadithCollectionOrder(storage), onBack = {}, onOpenHadith = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("Здесь появятся хадисы, которые вы добавите")
    }

    @Test
    fun favoritesButtonOnHomeOpensFavorites() {
        var opened = false
        compose.setContent {
            InabahTheme {
                HadithHomeScreen(HadithStore(FixedRepository()), HadithProgress(storage), HadithCollectionOrder(storage),
                    onOpenCollection = {}, onOpenFavorites = { opened = true }, contentPadding = PaddingValues())
            }
        }
        waitForNode("Избранное")
        compose.onNodeWithContentDescription("Избранное").performClick()
        compose.runOnIdle { check(opened) }
    }

    @Test
    fun clearFavoritesAfterConfirmationKeepsMarks() {
        val progress = HadithProgress(storage)
        val favorites = HadithFavorites(storage)
        val nawawi = HadithId(HadithCollection.Nawawi, 1)
        progress.toggleRead(nawawi)
        favorites.toggle(nawawi)
        compose.setContent {
            InabahTheme {
                HadithSettingsScreen(HadithStore(FixedRepository()), progress, favorites, HadithCollectionOrder(storage), ReminderSettings(storage),
                    onOpenOrder = {}, onBack = {}, contentPadding = PaddingValues())
            }
        }
        compose.onNodeWithText("Очистить избранное").performScrollTo().performClick()
        compose.onNodeWithText("Очистить").performClick()

        compose.runOnIdle {
            check(favorites.favorites.value.isEmpty())
            check(progress.status(nawawi) == HadithStatus.Read)
        }
        compose.onNodeWithText("Очистить избранное").assertIsNotEnabled()
    }

    @Test
    fun notesHiddenUntilToggledAndCollapsedAgainAfterPaging() {
        compose.setContent {
            InabahTheme {
                HadithDetailScreen(HadithId(HadithCollection.Nawawi, 1), HadithStore(NotesRepository()), HadithProgress(storage),
                    HadithFavorites(storage), ReadingSettings(storage), onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForNode("1 из 2")
        // Свёрнутые сноски не в дереве доступности — их не видно и TalkBack.
        compose.onAllNodesWithText(NOTE_TEXT).assertCountEquals(0)
        compose.onNodeWithContentDescription("Сноски: 2").performScrollTo().performClick()
        compose.onNodeWithText(NOTE_TEXT).assertExists()

        compose.onNodeWithContentDescription("Следующий хадис").performClick()
        waitForNode("2 из 2")
        compose.onNodeWithContentDescription("Предыдущий хадис").performClick()
        waitForNode("1 из 2")
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(NOTE_TEXT).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
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

    override suspend fun collectionInfo(collection: HadithCollection): CollectionInfo = CollectionInfo.Empty

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> = listOf(
        Hadith(HadithId(collection, 1), "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ",
            HadithTranslation(ContentLanguage.Base, narrator = null, text = "Перевод 1", source = null)),
        Hadith(HadithId(collection, 2), "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ", translation = null),
    )
}

/** Три хадиса ан-Навави, по одному в других сборниках; составитель — только у ан-Навави. */
private open class FixedRepository : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> = emptyList()

    override suspend fun collectionInfo(collection: HadithCollection): CollectionInfo = CollectionInfo(
        authors = if (collection == HadithCollection.Nawawi) listOf(CollectionAuthor(NAWAWI_AUTHOR, NAWAWI_YEARS)) else emptyList(),
        about = listOf(ABOUT_PARAGRAPH),
    )

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

/** Как [FixedRepository], но первый запрос «О сборнике» — ошибка: проверка «Повторить». */
private class InfoFailsOnceRepository : FixedRepository() {
    private var failed = false

    override suspend fun collectionInfo(collection: HadithCollection): CollectionInfo {
        if (!failed) {
            failed = true
            throw ContentError.ResourceMissing("collections.json")
        }
        return super.collectionInfo(collection)
    }
}

/** Два хадиса ан-Навави: у первого две сноски, у второго — нет. */
private class NotesRepository : FixedRepository() {
    override suspend fun hadiths(collection: HadithCollection): List<Hadith> = listOf(
        Hadith(HadithId(collection, 1), "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ", HadithTranslation(
            ContentLanguage.Base, "Умар ибн аль-Хаттаб", "Перевод 1", "аль-Бухари",
            notes = listOf(HadithNote("Слово", NOTE_TEXT), HadithNote("Различия передач", "Иначе у ат-Тирмизи.")),
        )),
        Hadith(HadithId(collection, 2), "إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ",
            HadithTranslation(ContentLanguage.Base, "Умар ибн аль-Хаттаб", "Перевод 2", "аль-Бухари")),
    )
}
