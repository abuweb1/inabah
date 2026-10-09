package app.inabah.android.core.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * «Избранное» хадисов из всех сборников (решения пользователя 2026-10-10, `docs/03-screen-hadith-home.md`).
 * Ключи — по образцу отметок: `h_fav_nawawi_1` = `"1"`; снятая закладка — ключ удаляется. Отдельно
 * от [HadithProgress]: «Сбросить прогресс» сборника избранное не трогает.
 */
class HadithFavorites(private val storage: PreferencesStorage) {
    private val _favorites = MutableStateFlow(readFavorites(storage.snapshot))

    val favorites: StateFlow<Set<HadithId>> = _favorites.asStateFlow()

    fun isFavorite(id: HadithId): Boolean = id in _favorites.value

    fun toggle(id: HadithId) {
        val key = key(id)
        if (isFavorite(id)) {
            _favorites.value -= id
            storage.edit { it.remove(key) }
        } else {
            _favorites.value += id
            storage.edit { it[key] = MARK }
        }
    }

    /** Убрать все закладки — все ключи `h_fav_…`, в том числе с неизвестным сборником или номером. */
    fun clear() {
        if (_favorites.value.isEmpty() && storage.snapshot.asMap().keys.none { KeyPattern.matches(it.name) }) return
        _favorites.value = emptySet()
        storage.edit { preferences ->
            preferences.asMap().keys.filter { KeyPattern.matches(it.name) }.forEach { preferences.remove(it) }
        }
    }

    companion object {
        private const val MARK = "1"
        private val KeyPattern = Regex("""^h_fav_(\w+)_(\d+)$""")

        fun key(id: HadithId) = stringPreferencesKey("h_fav_${id.collection.key}_${id.number}")

        /** Значение не `"1"`, неизвестный сборник, номер < 1 — пропустить. */
        private fun readFavorites(preferences: Preferences): Set<HadithId> =
            preferences.asMap().mapNotNullTo(mutableSetOf()) { (key, value) ->
                if (value != MARK) return@mapNotNullTo null
                val (collectionKey, number) = KeyPattern.matchEntire(key.name)?.destructured ?: return@mapNotNullTo null
                val collection = HadithCollection.fromKey(collectionKey) ?: return@mapNotNullTo null
                number.toIntOrNull()?.takeIf { it >= 1 }?.let { HadithId(collection, it) }
            }
    }
}
