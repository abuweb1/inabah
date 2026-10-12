package app.inabah.android.core.releases

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.settings.PreferencesStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Что делать с окном «Что нового» при запуске. */
enum class WhatsNewDecision {
    /** Показать пункты текущей версии; версия запоминается, когда окно закрыли. */
    Show,

    /** Окна нет, но текущую версию запомнить: новая установка или для версии нет записи. */
    RecordSilently,

    /** Ничего: эта версия уже показана (или приложение откатили на старую). */
    Nothing,
}

/**
 * Правило показа (docs/android/03-domain-logic.md, «Что нового»). [lastShown] — последняя показанная версия
 * (`null` — ключа нет: новая установка или обновление с версии без этой функции, 1.1.0), [isFreshInstall] —
 * приложение установлено, а не обновлено, [hasNotes] — в `versions.json` есть запись для [current].
 */
fun whatsNewDecision(lastShown: AppVersion?, current: AppVersion, isFreshInstall: Boolean, hasNotes: Boolean): WhatsNewDecision =
    when {
        lastShown == null && isFreshInstall -> WhatsNewDecision.RecordSilently
        lastShown != null && current <= lastShown -> WhatsNewDecision.Nothing
        hasNotes -> WhatsNewDecision.Show
        else -> WhatsNewDecision.RecordSilently
    }

/**
 * Окно «Что нового» после обновления: [check] — один раз при запуске, [pending] — что показать (`null` — нечего),
 * [dismiss] — окно закрыли («ОК», касание мимо, «Назад»): версия запоминается только тогда, и если процесс умер
 * раньше, окно покажется при следующем запуске. [currentVersion] — `versionName`; не `X.Y.Z` — окна нет.
 */
class WhatsNew(
    private val storage: PreferencesStorage,
    private val notes: ReleaseNotesSource,
    private val currentVersion: String,
    private val isFreshInstall: Boolean,
) {
    private val _pending = MutableStateFlow<ReleaseNotes?>(null)
    val pending: StateFlow<ReleaseNotes?> = _pending.asStateFlow()

    suspend fun check() {
        val current = AppVersion.parse(currentVersion) ?: return
        val lastShown = AppVersion.parse(storage.snapshot[KEY])
        val currentNotes = notes.notes(current)
        when (whatsNewDecision(lastShown, current, isFreshInstall, hasNotes = currentNotes != null)) {
            WhatsNewDecision.Show -> _pending.value = currentNotes
            WhatsNewDecision.RecordSilently -> record(current)
            WhatsNewDecision.Nothing -> Unit
        }
    }

    fun dismiss() {
        val shown = _pending.value ?: return
        _pending.value = null
        record(shown.version)
    }

    private fun record(version: AppVersion) {
        storage.edit { it[KEY] = version.text }
    }

    companion object {
        /** Последняя показанная версия (`1.1.1`); iOS — тот же ключ в `UserDefaults`. */
        val KEY = stringPreferencesKey("app.whatsNew.lastVersion")
    }
}
