package app.inabah.android.core.share

import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.content.model.HadithTranslation
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.content.model.ZikrTranslation
import app.inabah.android.feature.azkar.zikrShareText
import app.inabah.android.feature.hadith.hadithShareText
import kotlin.test.assertEquals
import org.junit.Test

/** «Поделиться»: текст зикра и хадиса — всё, что на экране, в его порядке. */
class ShareTextTest {
    private val ru = ContentLanguage.of("ru")

    private fun zikr(translation: ZikrTranslation?) = Zikr(
        id = ZikrId(AzkarSection.Morning, 3),
        arabic = "سُبْحَانَ اللَّهِ وَ بِحَمْدِهِ",
        repetitions = 100,
        audioFileName = "morning_03.mp3",
        translation = translation,
    )

    private fun hadith(translation: HadithTranslation?) = Hadith(
        id = HadithId(HadithCollection.Nawawi, 2),
        arabic = "عَنْ عُمَرَ\nقَالَ: بَيْنَمَا نَحْنُ",
        translation = translation,
    )

    @Test
    fun `Зикр — раздел, арабский, «N раз», транскрипция, перевод, источник`() {
        val text = zikrShareText(
            zikr(ZikrTranslation(ru, "Пречист Аллах и хвала Ему.", "Субхана-Ллахи ва би-хамди-хи.", "Муслим")),
            sectionTitle = "Утренние азкары",
            repetitions = "100 раз",
        )

        assertEquals(
            "Утренние азкары\n\nسُبْحَانَ اللَّهِ وَ بِحَمْدِهِ\n\n100 раз\n\n" +
                "Субхана-Ллахи ва би-хамди-хи.\n\nПречист Аллах и хвала Ему.\n\nМуслим",
            text,
        )
    }

    @Test
    fun `Зикр без перевода — раздел, арабский и «N раз», без пустых строк`() {
        val text = zikrShareText(zikr(translation = null), sectionTitle = "Утренние азкары", repetitions = "100 раз")

        assertEquals("Утренние азкары\n\nسُبْحَانَ اللَّهِ وَ بِحَمْدِهِ\n\n100 раз", text)
    }

    @Test
    fun `Хадис — заголовок, арабский одним абзацем, передатчик, перевод, источник`() {
        val text = hadithShareText(
            hadith(HadithTranslation(ru, "Умар ибн аль-Хаттаб", "«Приход Джибриля…»", "Муслим (№ 8)")),
            header = "40 хадисов ан-Навави · Хадис 2",
            narratorLine = "Передал: Умар ибн аль-Хаттаб",
            sourceLine = "Приводится: Муслим (№ 8)",
        )

        assertEquals(
            "40 хадисов ан-Навави · Хадис 2\n\nعَنْ عُمَرَ قَالَ: بَيْنَمَا نَحْنُ\n\nПередал: Умар ибн аль-Хаттаб\n\n" +
                "«Приход Джибриля…»\n\nПриводится: Муслим (№ 8)",
            text,
        )
    }

    @Test
    fun `Хадис без передатчика, источника и перевода — только то, что есть`() {
        val withoutNotes = hadithShareText(
            hadith(HadithTranslation(ru, narrator = null, text = "Перевод", source = null)),
            header = "Хадис 2", narratorLine = null, sourceLine = null,
        )
        val withoutTranslation = hadithShareText(hadith(null), header = "Хадис 2", narratorLine = null, sourceLine = null)

        assertEquals("Хадис 2\n\nعَنْ عُمَرَ قَالَ: بَيْنَمَا نَحْنُ\n\nПеревод", withoutNotes)
        assertEquals("Хадис 2\n\nعَنْ عُمَرَ قَالَ: بَيْنَمَا نَحْنُ", withoutTranslation)
    }

    @Test
    fun `Пустые и пробельные блоки пропускаются, края блоков обрезаются`() {
        assertEquals("а\n\nб", shareBlocks(null, "  а ", "", "   ", "б\n"))
    }
}
