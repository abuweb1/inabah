package app.inabah.android.core.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.ZikrId
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Прогресс «Учимся читать» (раздел «Махрадж», 2026-10-15):
 * - урок шага пройден - даны ответы на все вопросы проверки (правильность не важна) или нажато «Готово»
 *   у урока без вопросов; ключ `reading.step.<N>.done` = `true`;
 * - «Читаем слова» - сколько раз прочитано каждое слово шага: `reading.words.<N>` = `"10,10,3,0"`;
 * - итог «Читаем зикр» (2026-10-17) - зикр по разделу и номеру в «Азкарах» (`morning.6`, не по месту в шаге:
 *   отметки не переходят к другому зикру, если зикры итога переставят): прочитанные слова
 *   `reading.zikr.morning.6.words` = `"0,1,4"` (каждое - один раз) и «прочитал целиком» `reading.zikr.morning.6.read` = `true`.
 * - день, когда последний раз пройден шаг (напоминание пройти урок, 2026-10-20): `reading.stepFinishedDay` = `"2026-10-20"`.
 * Пройден ли шаг целиком (урок и все слова) - решает `completedSteps` (feature/makharij).
 * [today] - сегодняшняя дата в поясе устройства (в тестах подменяется).
 */
class ReadingProgress(
    private val storage: PreferencesStorage,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val _lessonsDone = MutableStateFlow(readLessons(storage.snapshot))
    private val _wordCounts = MutableStateFlow(readWords(storage.snapshot))
    private val _unlockSeen = MutableStateFlow(storage.snapshot[UNLOCK_SEEN_KEY] ?: 1)
    private val _zikrs = MutableStateFlow(readZikrs(storage.snapshot))

    /** Номера шагов с пройденным уроком. */
    val lessonsDone: StateFlow<Set<Int>> = _lessonsDone.asStateFlow()

    /** Шаг → сколько раз прочитано каждое слово (по порядку слов шага); нет шага - ничего не читали. */
    val wordCounts: StateFlow<Map<Int, List<Int>>> = _wordCounts.asStateFlow()

    /** Зикр итога → прочитанные слова и прочитан ли целиком; нет зикра - ничего не читали. */
    val zikrs: StateFlow<Map<ZikrId, ZikrProgress>> = _zikrs.asStateFlow()

    /** День, когда последний раз пройден шаг (впервые или снова); не проходили - `null`. */
    val stepFinishedDay: LocalDate?
        get() = storage.snapshot[STEP_FINISHED_DAY_KEY]?.let { value ->
            try {
                LocalDate.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }

    /** Шаг пройден сейчас (окно «Шаг пройден»): сегодня напоминание пройти урок больше не нужно. */
    fun markStepFinished() {
        val day = today().toString()
        if (storage.snapshot[STEP_FINISHED_DAY_KEY] == day) return
        storage.edit { it[STEP_FINISHED_DAY_KEY] = day }
    }

    /** Последний открытый шаг, открытие которого уже показано (замок «отщёлкнулся»); по умолчанию 1. */
    val unlockSeen: StateFlow<Int> = _unlockSeen.asStateFlow()

    fun markUnlockSeen(step: Int) {
        if (step <= _unlockSeen.value) return
        _unlockSeen.value = step
        storage.edit { it[UNLOCK_SEEN_KEY] = step }
    }

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

    /** Прочитал слово [word] зикра [zikr]; повторное касание не считается. */
    fun readZikrWord(zikr: ZikrId, word: Int) {
        val current = _zikrs.value[zikr] ?: ZikrProgress()
        if (word < 0 || word in current.words) return
        val updated = current.copy(words = current.words + word)
        _zikrs.value += zikr to updated
        storage.edit { it[zikrWordsKey(zikr)] = updated.words.sorted().joinToString(",") }
    }

    /** Прочитал зикр [zikr] целиком. */
    fun markZikrRead(zikr: ZikrId) {
        val current = _zikrs.value[zikr] ?: ZikrProgress()
        if (current.isRead) return
        _zikrs.value += zikr to current.copy(isRead = true)
        storage.edit { it[zikrReadKey(zikr)] = true }
    }

    /**
     * Сбросить шаг [step]: урок, прочитанные слова и зикры [zikrs] (зикры итога - у итога). Шаги после него
     * снова закрыты, поэтому и «отщёлкнутый» замок - не дальше [step]: при новом открытии он отщёлкнется снова.
     */
    fun resetStep(step: Int, zikrs: Collection<ZikrId> = emptyList()) {
        val stepZikrs = zikrs.filter { it in _zikrs.value }
        if (step !in _lessonsDone.value && step !in _wordCounts.value && stepZikrs.isEmpty()) return
        _lessonsDone.value -= step
        _wordCounts.value -= step
        _zikrs.value -= stepZikrs.toSet()
        val seen = _unlockSeen.value.coerceAtMost(step)
        _unlockSeen.value = seen
        storage.edit { preferences ->
            preferences.remove(lessonKey(step))
            preferences.remove(wordsKey(step))
            stepZikrs.forEach { zikr ->
                preferences.remove(zikrWordsKey(zikr))
                preferences.remove(zikrReadKey(zikr))
            }
            preferences[UNLOCK_SEEN_KEY] = seen
        }
    }

    /** Сбросить весь прогресс - все ключи `reading.step.…`, `reading.words.…`, `reading.zikr.…` и день пройденного шага. */
    fun reset() {
        val keys = storage.snapshot.asMap().keys.filter { isProgressKey(it.name) }
        if (_lessonsDone.value.isEmpty() && _wordCounts.value.isEmpty() && _zikrs.value.isEmpty() && keys.isEmpty()) return
        _lessonsDone.value = emptySet()
        _wordCounts.value = emptyMap()
        _zikrs.value = emptyMap()
        _unlockSeen.value = 1
        storage.edit { preferences ->
            preferences.asMap().keys
                .filter { isProgressKey(it.name) }
                .forEach { preferences.remove(it) }
            preferences.remove(UNLOCK_SEEN_KEY)
        }
    }

    companion object {
        private val LessonPattern = Regex("""^reading\.step\.(\d+)\.done$""")
        private val WordsPattern = Regex("""^reading\.words\.(\d+)$""")
        private val ZikrWordsPattern = Regex("""^reading\.zikr\.(\w+)\.(\d+)\.words$""")
        private val ZikrReadPattern = Regex("""^reading\.zikr\.(\w+)\.(\d+)\.read$""")
        private val UNLOCK_SEEN_KEY = intPreferencesKey("reading.unlockSeen")
        private val STEP_FINISHED_DAY_KEY = stringPreferencesKey("reading.stepFinishedDay")

        // Зикры - любой ключ `reading.zikr.…`: и ключи по номеру в шаге из проверочных сборок до 2026-10-17.
        private fun isProgressKey(name: String) = LessonPattern.matches(name) || WordsPattern.matches(name) ||
            name.startsWith(ZIKR_KEY_PREFIX) || name == STEP_FINISHED_DAY_KEY.name
        private const val ZIKR_KEY_PREFIX = "reading.zikr."

        fun lessonKey(step: Int) = booleanPreferencesKey("reading.step.$step.done")
        fun wordsKey(step: Int) = stringPreferencesKey("reading.words.$step")
        fun zikrWordsKey(zikr: ZikrId) = stringPreferencesKey("reading.zikr.${zikr.section.key}.${zikr.number}.words")
        fun zikrReadKey(zikr: ZikrId) = booleanPreferencesKey("reading.zikr.${zikr.section.key}.${zikr.number}.read")

        /** Зикр из ключа: неизвестный раздел или номер < 1 - null. */
        private fun zikrOf(pattern: Regex, name: String): ZikrId? {
            val (section, number) = pattern.matchEntire(name)?.destructured ?: return null
            return ZikrId(
                AzkarSection.entries.firstOrNull { it.key == section } ?: return null,
                number.toIntOrNull()?.takeIf { it >= 1 } ?: return null,
            )
        }

        /** Испорченный номер слова - пропустить; «прочитан» - только `true`. */
        private fun readZikrs(preferences: Preferences): Map<ZikrId, ZikrProgress> {
            val result = mutableMapOf<ZikrId, ZikrProgress>()
            for ((key, value) in preferences.asMap()) {
                zikrOf(ZikrWordsPattern, key.name)?.let { zikr ->
                    val words = (value as? String).orEmpty().split(',').mapNotNullTo(mutableSetOf()) { it.trim().toIntOrNull()?.takeIf { n -> n >= 0 } }
                    result[zikr] = (result[zikr] ?: ZikrProgress()).copy(words = words)
                }
                zikrOf(ZikrReadPattern, key.name)?.let { zikr ->
                    if (value == true) result[zikr] = (result[zikr] ?: ZikrProgress()).copy(isRead = true)
                }
            }
            return result
        }

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

/** Зикр итога «Читаем зикр»: номера прочитанных слов и «прочитал целиком». */
data class ZikrProgress(val words: Set<Int> = emptySet(), val isRead: Boolean = false)
