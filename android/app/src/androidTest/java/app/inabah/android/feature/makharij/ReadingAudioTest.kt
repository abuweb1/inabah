package app.inabah.android.feature.makharij

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inabah.android.core.audio.ClipPlayback
import app.inabah.android.core.content.AssetContentRepository
import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.ContentLanguagePriority
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.settings.ReadingProgress
import app.inabah.android.core.settings.ReadingSettings
import app.inabah.android.core.settings.TestDataStoreRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TIMEOUT_MILLIS = 5_000L
private const val LAM = "ل"

/** Записи «Учимся читать»: цепочка листа буквы и «Послушать алфавит» со звуком (подделка плеера, настоящий курс). */
@RunWith(AndroidJUnit4::class)
class ReadingAudioTest {
    @get:Rule(order = 0)
    val dataStore = TestDataStoreRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val assets = AssetContentRepository(
        InstrumentationRegistry.getInstrumentation().targetContext.assets::open,
        ContentLanguagePriority(listOf(ContentLanguage.Base)),
        Dispatchers.IO,
    )

    /** Курс, в котором у Льама есть название и слоги, а у Алифа и Ба - название. */
    private val repository = object : ContentRepository by assets {
        override suspend fun reading(): ReadingCourse = withAudio(assets.reading())
    }

    @Before
    fun enableChecks() {
        compose.enableAccessibilityChecks()
    }

    @Test
    fun letterSheetPlaysChainInOrderAndChipAlone() {
        val clips = FakeClips(LAM_FILES.toSet() + "other.m4a")
        val course = runBlocking { repository.reading() }
        val lam = course.letters.first { it.letter == LAM }
        compose.setContent {
            InabahTheme {
                Box(Modifier.fillMaxSize()) {
                    LetterSheet(course, lam, stepNumber = 2, transliterationHidden = false, onDismiss = {},
                        contentPadding = PaddingValues(), clips = clips)
                }
            }
        }
        // Открыли лист - цепочка сама: название, затем слоги по очереди; во время цепочки в шапке ⏹.
        compose.waitUntil(TIMEOUT_MILLIS) { clips.played == LAM_FILES.take(1) }
        compose.onNodeWithContentDescription("Стоп").assertExists()
        for (count in 2..LAM_FILES.size) {
            clips.finish()
            compose.waitUntil(TIMEOUT_MILLIS) { clips.played.size == count }
        }
        assertEquals(LAM_FILES, clips.played)
        clips.finish()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("Прослушать").fetchSemanticsNodes().isNotEmpty() }

        // Элемент цепочки по нажатию - только он, дальше цепочка не идёт.
        compose.onNodeWithContentDescription("касра, ли").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.played.last() == KASRA }
        clips.finish()
        compose.mainClock.advanceTimeBy(CHAIN_WAIT_MILLIS)
        assertEquals(LAM_FILES.size + 1, clips.played.size)

        // ⏹ посреди цепочки - тишина и снова ▶.
        compose.onNodeWithContentDescription("Прослушать").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.playing.value == LAM_FILES.first() }
        compose.onNodeWithContentDescription("Стоп").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("Прослушать").fetchSemanticsNodes().isNotEmpty() }
        assertNull(clips.playing.value)
    }

    @Test
    fun closingSheetStopsChain() {
        val clips = FakeClips(LAM_FILES.toSet())
        val course = runBlocking { repository.reading() }
        val lam = course.letters.first { it.letter == LAM }
        var open: ReadingLetter? by mutableStateOf(lam)
        compose.setContent {
            InabahTheme {
                Box(Modifier.fillMaxSize()) {
                    LetterSheet(course, open, stepNumber = 2, transliterationHidden = false, onDismiss = { open = null },
                        contentPadding = PaddingValues(), clips = clips)
                }
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) { clips.playing.value == LAM_FILES.first() }
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.playing.value == null }
        compose.mainClock.advanceTimeBy(CHAIN_WAIT_MILLIS)
        assertEquals(LAM_FILES.take(1), clips.played)
    }

    @Test
    fun interruptedChainDoesNotGoOn() {
        val clips = FakeClips(LAM_FILES.toSet())
        val course = runBlocking { repository.reading() }
        val lam = course.letters.first { it.letter == LAM }
        compose.setContent {
            InabahTheme {
                Box(Modifier.fillMaxSize()) {
                    LetterSheet(course, lam, stepNumber = 2, transliterationHidden = false, onDismiss = {},
                        contentPadding = PaddingValues(), clips = clips)
                }
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) { clips.playing.value == LAM_FILES.first() }
        clips.interrupt()
        compose.mainClock.advanceTimeBy(CHAIN_WAIT_MILLIS)
        assertEquals("после прерывания - тишина, не через динамик", LAM_FILES.take(1), clips.played)
        compose.onNodeWithContentDescription("Прослушать").assertExists()
    }

    @Test
    fun openingLetterSheetPausesAlphabet() {
        val clips = FakeClips(setOf(ALIF_NAME, BA_NAME) + LAM_FILES)
        val store = ReadingStore(repository).also { runBlocking { it.load() } }
        compose.setContent {
            InabahTheme {
                ReadingLessonScreen(1, store, ReadingProgress(dataStore.storage), ReadingSettings(dataStore.storage),
                    onBack = {}, onOpenQuiz = {}, onOpenWords = {}, onOpenAlphabet = {}, onOpenLetterForms = {},
                    contentPadding = PaddingValues(), clips = clips)
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("Послушать алфавит").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Послушать алфавит").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.played == listOf(ALIF_NAME) }
        // Открыли лист Льама - алфавит на паузе, звучит цепочка листа.
        compose.onNodeWithContentDescription("Льам").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.playing.value == LAM_FILES.first() }
        // Урок за листом скрыт для TalkBack - кнопка панели в полном дереве.
        compose.onNodeWithContentDescription("Продолжить", useUnmergedTree = true).assertExists()
        clips.finish()
        compose.mainClock.advanceTimeBy(CHAIN_WAIT_MILLIS)
        assertEquals("алфавит не перебивает лист", false, BA_NAME in clips.played)
    }

    @Test
    fun alphabetPlaysLetterNamesOneAfterAnother() {
        val clips = FakeClips(setOf(ALIF_NAME, BA_NAME))
        // Курс загружен заранее: первый разбор reading.json на эмуляторе бывает дольше ожидания.
        val store = ReadingStore(repository).also { runBlocking { it.load() } }
        compose.setContent {
            InabahTheme {
                ReadingLessonScreen(1, store, ReadingProgress(dataStore.storage), ReadingSettings(dataStore.storage),
                    onBack = {}, onOpenQuiz = {}, onOpenWords = {}, onOpenAlphabet = {}, onOpenLetterForms = {},
                    contentPadding = PaddingValues(), clips = clips)
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodesWithContentDescription("Послушать алфавит").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Послушать алфавит").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.played == listOf(ALIF_NAME) }
        // Следующая буква - когда название прозвучало, не по таймеру.
        compose.mainClock.advanceTimeBy(CHAIN_WAIT_MILLIS)
        assertEquals(listOf(ALIF_NAME), clips.played)
        clips.finish()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.played == listOf(ALIF_NAME, BA_NAME) }
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText("2 из 29")).fetchSemanticsNodes().isNotEmpty() }

        // Пауза - звучащая буква замолкает.
        compose.onNodeWithContentDescription("Пауза").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { clips.playing.value == null }
    }

    /** Подделка плеера: [play] - запись звучит, пока тест не скажет [finish]. */
    private class FakeClips(files: Set<String>) : ClipPlayback {
        override val available = MutableStateFlow(files)
        override val playing = MutableStateFlow<String?>(null)
        override val interruptions = MutableStateFlow(0)
        val played = mutableListOf<String>()

        override fun play(file: String) {
            if (file !in available.value) return
            played += file
            playing.value = file
        }

        override fun stop() {
            playing.value = null
        }

        fun finish() {
            playing.value = null
        }

        /** Звук забрали (вынули наушники, плеер азкаров) - как `ClipPlayer`. */
        fun interrupt() {
            interruptions.value += 1
            playing.value = null
        }
    }

    private companion object {
        const val ALIF_NAME = "reading_01_name.m4a"
        const val BA_NAME = "reading_02_name.m4a"
        const val KASRA = "reading_23_kasra.m4a"
        val LAM_FILES = listOf("reading_23_name.m4a", "reading_23_fatha.m4a", KASRA, "reading_23_damma.m4a")

        /** Дольше любой паузы между записями: если бы цепочка шла дальше, следующая уже прозвучала бы. */
        const val CHAIN_WAIT_MILLIS = 3_000L

        fun withAudio(letter: ReadingLetter): ReadingLetter = when (letter.letter) {
            LAM -> letter.copy(audio = mapOf("name" to LAM_FILES[0], "fatha" to LAM_FILES[1], "kasra" to LAM_FILES[2], "damma" to LAM_FILES[3]))
            "ا" -> letter.copy(audio = mapOf("name" to ALIF_NAME))
            "ب" -> letter.copy(audio = mapOf("name" to BA_NAME))
            else -> letter
        }

        /** Записи - и в списке букв курса, и в сетке букв урока. */
        fun withAudio(course: ReadingCourse): ReadingCourse = course.copy(
            letters = course.letters.map(::withAudio),
            steps = course.steps.map { step ->
                step.copy(blocks = step.blocks.map { block -> if (block is ReadingBlock.Letters) block.copy(letters = block.letters.map(::withAudio)) else block })
            },
        )
    }
}
