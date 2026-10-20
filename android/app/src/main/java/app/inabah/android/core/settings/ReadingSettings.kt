package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кегль арабского текста (iOS `ReadingSettings`): 14…40, шаг 2, по умолчанию 21; «Сворачивать арабский
 * текст» на экране хадиса (решение пользователя 2026-10-11) — по умолчанию выключено. «Скрыть
 * транскрипцию» в «Учимся читать» (кнопка-глаз, решение пользователя 2026-10-15) — по умолчанию видна;
 * «Открыть все шаги» (настройки «Махраджа», решение пользователя 2026-10-19) - по умолчанию выключено.
 */
class ReadingSettings(private val storage: PreferencesStorage) {
    private val _arabicFontSize = MutableStateFlow(
        (storage.snapshot[KEY] ?: DEFAULT_SIZE).coerceIn(MIN_SIZE, MAX_SIZE),
    )
    val arabicFontSize: StateFlow<Double> = _arabicFontSize.asStateFlow()

    private val _hadithArabicCollapsed = MutableStateFlow(storage.snapshot[COLLAPSE_KEY] ?: false)

    /** Включено — арабский текст каждого хадиса открывается свёрнутым в строку «ХАДИС N + начало текста». */
    val hadithArabicCollapsed: StateFlow<Boolean> = _hadithArabicCollapsed.asStateFlow()

    fun setHadithArabicCollapsed(collapsed: Boolean) {
        if (collapsed == _hadithArabicCollapsed.value) return
        _hadithArabicCollapsed.value = collapsed
        storage.edit { it[COLLAPSE_KEY] = collapsed }
    }

    private val _transliterationHidden = MutableStateFlow(storage.snapshot[TRANSLIT_KEY] ?: false)

    /**
     * Включено — транскрипция в уроках и листе буквы «Учимся читать» прозрачна (место остаётся). В проверке
     * её нет: варианты ответа и есть транскрипция. TalkBack читает её и скрытой — иначе незрячему её не узнать.
     */
    val transliterationHidden: StateFlow<Boolean> = _transliterationHidden.asStateFlow()

    fun setTransliterationHidden(hidden: Boolean) {
        if (hidden == _transliterationHidden.value) return
        _transliterationHidden.value = hidden
        storage.edit { it[TRANSLIT_KEY] = hidden }
    }

    private val _allStepsOpen = MutableStateFlow(storage.snapshot[ALL_STEPS_OPEN_KEY] ?: false)

    /** Включено - шаги «Учимся читать» без замков: начать можно с любого. Отметки считаются как обычно. */
    val allStepsOpen: StateFlow<Boolean> = _allStepsOpen.asStateFlow()

    fun setAllStepsOpen(open: Boolean) {
        if (open == _allStepsOpen.value) return
        _allStepsOpen.value = open
        storage.edit { it[ALL_STEPS_OPEN_KEY] = open }
    }

    /**
     * Подсказка экрана «Учимся читать» уже показана (живые ячейки, решение пользователя 2026-10-20): волна и подсказка
     * у первой ячейки - только при первом открытии экрана [key] (`lesson.3`, `words`). Читается при открытии экрана.
     */
    fun introSeen(key: String): Boolean = storage.snapshot[introKey(key)] ?: false

    fun markIntroSeen(key: String) {
        if (introSeen(key)) return
        storage.edit { it[introKey(key)] = true }
    }

    private val _zoomLearned = MutableStateFlow(storage.snapshot[ZOOM_LEARNED_KEY] ?: false)

    /** Слово уже увеличивали долгим нажатием - плитки больше не «зовут» (покачивание при тишине). */
    val zoomLearned: StateFlow<Boolean> = _zoomLearned.asStateFlow()

    fun markZoomLearned() {
        if (_zoomLearned.value) return
        _zoomLearned.value = true
        storage.edit { it[ZOOM_LEARNED_KEY] = true }
    }

    /** «Показать подсказки снова» (настройки «Махраджа»): забыть показанные подсказки и увеличение. Прогресс не трогает. */
    fun resetIntros() {
        _zoomLearned.value = false
        storage.edit { preferences ->
            preferences.asMap().keys.filter { it.name.startsWith(INTRO_PREFIX) }.forEach { preferences.remove(it) }
            preferences.remove(ZOOM_LEARNED_KEY)
        }
    }

    fun increaseArabicFontSize() = setArabicFontSize(_arabicFontSize.value + STEP)

    fun decreaseArabicFontSize() = setArabicFontSize(_arabicFontSize.value - STEP)

    private fun setArabicFontSize(size: Double) {
        val clamped = size.coerceIn(MIN_SIZE, MAX_SIZE)
        if (clamped == _arabicFontSize.value) return
        _arabicFontSize.value = clamped
        storage.edit { it[KEY] = clamped }
    }

    companion object {
        const val DEFAULT_SIZE = 21.0
        const val MIN_SIZE = 14.0
        const val MAX_SIZE = 40.0
        const val STEP = 2.0

        /** «А+» активна — кегль ещё не на верхней границе. Одно правило для всех экранов. */
        fun canIncrease(size: Double): Boolean = size < MAX_SIZE

        /** «А−» активна — кегль ещё не на нижней границе. */
        fun canDecrease(size: Double): Boolean = size > MIN_SIZE

        private val KEY = doublePreferencesKey("reading.arabicFontSize")
        private val COLLAPSE_KEY = booleanPreferencesKey("reading.hadithArabicCollapsed")
        private val TRANSLIT_KEY = booleanPreferencesKey("reading.transliterationHidden")
        private val ALL_STEPS_OPEN_KEY = booleanPreferencesKey("reading.allStepsOpen")
        private const val INTRO_PREFIX = "reading.intro."
        private fun introKey(key: String) = booleanPreferencesKey(INTRO_PREFIX + key)
        private val ZOOM_LEARNED_KEY = booleanPreferencesKey("reading.zoomLearned")
    }
}
