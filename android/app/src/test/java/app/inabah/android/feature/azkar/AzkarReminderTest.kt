package app.inabah.android.feature.azkar

import kotlin.test.assertEquals
import org.junit.Test

/** «Сделать напоминание»: текст как рассылка пользователя — блоки, слова Абу ад-Дарды по строкам, черта, ссылка. */
class AzkarReminderTest {
    @Test
    fun `Напоминание — заголовок, призыв, слова Абу ад-Дарды, время чтения, ссылка`() {
        val text = azkarReminderText(
            title = "‼️ Утренние азкары ‼️",
            call = "Прочитайте утренние азкары.",
            quoteIntro = "Абу ад-Дарда сказал:",
            quoteTranslation = "«Перевод».",
            quoteSource = "Ахмад в «аз-Зухд», 726",
            bestTime = "❗ С Фаджра до восхода солнца.",
            storeLinks = "https://apps.apple.com/app/id6819638881",
        )

        assertEquals(
            "‼️ Утренние азкары ‼️\n\n" +
                "Прочитайте утренние азкары.\n\n" +
                "Абу ад-Дарда сказал:\n" +
                "إِنَّ الَّذِينَ أَلْسِنَتُهُمْ رَطْبَةٌ بِذِكْرِ اللَّهِ يَدْخُلُ الْجَنَّةَ وَهُوَ يَضْحَكُ\n" +
                "«Перевод».\n" +
                "Ахмад в «аз-Зухд», 726\n\n" +
                "______________________\n❗ С Фаджра до восхода солнца.\n\n" +
                "https://apps.apple.com/app/id6819638881",
            text,
        )
    }

    @Test
    fun `Без ссылок на приложение — текст заканчивается временем чтения`() {
        val text = azkarReminderText("Т", "П", "А", "П", "И", "Время", storeLinks = null)

        assertEquals(true, text.endsWith("______________________\nВремя"))
    }
}
