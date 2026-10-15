package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Кегль арабского текста (iOS `ReadingSettings`): 14…40, шаг 2, по умолчанию 21; «Сворачивать арабский
 * текст» на экране хадиса (решение пользователя 2026-10-11) — по умолчанию выключено. «Скрыть
 * транскрипцию» в «Учимся читать» (кнопка-глаз, решение пользователя 2026-10-15) — по умолчанию видна.
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
    }
}
