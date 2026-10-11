package app.inabah.android.feature.azkar

import app.inabah.android.core.designsystem.components.arabicPreview
import kotlin.test.assertEquals
import org.junit.Test

/** Подписи экрана азкаров: свёрнутая строка и процент шапки. */
class AzkarPresentationTest {
    @Test
    fun `Свёрнутая строка — первые 8 слов и многоточие`() {
        val arabic = "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ وَرِضَا نَفْسِهِ وَزِنَةَ عَرْشِهِ وَمِدَادَ كَلِمَاتِهِ"

        assertEquals("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ وَرِضَا نَفْسِهِ وَزِنَةَ…", arabicPreview(arabic))
    }

    @Test
    fun `Текст до 8 слов — целиком, без многоточия`() {
        val eight = "١ ٢ ٣ ٤ ٥ ٦ ٧ ٨"

        assertEquals(eight, arabicPreview(eight))
        assertEquals("سُبْحَانَ اللَّهِ", arabicPreview("سُبْحَانَ اللَّهِ"))
    }

    @Test
    fun `Слова разделяют любые пробелы и переводы строк`() {
        assertEquals("a b c…", arabicPreview("a  b\nc d", words = 3))
    }

    @Test
    fun `Процент шапки — половина вверх, как в прототипе`() {
        assertEquals(13, headerPercent(SectionProgress(completed = 2, total = 16)))
        assertEquals(6, headerPercent(SectionProgress(completed = 1, total = 16)))
        assertEquals(50, headerPercent(SectionProgress(completed = 1, total = 2)))
        assertEquals(100, headerPercent(SectionProgress(completed = 16, total = 16)))
        assertEquals(0, headerPercent(SectionProgress(completed = 0, total = 0)))
    }
}
