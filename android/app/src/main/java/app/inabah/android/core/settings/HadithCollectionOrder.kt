package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.HadithCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Порядок сборников на главной хадисов и в настройках (iOS `HadithCollectionOrder`).
 * Хранится строкой через запятую (`"qudsi,nawawi,ajurri"`): в DataStore Preferences нет списков,
 * а множество потеряло бы порядок.
 */
class HadithCollectionOrder(private val storage: PreferencesStorage) {
    private val _collections = MutableStateFlow(parse(storage.snapshot[KEY]))
    val collections: StateFlow<List<HadithCollection>> = _collections.asStateFlow()

    val isDefault: Boolean get() = _collections.value == DEFAULT_ORDER

    /** Перетаскивание: сборник с позиции [fromIndex] встаёт на позицию [toIndex] итогового списка. */
    fun move(fromIndex: Int, toIndex: Int) {
        val order = _collections.value.toMutableList()
        if (fromIndex !in order.indices || toIndex !in order.indices) return
        order.add(toIndex, order.removeAt(fromIndex))
        set(order)
    }

    /** Действия TalkBack «Выше» (−1) / «Ниже» (+1); за краями списка — ничего. */
    fun move(collection: HadithCollection, by: Int) {
        if (!canMove(collection, by)) return
        val index = _collections.value.indexOf(collection)
        move(index, index + by)
    }

    fun canMove(collection: HadithCollection, by: Int): Boolean {
        val index = _collections.value.indexOf(collection)
        return index >= 0 && (index + by) in _collections.value.indices
    }

    fun restoreDefault() {
        set(DEFAULT_ORDER)
    }

    private fun set(order: List<HadithCollection>) {
        if (order == _collections.value) return
        _collections.value = order
        storage.edit { it[KEY] = order.joinToString(SEPARATOR) { collection -> collection.key } }
    }

    companion object {
        val DEFAULT_ORDER: List<HadithCollection> = HadithCollection.entries

        private const val SEPARATOR = ","
        private val KEY = stringPreferencesKey("hadith.collectionOrder")

        /** Неизвестные значения и повторы — отбросить, недостающие сборники — в конец в исходном порядке. */
        private fun parse(stored: String?): List<HadithCollection> {
            val known = stored.orEmpty().split(SEPARATOR).mapNotNull(HadithCollection::fromKey).distinct()
            return known + DEFAULT_ORDER.filterNot { it in known }
        }
    }
}
