package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.ZikrId
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** «Учимся читать»: пройденные уроки, счёт слов и «Скрыть транскрипцию». */
class ReadingProgressTest {
    private companion object {
        val MORNING_6 = ZikrId(AzkarSection.Morning, 6)
        val EVENING_1 = ZikrId(AzkarSection.Evening, 1)
    }

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
    fun `Зикр итога - по разделу и номеру, слова по одному разу, «прочитал целиком», переживает перезапуск`() = TestStorage.run { storage ->
        val progress = ReadingProgress(storage.storage)

        progress.readZikrWord(zikr = MORNING_6, word = 2)
        progress.readZikrWord(zikr = MORNING_6, word = 2)
        progress.readZikrWord(zikr = MORNING_6, word = 0)
        progress.readZikrWord(zikr = MORNING_6, word = -1)
        progress.markZikrRead(MORNING_6)

        val expected = ZikrProgress(words = setOf(0, 2), isRead = true)
        assertEquals(expected, progress.zikrs.value[MORNING_6])
        assertNull(progress.zikrs.value[EVENING_1], "другой зикр не задет")
        assertEquals(expected, ReadingProgress(storage.storage).zikrs.value[MORNING_6], "после перезапуска")
        val persisted = storage.persisted()
        assertEquals("0,2", persisted[stringPreferencesKey("reading.zikr.morning.6.words")])
        assertEquals(true, persisted[booleanPreferencesKey("reading.zikr.morning.6.read")])
    }

    @Test
    fun `Сброс удаляет и прогресс зикров итога`() = TestStorage.run { storage ->
        val progress = ReadingProgress(storage.storage)
        progress.readZikrWord(EVENING_1, 0)
        progress.markZikrRead(EVENING_1)

        progress.reset()

        assertTrue(progress.zikrs.value.isEmpty())
        assertNull(storage.persisted()[ReadingProgress.zikrWordsKey(EVENING_1)])
        assertNull(storage.persisted()[ReadingProgress.zikrReadKey(EVENING_1)])
    }

    @Test
    fun `Ключи зикров по номеру в шаге и неизвестный раздел не читаются, сброс их удаляет`() = TestStorage.run { storage ->
        storage.seed {
            it[stringPreferencesKey("reading.zikr.0.words")] = "0,1"
            it[booleanPreferencesKey("reading.zikr.0.read")] = true
            it[stringPreferencesKey("reading.zikr.night.3.words")] = "0"
            it[stringPreferencesKey("reading.zikr.morning.0.words")] = "0"
        }
        val progress = ReadingProgress(storage.storage)
        assertTrue(progress.zikrs.value.isEmpty())

        progress.reset()

        assertTrue(storage.persisted().asMap().keys.none { it.name.startsWith("reading.zikr.") })
    }

    @Test
    fun `«Скрыть транскрипцию» — по умолчанию видна, выбор сохраняется`() = TestStorage.run { storage ->
        val settings = ReadingSettings(storage.storage)
        assertFalse(settings.transliterationHidden.value)

        settings.setTransliterationHidden(true)

        assertTrue(ReadingSettings(storage.storage).transliterationHidden.value, "после перезапуска")
    }

    @Test
    fun `Сброс шага удаляет только его урок, слова и зикры, замок откатывается к нему`() = TestStorage.run { storage ->
        val progress = ReadingProgress(storage.storage)
        progress.markLessonDone(2)
        progress.readWord(2, 0, 2, 10)
        progress.markLessonDone(3)
        progress.readWord(3, 1, 2, 10)
        progress.markLessonDone(13)
        progress.readZikrWord(MORNING_6, 0)
        progress.markZikrRead(EVENING_1)
        progress.markUnlockSeen(5)

        progress.resetStep(3)
        progress.resetStep(13, listOf(MORNING_6))

        assertEquals(setOf(2), progress.lessonsDone.value)
        assertEquals(setOf(2), progress.wordCounts.value.keys)
        assertEquals(setOf(EVENING_1), progress.zikrs.value.keys, "зикр другого шага не задет")
        assertEquals(3, progress.unlockSeen.value)
        val restarted = ReadingProgress(storage.storage)
        assertEquals(setOf(2), restarted.lessonsDone.value, "после перезапуска")
        assertEquals(setOf(2), restarted.wordCounts.value.keys)
        assertEquals(setOf(EVENING_1), restarted.zikrs.value.keys)
        assertEquals(3, restarted.unlockSeen.value)
        assertNull(storage.persisted()[ReadingProgress.zikrWordsKey(MORNING_6)])
    }

    @Test
    fun `Сброс шага без прогресса ничего не меняет`() = TestStorage.run { storage ->
        val progress = ReadingProgress(storage.storage)
        progress.markUnlockSeen(5)

        progress.resetStep(2, listOf(MORNING_6))

        assertEquals(5, progress.unlockSeen.value)
        assertEquals(5, ReadingProgress(storage.storage).unlockSeen.value)
    }

    @Test
    fun `«Открыть все шаги» - по умолчанию выключено, выбор сохраняется и не сбрасывается с прогрессом`() = TestStorage.run { storage ->
        val settings = ReadingSettings(storage.storage)
        assertFalse(settings.allStepsOpen.value)

        settings.setAllStepsOpen(true)
        ReadingProgress(storage.storage).reset()

        assertTrue(ReadingSettings(storage.storage).allStepsOpen.value, "после перезапуска")
    }

    @Test
    fun `День пройденного шага — сегодняшний, переживает перезапуск и стирается сбросом всего прогресса`() = TestStorage.run { storage ->
        var today = LocalDate.of(2026, 10, 20)
        val progress = ReadingProgress(storage.storage) { today }
        assertNull(progress.stepFinishedDay)

        progress.markStepFinished()
        assertEquals(today, ReadingProgress(storage.restart()).stepFinishedDay, "после перезапуска")

        today = today.plusDays(1)
        val next = ReadingProgress(storage.storage) { today }
        next.markStepFinished()
        assertEquals(today, next.stepFinishedDay)

        next.reset()
        assertNull(ReadingProgress(storage.restart()).stepFinishedDay)
    }

    @Test
    fun `Испорченный день пройденного шага — как не проходили`() = TestStorage.run { storage ->
        storage.seed { it[stringPreferencesKey("reading.stepFinishedDay")] = "вчера" }

        assertNull(ReadingProgress(storage.storage).stepFinishedDay)
    }
}
