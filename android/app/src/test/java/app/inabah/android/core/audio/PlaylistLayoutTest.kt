package app.inabah.android.core.audio

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

internal fun track(number: Int, repeats: Int = 1) = AudioTrack(
    id = "azkar.morning.$number",
    assetFile = "morning_%02d.mp3".format(number),
    title = "Зикр №$number",
    subtitle = "Утренние азкары",
    category = "АЗКАРЫ",
    repeatCount = repeats,
)

/** Развёртка «Прослушать все» в элементы движка (повторы, паузы) и переходы по зикрам. */
class PlaylistLayoutTest {
    private val tracks = listOf(track(1), track(2, repeats = 3), track(3))

    @Test
    fun `Повторы идут подряд, пауза — только между разными зикрами`() {
        val items = expandPlaylist(tracks, pauseSeconds = 1.0, rate = 1f)
        val shape = items.map { if (it is PlaylistItem.Track) "${it.zikrIndex}.${it.repetition}" else "|" }
        assertEquals(listOf("0.1", "|", "1.1", "1.2", "1.3", "|", "2.1"), shape)
    }

    @Test
    fun `Пауза в секундах реального времени — длительность тишины × скорость`() {
        val pause = expandPlaylist(tracks, pauseSeconds = 3.0, rate = 1.5f).filterIsInstance<PlaylistItem.Pause>().first()
        assertEquals(4_500L, pause.durationMs)
    }

    @Test
    fun `Пауза 0 — без элементов тишины`() {
        assertEquals(5, expandPlaylist(tracks, pauseSeconds = 0.0, rate = 1f).size)
    }

    @Test
    fun `repeatCount меньше 1 — как 1`() {
        val items = expandPlaylist(listOf(track(1, repeats = 0), track(2, repeats = -3)), 0.0, 1f)
        assertEquals(2, items.size)
    }

    @Test
    fun `Соседний зикр — его первый повтор, на краях — нет`() {
        val items = expandPlaylist(tracks, pauseSeconds = 1.0, rate = 1f)
        // Со второго повтора второго зикра (элемент 3): назад — к первому зикру, вперёд — к третьему.
        assertEquals(0, items.neighbourZikrItem(3, step = -1))
        assertEquals(6, items.neighbourZikrItem(3, step = 1))
        assertNull(items.neighbourZikrItem(0, step = -1))
        assertNull(items.neighbourZikrItem(6, step = 1))
    }

    @Test
    fun `Пауза относится к следующему зикру, повтор — первый`() {
        val items = expandPlaylist(tracks, pauseSeconds = 1.0, rate = 1f)
        assertEquals(1, items.zikrAt(1))
        assertEquals(1, items.repetitionAt(1))
        assertEquals(3, items.zikrCount)
    }

    @Test
    fun `Служба находит соседние зикры по mediaId`() {
        val ids = expandPlaylist(tracks, pauseSeconds = 1.0, rate = 1f).map { it.mediaId }
        assertEquals(2, neighbourZikrItemByMediaId(ids, current = 1, step = 0))
        assertEquals(6, neighbourZikrItemByMediaId(ids, current = 4, step = 1))
        assertNull(neighbourZikrItemByMediaId(ids, current = 6, step = 1))
        assertNull(neighbourZikrItemByMediaId(listOf("чужой"), current = 0, step = 1))
    }
}
