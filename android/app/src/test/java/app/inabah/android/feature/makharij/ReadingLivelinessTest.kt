package app.inabah.android.feature.makharij

import androidx.compose.ui.geometry.Rect
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Живые ячейки «Учимся читать»: порядок волны, видимость ячейки, «зов». */
class ReadingLivelinessTest {
    private val host = Rect(0f, 100f, 400f, 900f)

    @Test
    fun `Волна - по порядку чтения, ряд за рядом сверху вниз, в ряду справа налево`() {
        // Два ряда по три ячейки; второй ряд чуть «неровный» (1 px) - всё равно один ряд.
        val cells = listOf(
            Rect(0f, 100f, 100f, 160f), Rect(150f, 100f, 250f, 160f), Rect(300f, 100f, 400f, 160f),
            Rect(0f, 171f, 100f, 231f), Rect(150f, 170f, 250f, 230f), Rect(300f, 170f, 400f, 230f),
        )

        assertEquals(listOf(2, 1, 0, 5, 4, 3), readingOrder(cells), "правая верхняя - первая, правая второго ряда - четвёртая")
        assertEquals(emptyList(), readingOrder(emptyList()))
    }

    @Test
    fun `В волну и подсказку - только видимые ячейки`() {
        assertTrue(isVisible(Rect(10f, 120f, 90f, 180f), host))
        assertFalse(isVisible(Rect(10f, 1000f, 90f, 1060f), host), "ниже экрана (прокрутка)")
        assertFalse(isVisible(Rect(450f, 120f, 530f, 180f), host), "соседняя страница листания")
        assertFalse(isVisible(Rect.Zero, host), "ещё не размещена")
    }

    @Test
    fun `«Зов» - покачивание с наклоном, начинается и кончается в покое`() {
        assertEquals(0f, wiggleRotation(0f))
        assertEquals(0f, wiggleRotation(1f))
        assertEquals(-4f, wiggleRotation(0.15f))
        assertEquals(4f, wiggleRotation(0.3f))
        assertEquals(1f, wiggleScale(0f))
        assertEquals(1f, wiggleScale(1f))
        assertTrue(wiggleScale(0.3f) > 1f, "в середине плитка чуть больше")
    }
}
