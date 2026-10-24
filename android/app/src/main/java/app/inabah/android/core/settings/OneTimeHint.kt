package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Подсказка-пузырь, которую показывают один раз (как подсказки «Учимся читать», решение пользователя 2026-10-24):
 * у кнопки «Къибла» на главной «Азкаров» ([QIBLA_ENTRY]) и у ⓘ «Как повысить точность» на экране «Къибла»
 * ([QIBLA_HELP]). Отмечается, когда подсказку показали или нажали то, на что она указывает.
 */
class OneTimeHint(private val storage: PreferencesStorage, key: String) {
    private val key = booleanPreferencesKey(key)

    val seen: Boolean get() = storage.snapshot[key] ?: false

    fun markSeen() {
        if (seen) return
        storage.edit { it[key] = true }
    }

    companion object {
        const val QIBLA_ENTRY = "azkar.qiblaHint.seen"
        const val QIBLA_HELP = "qibla.helpHint.seen"
    }
}
