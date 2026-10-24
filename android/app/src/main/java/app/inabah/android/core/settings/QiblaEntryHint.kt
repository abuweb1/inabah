package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Подсказка у кнопки «Къибла» на главной «Азкаров» (решение пользователя 2026-10-24, как подсказки «Учимся читать»):
 * один раз. Отмечается, когда подсказку показали или кнопку нажали. Читается при открытии главной.
 */
class QiblaEntryHint(private val storage: PreferencesStorage) {
    val seen: Boolean get() = storage.snapshot[KEY] ?: false

    fun markSeen() {
        if (seen) return
        storage.edit { it[KEY] = true }
    }

    private companion object {
        val KEY = booleanPreferencesKey("azkar.qiblaHint.seen")
    }
}
