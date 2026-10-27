package app.inabah.android.core.settings

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Флаг «один раз» в хранилище (2026-10-24): подсказки-пузыри как в «Учимся читать» - у кнопки «Къибла» на главной
 * «Азкаров» ([QIBLA_ENTRY_HINT]) и у ⓘ «Как повысить точность» ([QIBLA_HELP_HINT]); «доступ к местоположению уже
 * спрашивали и пользователь ответил» ([QIBLA_LOCATION_ASKED]) - без него отказ «больше не спрашивать» не отличить
 * от «ещё не спрашивали» (аудит 2026-10-24). Местоположения не хранит.
 */
class OnceFlag(private val storage: PreferencesStorage, key: String) {
    private val key = booleanPreferencesKey(key)

    val isSet: Boolean get() = storage.snapshot[key] ?: false

    fun set() {
        if (isSet) return
        storage.edit { it[key] = true }
    }

    companion object {
        const val QIBLA_ENTRY_HINT = "azkar.qiblaHint.seen"
        const val QIBLA_HELP_HINT = "qibla.helpHint.seen"
        const val QIBLA_LOCATION_ASKED = "qibla.location.asked"
    }
}
