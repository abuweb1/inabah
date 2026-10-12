package app.inabah.android.feature.hadith

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Кавычки у слова сноски: только если оно есть в переводе (решение пользователя 2026-10-10). */
class NoteQuoteTest {
    private val translation = "Аллахӏ сказал: “Потомки Адама ругают время, а Я — время. Он предписал Себе милость”."

    @Test
    fun `Слово из перевода — в кавычках, без учёта регистра, «ё» и знаков`() {
        assertTrue(isQuotedTerm("А Я — время", translation))
        assertTrue(isQuotedTerm("он предписал себе", translation))
        assertTrue(isQuotedTerm("Потомки Адама ругают время", translation))
    }

    @Test
    fun `Пояснение ко всему хадису и часть слова — без кавычек`() {
        assertFalse(isQuotedTerm("Различия передач", translation))
        assertFalse(isQuotedTerm("Потом", translation), "часть слова — не цитата")
        assertFalse(isQuotedTerm("—", translation))
    }
}
