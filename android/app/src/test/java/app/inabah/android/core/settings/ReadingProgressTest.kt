package app.inabah.android.core.settings

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** «Учимся читать»: пройденные уроки, счёт слов и «Скрыть транскрипцию». */
class ReadingProgressTest {
    @Test
    fun `Пройденный урок переживает перезапуск в ключе reading_step_N_done`() = TestStorage.run { storage ->
        val progress = ReadingProgress(storage.storage)

        progress.markLessonDone(2)
        progress.markLessonDone(2)
        progress.markLessonDone(1)

        assertEquals(setOf(1, 2), progress.lessonsDone.value)
        assertEquals(true, storage.persisted()[ReadingProgress.lessonKey(2)])
        assertEquals(setOf(1, 2), ReadingProgress(storage.storage).lessonsDone.value, "после перезапуска")
    }

    @Test
    fun `Слово считается до repeat раз и переживает перезапуск`() = TestStorage.run { storage ->
        val progress = ReadingProgress(storage.storage)

        repeat(12) { progress.readWord(step = 2, index = 1, total = 3, repeat = 10) }
        progress.readWord(step = 2, index = 0, total = 3, repeat = 10)
        progress.readWord(step = 2, index = 7, total = 3, repeat = 10)

        assertEquals(listOf(1, 10, 0), progress.wordCounts.value[2])
        assertEquals("1,10,0", storage.persisted()[ReadingProgress.wordsKey(2)])
        assertEquals(listOf(1, 10, 0), ReadingProgress(storage.storage).wordCounts.value[2], "после перезапуска")
    }

    @Test
    fun `Сброс удаляет уроки и слова`() = TestStorage.run { storage ->
        val progress = ReadingProgress(storage.storage)
        progress.markLessonDone(2)
        progress.readWord(2, 0, 2, 10)

        progress.reset()

        assertTrue(progress.lessonsDone.value.isEmpty())
        assertTrue(progress.wordCounts.value.isEmpty())
        assertNull(storage.persisted()[ReadingProgress.lessonKey(2)])
        assertNull(storage.persisted()[ReadingProgress.wordsKey(2)])
    }

    @Test
    fun `«Скрыть транскрипцию» — по умолчанию видна, выбор сохраняется`() = TestStorage.run { storage ->
        val settings = ReadingSettings(storage.storage)
        assertFalse(settings.transliterationHidden.value)

        settings.setTransliterationHidden(true)

        assertTrue(ReadingSettings(storage.storage).transliterationHidden.value, "после перезапуска")
    }
}
