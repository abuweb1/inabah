package app.inabah.android.core.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HadithStatus {
    None,
    Read,
    Memorized;

    val isRead: Boolean get() = this != None
    val isMemorized: Boolean get() = this == Memorized
}

/** Прогресс сборника: выученные входят в прочитанные. */
data class HadithCollectionProgress(val read: Int, val memorized: Int, val total: Int) {
    val readFraction: Double get() = if (total > 0) read.toDouble() / total else 0.0
}

/**
 * Отметки хадисов (iOS `HadithProgress`): «выучен» ⇒ «прочитан». Хранятся в ключах прототипа
 * с номером хадиса: `h_read_nawawi_1`, `h_mem_nawawi_1` = `"1"`; снятая отметка — ключ удаляется.
 */
class HadithProgress(private val storage: PreferencesStorage) {
    private val _statuses = MutableStateFlow(readStatuses(storage.snapshot))

    /** Только отмеченные хадисы; нет в словаре — [HadithStatus.None]. */
    val statuses: StateFlow<Map<HadithId, HadithStatus>> = _statuses.asStateFlow()

    fun status(id: HadithId): HadithStatus = _statuses.value[id] ?: HadithStatus.None

    /** Прочитан (или выучен) → снять всё; иначе → «прочитан». */
    fun toggleRead(id: HadithId) {
        set(id, if (status(id).isRead) HadithStatus.None else HadithStatus.Read)
    }

    /** Выучен → остаётся «прочитан»; иначе → «выучен». */
    fun toggleMemorized(id: HadithId) {
        set(id, if (status(id).isMemorized) HadithStatus.Read else HadithStatus.Memorized)
    }

    /**
     * Снять все отметки сборника — все его ключи с номером, включая 0 и номера за пределами сборника;
     * другие сборники и посторонние ключи не трогать.
     */
    fun reset(collection: HadithCollection) {
        _statuses.value = _statuses.value.filterKeys { it.collection != collection }
        storage.edit { preferences ->
            preferences.asMap().keys
                .filter { key -> parseKey(key.name)?.collection == collection }
                .forEach { preferences.remove(it) }
        }
    }

    /** Учитываются только номера `1…total`. */
    fun progress(collection: HadithCollection, total: Int): HadithCollectionProgress {
        val statuses = _statuses.value.filterKeys { it.collection == collection && it.number in 1..total }.values
        return HadithCollectionProgress(
            read = statuses.count { it.isRead },
            memorized = statuses.count { it.isMemorized },
            total = total,
        )
    }

    private fun set(id: HadithId, status: HadithStatus) {
        if (status == status(id)) return
        _statuses.value = if (status == HadithStatus.None) _statuses.value - id else _statuses.value + (id to status)
        storage.edit { preferences ->
            val read = readKey(id)
            val memorized = memorizedKey(id)
            if (status.isRead) preferences[read] = MARK else preferences.remove(read)
            if (status.isMemorized) preferences[memorized] = MARK else preferences.remove(memorized)
        }
    }

    companion object {
        private const val MARK = "1"
        private val KeyPattern = Regex("""^h_(read|mem)_(\w+)_(\d+)$""")

        fun readKey(id: HadithId) = stringPreferencesKey("h_read_${id.collection.key}_${id.number}")
        fun memorizedKey(id: HadithId) = stringPreferencesKey("h_mem_${id.collection.key}_${id.number}")

        private class ParsedKey(val collection: HadithCollection, val number: Int, val isMemorized: Boolean)

        private fun parseKey(name: String): ParsedKey? {
            val match = KeyPattern.matchEntire(name) ?: return null
            val (kind, collectionKey, number) = match.destructured
            val collection = HadithCollection.fromKey(collectionKey) ?: return null
            return ParsedKey(collection, number.toIntOrNull() ?: return null, isMemorized = kind == "mem")
        }

        /** Один проход по ключам `h_`: значение не `"1"`, неизвестный сборник, номер < 1 — пропустить. */
        private fun readStatuses(preferences: Preferences): Map<HadithId, HadithStatus> {
            val result = mutableMapOf<HadithId, HadithStatus>()
            for ((key, value) in preferences.asMap()) {
                if (value != MARK) continue
                val parsed = parseKey(key.name) ?: continue
                if (parsed.number < 1) continue
                val id = HadithId(parsed.collection, parsed.number)
                if (parsed.isMemorized) {
                    result[id] = HadithStatus.Memorized
                } else {
                    result.putIfAbsent(id, HadithStatus.Read)
                }
            }
            return result
        }
    }
}
