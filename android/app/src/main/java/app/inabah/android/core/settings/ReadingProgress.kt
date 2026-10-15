package app.inabah.android.core.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Прогресс «Учимся читать» (раздел «Махрадж», 2026-10-15):
 * - урок шага пройден - даны ответы на все вопросы проверки (правильность не важна) или нажато «Готово»
 *   у урока без вопросов; ключ `reading.step.<N>.done` = `true`;
 * - «Читаем слова» - сколько раз прочитано каждое слово шага: `reading.words.<N>` = `"10,10,3,0"`.
 * Пройден ли шаг целиком (урок и все слова) - решает `completedSteps` (feature/makharij).
 */
class ReadingProgress(private val storage: PreferencesStorage) {
    private val _lessonsDone = MutableStateFlow(readLessons(storage.snapshot))
    private val _wordCounts = MutableStateFlow(readWords(storage.snapshot))

    /** Номера шагов с пройденным уроком. */
    val lessonsDone: StateFlow<Set<Int>> = _lessonsDone.asStateFlow()

    /** Шаг → сколько раз прочитано каждое слово (по порядку слов шага); нет шага - ничего не читали. */
    val wordCounts: StateFlow<Map<Int, List<Int>>> = _wordCounts.asStateFlow()

    fun markLessonDone(step: Int) {
        if (step in _lessonsDone.value) return
        _lessonsDone.value += step
        storage.edit { it[lessonKey(step)] = true }
    }

    /** Прочитал слово [index] шага [step] ещё раз; больше [repeat] не считается. [total] - слов в шаге. */
    fun readWord(step: Int, index: Int, total: Int, repeat: Int) {
        val counts = (_wordCounts.value[step].orEmpty() + List(total) { 0 }).take(total)
        if (index !in counts.indices || counts[index] >= repeat) return
        val updated = counts.toMutableList().also { it[index] += 1 }
        _wordCounts.value += step to updated
        storage.edit { it[wordsKey(step)] = updated.joinToString(",") }
    }

    /** Сбросить весь прогресс - все ключи `reading.step.…` и `reading.words.…`. */
    fun reset() {
        val keys = storage.snapshot.asMap().keys.filter { LessonPattern.matches(it.name) || WordsPattern.matches(it.name) }
        if (_lessonsDone.value.isEmpty() && _wordCounts.value.isEmpty() && keys.isEmpty()) return
        _lessonsDone.value = emptySet()
        _wordCounts.value = emptyMap()
        storage.edit { preferences ->
            preferences.asMap().keys
                .filter { LessonPattern.matches(it.name) || WordsPattern.matches(it.name) }
                .forEach { preferences.remove(it) }
        }
    }

    companion object {
        private val LessonPattern = Regex("""^reading\.step\.(\d+)\.done$""")
        private val WordsPattern = Regex("""^reading\.words\.(\d+)$""")

        fun lessonKey(step: Int) = booleanPreferencesKey("reading.step.$step.done")
        fun wordsKey(step: Int) = stringPreferencesKey("reading.words.$step")

        /** Значение не `true`, номер < 1 - пропустить. */
        private fun readLessons(preferences: Preferences): Set<Int> =
            preferences.asMap().mapNotNullTo(mutableSetOf()) { (key, value) ->
                if (value != true) return@mapNotNullTo null
                LessonPattern.matchEntire(key.name)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 1 }
            }

        /** Испорченное число - 0; номер < 1 или не строка - пропустить. */
        private fun readWords(preferences: Preferences): Map<Int, List<Int>> =
            preferences.asMap().mapNotNull { (key, value) ->
                val step = WordsPattern.matchEntire(key.name)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it >= 1 }
                    ?: return@mapNotNull null
                val text = value as? String ?: return@mapNotNull null
                step to text.split(',').filter { it.isNotBlank() }.map { it.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0 }
            }.toMap()
    }
}
