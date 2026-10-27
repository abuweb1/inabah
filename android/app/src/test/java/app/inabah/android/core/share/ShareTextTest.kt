package app.inabah.android.core.share

import app.inabah.android.core.content.ContentLanguage
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.content.model.HadithNote
import app.inabah.android.core.content.model.HadithTranslation
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.content.model.ZikrTranslation
import app.inabah.android.feature.azkar.zikrShareText
import app.inabah.android.feature.hadith.hadithNotesBlock
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
        translation = translation,
    )

    private fun hadith(translation: HadithTranslation?) = Hadith(
        id = HadithId(HadithCollection.Nawawi, 2),
        arabic = "عَنْ عُمَرَ\nقَالَ: بَيْنَمَا نَحْنُ",
        translation = translation,
    )

    private val links = "https://apps.apple.com/app/id6819638881"

    @Test
    fun `Зикр — раздел, арабский, «N раз», транскрипция, перевод, источник, ссылки на приложение`() {
        val text = zikrShareText(
            zikr(ZikrTranslation(ru, "Пречист Аллах и хвала Ему.", "Субхана-Ллахи ва би-хамди-хи.", "Муслим")),
            sectionTitle = "Утренние азкары",
            repetitions = "100 раз",
            narratorLine = null,
            storeLinks = links,
        )

        assertEquals(
            "Утренние азкары\n\nسُبْحَانَ اللَّهِ وَ بِحَمْدِهِ\n\n100 раз\n\n" +
                "Субхана-Ллахи ва би-хамди-хи.\n\nПречист Аллах и хвала Ему.\n\nМуслим\n\n$links",
            text,
        )
    }

    @Test
    fun `Зикр без перевода — раздел, арабский и «N раз», без пустых строк`() {
        val text = zikrShareText(
            zikr(translation = null), sectionTitle = "Утренние азкары", repetitions = "100 раз", narratorLine = null, storeLinks = null,
        )

        assertEquals("Утренние азкары\n\nسُبْحَانَ اللَّهِ وَ بِحَمْدِهِ\n\n100 раз", text)
    }

    @Test
    fun `Зикр с вступлением — как есть, без «Передал», после транскрипции, сразу перед переводом`() {
        val text = zikrShareText(
            zikr(ZikrTranslation(ru, "Перевод", "Транскрипция", source = null, narrator = "Передаётся от Абу Хурайры")),
            sectionTitle = "Утренние азкары", repetitions = "1 раз", narratorLine = "Передаётся от Абу Хурайры", storeLinks = null,
        )

        assertEquals(
            "Утренние азкары\n\nسُبْحَانَ اللَّهِ وَ بِحَمْدِهِ\n\n1 раз\n\nТранскрипция\n\nПередаётся от Абу Хурайры\n\nПеревод",
            text,
        )
    }

    @Test
    fun `Ссылки на приложение — две под подписями платформ через пустую строку, одна — без подписи`() {
        assertEquals(
            "Для iPhone\n${StoreLinks.APP_STORE}\n\nДля Android\n${StoreLinks.GOOGLE_PLAY}",
            storeLinksBlock("Для iPhone", "Для Android"),
        )
        assertEquals("https://g", storeLinksBlock("Для iPhone", "Для Android", appStore = null, googlePlay = "https://g"))
        assertEquals("https://a", storeLinksBlock("Для iPhone", "Для Android", appStore = "https://a", googlePlay = null))
    }

    @Test
    fun `Хадис — заголовок, арабский одним абзацем, вступление без «Передал», перевод, источник`() {
        val intro = "Передаётся от Умара ибн аль-Хаттаба, да будет доволен им Аллахӏ, что он сказал:"
        val text = hadithShareText(
            hadith(HadithTranslation(ru, intro, "«Приход Джибриля…»", "Муслим (№ 8)")),
            header = "40 хадисов ан-Навави · Хадис 2",
            narratorLine = intro,
            sourceLine = "Приводится: Муслим (№ 8)",
            notesBlock = null,
            storeLinks = links,
        )

        assertEquals(
            "40 хадисов ан-Навави · Хадис 2\n\nعَنْ عُمَرَ قَالَ: بَيْنَمَا نَحْنُ\n\n$intro\n\n" +
                "«Приход Джибриля…»\n\nПриводится: Муслим (№ 8)\n\n$links",
            text,
        )
    }

    @Test
    fun `Хадис без передатчика, источника и перевода — только то, что есть`() {
        val withoutNotes = hadithShareText(
            hadith(HadithTranslation(ru, narrator = null, text = "Перевод", source = null)),
            header = "Хадис 2", narratorLine = null, sourceLine = null, notesBlock = null, storeLinks = null,
        )
        val withoutTranslation = hadithShareText(
            hadith(null), header = "Хадис 2", narratorLine = null, sourceLine = null, notesBlock = null, storeLinks = null,
        )

        assertEquals("Хадис 2\n\nعَنْ عُمَرَ قَالَ: بَيْنَمَا نَحْنُ\n\nПеревод", withoutNotes)
        assertEquals("Хадис 2\n\nعَنْ عُمَرَ قَالَ: بَيْنَمَا نَحْنُ", withoutTranslation)
    }

    @Test
    // Решения пользователя 2026-10-11 (TASKS §12, пример): «Комментарии учёных:», пустая строка, сноски через
    // пустую строку — слово и пояснение на двух строках; слово из перевода — в кавычках, общее пояснение — без.
    fun `Хадис со сносками — «Комментарии учёных» после источника и перед ссылками`() {
        val notes = listOf(HadithNote("иман", "вера"), HadithNote("Различия передач", "у Муслима иначе"))
        val translation = "Иман — это вера в Аллаха."
        val block = hadithNotesBlock(notes, "Комментарии учёных:", translation) { "«$it»" }
        val text = hadithShareText(
            hadith(HadithTranslation(ru, narrator = null, text = translation, source = "Муслим", notes = notes)),
            header = "Хадис 1", narratorLine = null, sourceLine = "Приводится: Муслим", notesBlock = block, storeLinks = links,
        )

        assertEquals(
            "Хадис 1\n\nعَنْ عُمَرَ قَالَ: بَيْنَمَا نَحْنُ\n\n$translation\n\nПриводится: Муслим\n\n" +
                "Комментарии учёных:\n\n«иман»\nвера\n\nРазличия передач\nу Муслима иначе\n\n$links",
            text,
        )
        assertEquals(null, hadithNotesBlock(emptyList(), "Комментарии учёных:", translation) { it }, "без сносок — блока нет")
    }

    @Test
    fun `Пустые и пробельные блоки пропускаются, края блоков обрезаются`() {
        assertEquals("а\n\nб", shareBlocks(null, "  а ", "", "   ", "б\n"))
    }
}
