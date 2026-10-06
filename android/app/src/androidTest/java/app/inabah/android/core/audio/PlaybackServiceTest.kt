package app.inabah.android.core.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Тон — 8 с; плейлист из двух на 1,5× с паузой — около 12 с. */
private const val TIMEOUT_MILLIS = 30_000L

/**
 * Настоящий путь звука: контроллер → MediaControllerEngine → служба PlaybackService с ExoPlayer и
 * записями из ассетов — тестовыми тонами (`test-tone-*.wav`, только в debug-сборке: своих записей
 * в приложении пока нет). Ловит то, чего не видят тесты на подделке: события плеера не доходят до
 * приложения, служба не подключается, пауза-тишина не играет.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = AudioPlayerController(MediaControllerEngine(context))

    /** Тестовый тон (8 с, общий с тестами iOS) под видом утреннего зикра. */
    private fun shortTrack(number: Int = 1) = AudioTrack(
        id = "azkar.morning.$number",
        assetFile = "test-tone-$number.wav",
        title = "Зикр №$number",
        subtitle = "Утренние азкары",
        category = "АЗКАРЫ",
    )

    private fun onMain(block: () -> Unit) = runBlocking { withContext(Dispatchers.Main) { block() } }

    @After
    fun tearDown() {
        onMain { player.close() }
        scope.cancel()
    }

    @Test
    fun singleTrackPlaysToTheEndThroughService() = runBlocking {
        onMain {
            scope.launch { player.run() }
            player.play(shortTrack())
        }
        val finished = withTimeout(TIMEOUT_MILLIS) { player.state.first { it.isFinished } }
        assertTrue("длительность известна", finished.durationMs > 0)
        assertEquals(false, finished.isPlaying)
        assertEquals(false, finished.hasError)
    }

    @Test
    fun playlistGoesThroughPauseToNextZikr() = runBlocking {
        onMain {
            scope.launch { player.run() }
            // Пауза 1 с — элемент-тишина между зикрами: после него — второй зикр.
            player.playAll("azkar.morning", listOf(shortTrack(1), shortTrack(2)), rate = 1.5f, pauseSeconds = 1.0)
        }
        withTimeout(TIMEOUT_MILLIS) { player.state.first { it.zikrIndex == 1 && it.isPlaying } }
        val finished = withTimeout(TIMEOUT_MILLIS) { player.state.first { it.isFinished } }
        assertEquals(1, finished.zikrIndex)
        assertEquals(false, finished.hasError)
    }
}
