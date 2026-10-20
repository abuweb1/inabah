package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.content.AssetContentRepository
import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.content.model.closedSyllables
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.core.settings.TestDataStoreRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_MILLIS = 5_000L
/** Вступление экрана целиком: 0,5 с до волны, волна, «зов» 0,9 с - с запасом, но короче тишины 6 с. */
private const val INTRO_MILLIS = 4_000L
private const val QUIZ_SEED = 42L

/** «Учимся читать»: главная, урок с листом буквы и «глазом», проверка (настоящий reading.json из ассетов). */
@RunWith(AndroidJUnit4::class)
class ReadingScreensTest {
    // Хранилище — внешнее правило: закрывается после Compose.
    @get:Rule(order = 0)
    val dataStore = TestDataStoreRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val repository = AssetContentRepository(
        InstrumentationRegistry.getInstrumentation().targetContext.assets::open,
        ContentLanguagePriority(listOf(ContentLanguage.Base)),
        Dispatchers.IO,
    )
    private val store = ReadingStore(repository)

    @Before
    fun enableChecks() {
        compose.enableAccessibilityChecks()
        // Курс загружен заранее: первый разбор reading.json на эмуляторе бывает дольше ожидания.
        runBlocking { store.load() }
    }

    @Test
    fun homeCardShowsCurrentStep() {
        var opened = false
        compose.setContent {
            InabahTheme {
                MakharijHomeScreen(store, ReadingProgress(dataStore.storage), onOpenSteps = { opened = true },
                    contentPadding = PaddingValues())
            }
        }
        waitForText("Шаг 1 из 12 · Буквы")
        compose.onNodeWithText("Учимся читать").performClick()
        assertTrue(opened)
    }

    @Test
    fun alphabetPlaybackShowsBarAndStops() {
        compose.setContent {
            InabahTheme {
                ReadingLessonScreen(1, store, ReadingProgress(dataStore.storage), ReadingSettings(dataStore.storage),
                    onBack = {}, onOpenQuiz = {}, onOpenWords = {}, onOpenAlphabet = {}, onOpenLetterForms = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("Шаг 1 · Буквы")
        compose.onNodeWithContentDescription("Послушать алфавит").performClick()
        compose.onNodeWithContentDescription("Пауза").performClick()
        compose.onNodeWithContentDescription("Продолжить").assertExists()
        compose.onNodeWithContentDescription("Следующая буква").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText("из 29", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Стоп")[0].performClick()
        compose.onNodeWithContentDescription("Послушать алфавит").assertExists()
    }

    @Test
    fun letterSheetOpensFromAlphabetAndEyeHidesTransliteration() {
        val settings = ReadingSettings(dataStore.storage)
        compose.setContent {
            InabahTheme {
                ReadingLessonScreen(1, store, ReadingProgress(dataStore.storage), settings, onBack = {}, onOpenQuiz = {}, onOpenWords = {}, onOpenAlphabet = {}, onOpenLetterForms = {},
                    contentPadding = PaddingValues())
            }
        }
        waitForText("Шаг 1 · Буквы")
        compose.onNodeWithContentDescription("Льам").performClick()
        compose.onNodeWithText("23-я буква").assertExists()
        // До огласовок слоги цепочки — «ещё не пройдено».
        compose.onNodeWithContentDescription("фатхьа, льа, ещё не пройдено").assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText("23-я буква").fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithContentDescription("Скрыть транскрипцию").performClick()
        compose.onNodeWithContentDescription("Показать транскрипцию").assertExists()
        assertTrue(settings.transliterationHidden.value)
    }

    @Test
    fun lessonIntroShowsHintOnceAndTapHidesIt() {
        val settings = ReadingSettings(dataStore.storage)
        compose.setContent {
            InabahTheme {
                ReadingLessonScreen(1, store, ReadingProgress(dataStore.storage), settings, onBack = {}, onOpenQuiz = {}, onOpenWords = {},
                    onOpenAlphabet = {}, onOpenLetterForms = {}, contentPadding = PaddingValues())
            }
        }
        // Первое открытие: после волны - подсказка у первой буквы (не мешает нажатиям).
        waitForText("Нажмите на букву")
        assertFalse(settings.introSeen("lesson.1"))
        // Часы стоят: отметка - от нажатия, а не от конца 4 с подсказки.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Алиф").performClick()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("нажали букву - урок отмечен, подсказки больше не будет", settings.introSeen("lesson.1"))
        compose.mainClock.autoAdvance = true
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText("Нажмите на букву").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun answeringAllQuestionsCompletesStep() {
        val progress = ReadingProgress(dataStore.storage)
        compose.setContent {
            InabahTheme {
                ReadingQuizScreen(2, store, progress, onBack = {}, onOpenStep = {}, onOpenSteps = {}, onOpenWords = {},
                    contentPadding = PaddingValues(), seed = QUIZ_SEED)
            }
        }
        // Заход - 16 случайных вопросов из запаса шага; с тем же зерном - те же, что у quizRound.
        val course = (store.course.value as Loadable.Loaded).value
        val round = quizRound(course, requireNotNull(course.step(2)), QUIZ_SEED)
        waitForText("вопрос 1 из ${round.size}")
        // Неверный ответ - «Неверно», «Дальше» нет, верный не подсвечивается сам.
        compose.onNode(hasClickAction() and hasText(round[0].options.first { !it.isCorrect }.text)).performClick()
        waitForText("Неверно, попробуйте ещё раз")
        assertTrue(compose.onAllNodesWithText("Дальше").fetchSemanticsNodes().isEmpty())
        round.forEachIndexed { i, item ->
            compose.onNode(hasClickAction() and hasText(item.options.single { it.isCorrect }.text)).performClick()
            compose.onNodeWithText(if (i == round.lastIndex) "Завершить" else "Дальше").performScrollTo().performClick()
        }
        waitForText("Шаг пройден")
        assertEquals(setOf(2), progress.lessonsDone.value)
        // У шага есть слова: после проверки - «Читаем слова», не следующий шаг.
        compose.onNodeWithText("Читаем слова").assertExists()
    }

    @Test
    fun wordTileCountsReadsAndFirstGroupHasTransliteration() {
        val progress = ReadingProgress(dataStore.storage)
        compose.setContent {
            InabahTheme {
                ReadingWordsScreen(2, store, progress, ReadingSettings(dataStore.storage), onBack = {}, onOpenStep = {},
                    onOpenQuiz = {}, onOpenSteps = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("группа 1 из 4")
        // Слова перемешаны: первая плитка группы - любое слово; в первой группе - с транскрипцией.
        val first = compose.onAllNodes(hasClickAction() and hasStateDescriptionPrefix("прочитано 0 из"))[0].fetchSemanticsNode()
        val description = first.config[SemanticsProperties.ContentDescription].single()
        assertTrue(description, description.contains(", "))
        val tile = compose.onNodeWithContentDescription(description)
        repeat(3) { tile.performClick() }
        assertEquals(3, progress.wordCounts.value[2]?.max())
        // Свайп - в обе стороны (кнопки - тоже).
        tile.performTouchInput { swipeLeft() }
        waitForText("группа 2 из 4")
        compose.onNodeWithText("‹ Назад").performClick()
        waitForText("группа 1 из 4")
        compose.onNodeWithText("Дальше ›").performClick()
        waitForText("группа 2 из 4")
        // Во второй группе - без транскрипции и без глаза.
        assertTrue(compose.onAllNodesWithContentDescription("Скрыть транскрипцию").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun letterFormsScreenSwitchesLettersOfGroup() {
        compose.setContent {
            InabahTheme {
                ReadingLetterFormsScreen(3, "ب", store, ReadingSettings(dataStore.storage), onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("буква 1 из 8")
        compose.onNodeWithText("Ба").assertExists()
        compose.onNodeWithContentDescription("Хо").performClick()
        waitForText("буква 8 из 8")
    }

    @Test
    fun letterCardHintShowsTransliterationAndLongPressZoomsWord() {
        val settings = ReadingSettings(dataStore.storage)
        // Надпись-подсказка первого открытия - свой тест; волна и «зов» идут при каждом открытии -
        // ждём их конца, иначе их анимации торопили бы часы теста мимо подсказки 0,7 с.
        settings.markIntroSeen("letterCard")
        compose.setContent {
            InabahTheme {
                ReadingLetterFormsScreen(3, "ب", store, settings, onBack = {}, contentPadding = PaddingValues())
            }
        }
        waitForText("буква 1 из 8")
        compose.mainClock.advanceTimeBy(INTRO_MILLIS)
        compose.waitForIdle()
        // Транскрипция слов-примеров скрыта, лампочка показывает её на миг.
        compose.onNodeWithContentDescription("بَكُمَ").assertExists()
        compose.onNodeWithContentDescription("Подсказка: показать транскрипцию").performClick()
        compose.onNodeWithContentDescription("بَكُمَ, бакума").assertExists()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("بَكُمَ").fetchSemanticsNodes().isNotEmpty() }

        // Долгое нажатие - слово увеличено поверх (на месте ячейка прозрачна), «Назад» - на место.
        compose.onNodeWithContentDescription("بَكُمَ").performTouchInput { longClick() }
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("بَكُمَ").fetchSemanticsNodes().size == 2 }
        assertTrue("увеличили - плитки больше не «зовут»", settings.zoomLearned.value)
        Espresso.pressBack()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("بَكُمَ").fetchSemanticsNodes().size == 1 }
        waitForText("буква 1 из 8")
    }


    @Test
    fun finalLessonListsZikrsWithoutDoneButton() {
        val final = runBlocking { repository.reading() }.steps.last()
        var opened: Int? = null
        compose.setContent {
            InabahTheme {
                ReadingLessonScreen(final.number, store, ReadingProgress(dataStore.storage), ReadingSettings(dataStore.storage),
                    onBack = {}, onOpenQuiz = {}, onOpenWords = {}, onOpenAlphabet = {}, onOpenLetterForms = {},
                    onOpenZikr = { opened = it }, contentPadding = PaddingValues())
            }
        }
        val last = final.zikrs.last()
        val card = hasClickAction() and hasText(last.arabic, substring = true)
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(final.zikrs.size, compose.onAllNodes(hasClickAction() and hasText("·", substring = true)).fetchSemanticsNodes().size)
        compose.onNode(card).performScrollTo().performClick()
        assertEquals(final.zikrs.lastIndex, opened)
        // Итог проходится зикрами - кнопки «Готово» нет.
        assertTrue(compose.onAllNodesWithText("Готово").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun zikrScreenPlaysAndStopsAndLastWordWithWholeCompletesFinal() {
        val final = runBlocking { repository.reading() }.steps.last()
        val progress = ReadingProgress(dataStore.storage)
        // Прочитано всё, кроме последнего слова последнего зикра и его «целиком».
        for (zikr in final.zikrs) {
            zikr.words.indices.forEach { if (zikr != final.zikrs.last() || it != zikr.words.lastIndex) progress.readZikrWord(zikr.zikr, it) }
            if (zikr != final.zikrs.last()) progress.markZikrRead(zikr.zikr)
        }
        val zikr = final.zikrs.last()
        val audio = requireNotNull(zikr.audio)
        compose.setContent {
            var playing by remember { mutableStateOf<String?>(null) }
            InabahTheme {
                ReadingZikrScreen(final.number, final.zikrs.lastIndex, store, progress, ReadingSettings(dataStore.storage),
                    onBack = {}, onOpenAzkar = {}, onOpenStep = {}, onOpenSteps = {}, contentPadding = PaddingValues(),
                    clips = setOf(audio), playingClip = playing, onPlayClip = { playing = it }, onStopClip = { playing = null })
            }
        }
        waitForText("Послушать")
        compose.onNodeWithText("Послушать").performScrollTo().performClick()
        compose.onNodeWithText("Стоп").performClick()
        compose.onNodeWithText("Послушать").assertExists()

        // Плитка зикра - без счёта: TalkBack читает «не прочитано», не «прочитано 0 из 1».
        val unread = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "не прочитано")
        assertEquals(1, compose.onAllNodes(unread).fetchSemanticsNodes().size)
        compose.onNode(unread).performScrollTo().performClick()
        assertTrue(compose.onAllNodes(unread).fetchSemanticsNodes().isEmpty())
        assertFalse(final.number in progress.lessonsDone.value)

        compose.onNodeWithText("Прочитал целиком").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("Машаа Аллах!").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(final.number in progress.lessonsDone.value)
    }

    @Test
    fun letterSheetShowsClosedSyllablesFromSukunStep() {
        val course = (store.course.value as Loadable.Loaded).value
        val ba = course.letters.first { it.letter == "ب" }
        compose.setContent {
            InabahTheme {
                Box(Modifier.fillMaxSize()) {
                    LetterSheet(course, ba, stepNumber = 7, transliterationHidden = false, onDismiss = {}, contentPadding = PaddingValues())
                }
            }
        }
        waitForText("2-я буква")
        // Согласная с сукуном одна не произносится - закрытые слоги, а не «звук б».
        for (closed in listOf("ъаб", "ъиб", "ъуб")) compose.onNodeWithContentDescription("сукун, $closed").assertExists()
    }

    @Test
    fun closedSyllableWithRecordingPlaysOnTap() {
        val course = (store.course.value as Loadable.Loaded).value
        val file = "reading_02_closed_fatha.m4a"
        val ba = course.letters.first { it.letter == "ب" }.copy(audio = mapOf("closedFatha" to file))
        val vowels = course.marks.filter { it.id in ReadingLetter.CLOSED_VOWELS }
        val row = closedSyllables(ba, requireNotNull(course.alif), vowels, requireNotNull(course.sukun))
        val played = mutableListOf<String>()
        compose.setContent {
            InabahTheme {
                ClosedSyllablesTable(ReadingBlock.ClosedSyllables(vowels, listOf(row)), hidden = false, clips = setOf(file), playingClip = null, onPlay = { played += it })
            }
        }
        // Слог - без формулы, просто «أَبْ»; с записью нажимается, без записи - нет.
        compose.onNodeWithContentDescription("Ба, ъаб").assertHasClickAction().performClick()
        compose.onNodeWithContentDescription("Ба, ъиб").assertHasNoClickAction()
        assertEquals(listOf(file), played)
    }

    private fun hasStateDescriptionPrefix(prefix: String) = SemanticsMatcher("StateDescription начинается с «$prefix»") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith(prefix) == true
    }

    private fun waitForText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
