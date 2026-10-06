package app.inabah.android.core.settings

import androidx.datastore.preferences.core.intPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Время азкаров «с — до» для каждого раздела (iOS `AzkarWindowSettings`): по умолчанию утренние —
 * 5:00–12:00, вечерние — 17:00–02:00. Хранится в минутах от полуночи.
 *
 * Стор на изменения не подписан: новое время он применяет при уходе с экрана настроек
 * (`AzkarStore.reconcile`) — прокрутка колеса времени не стирает прочитанное.
 */
class AzkarWindowSettings(private val storage: PreferencesStorage) {
    private val _windows: MutableStateFlow<Map<AzkarSection, AzkarWindow>>

    init {
        // Время обнуления версии 1.0.0 — у него другой смысл, поэтому оно не переносится.
        if (AzkarSection.entries.any { legacyResetKey(it) in storage.snapshot }) {
            storage.edit { preferences -> AzkarSection.entries.forEach { preferences.remove(legacyResetKey(it)) } }
        }
        _windows = MutableStateFlow(AzkarSection.entries.associateWith(::storedWindow))
    }

    val windows: StateFlow<Map<AzkarSection, AzkarWindow>> = _windows.asStateFlow()

    fun window(section: AzkarSection): AzkarWindow = _windows.value.getValue(section)

    /** Начало, совпадающее с концом, не сохраняется: у окна не было бы длины. */
    fun setStart(time: DayTime, section: AzkarSection) = update(window(section).copy(start = time), section)

    fun setEnd(time: DayTime, section: AzkarSection) = update(window(section).copy(end = time), section)

    private fun update(window: AzkarWindow, section: AzkarSection) {
        if (window.start == window.end || window == window(section)) return
        _windows.update { it + (section to window) }
        storage.edit {
            it[key(section, Edge.Start)] = window.start.minutesSinceMidnight
            it[key(section, Edge.End)] = window.end.minutesSinceMidnight
        }
    }

    /** Нет значения — по умолчанию; начало = концу — окно по умолчанию. */
    private fun storedWindow(section: AzkarSection): AzkarWindow {
        val fallback = defaultWindow(section)
        val start = storage.snapshot[key(section, Edge.Start)]?.let(DayTime::fromMinutes) ?: fallback.start
        val end = storage.snapshot[key(section, Edge.End)]?.let(DayTime::fromMinutes) ?: fallback.end
        return if (start == end) fallback else AzkarWindow(start, end)
    }

    private enum class Edge(val key: String) { Start("start"), End("end") }

    companion object {
        fun defaultWindow(section: AzkarSection): AzkarWindow = when (section) {
            AzkarSection.Morning -> AzkarWindow(DayTime.of(hour = 5, minute = 0), DayTime.of(hour = 12, minute = 0))
            AzkarSection.Evening -> AzkarWindow(DayTime.of(hour = 17, minute = 0), DayTime.of(hour = 2, minute = 0))
        }

        private fun key(section: AzkarSection, edge: Edge) = intPreferencesKey("azkar.window.${section.key}.${edge.key}")

        private fun legacyResetKey(section: AzkarSection) = intPreferencesKey("azkar.reset.${section.key}")
    }
}
