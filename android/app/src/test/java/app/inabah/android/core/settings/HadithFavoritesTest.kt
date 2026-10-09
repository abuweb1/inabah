package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** «Избранное» хадисов. */
class HadithFavoritesTest {
    private val nawawi = HadithId(HadithCollection.Nawawi, 1)
    private val qudsi = HadithId(HadithCollection.Qudsi, 7)

    private fun key(name: String) = stringPreferencesKey(name)

    @Test
    fun `Закладка ставится и снимается и переживает перезапуск в ключах h_fav`() = TestStorage.run { storage ->
        val favorites = HadithFavorites(storage.storage)

        favorites.toggle(nawawi)
        favorites.toggle(qudsi)
        assertEquals(setOf(nawawi, qudsi), favorites.favorites.value)
        assertEquals("1", storage.persisted()[key("h_fav_nawawi_1")])
        assertEquals(setOf(nawawi, qudsi), HadithFavorites(storage.storage).favorites.value, "после перезапуска")

        val restarted = HadithFavorites(storage.storage)
        restarted.toggle(nawawi)
        assertFalse(restarted.isFavorite(nawawi))
        assertNull(storage.persisted()[key("h_fav_nawawi_1")], "снятая закладка — ключ удалён")
    }

    @Test
    fun `Очистить избранное удаляет только закладки — отметки целы`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)
        val favorites = HadithFavorites(storage.storage)
        progress.toggleRead(nawawi)
        favorites.toggle(nawawi)
        favorites.toggle(qudsi)

        favorites.clear()

        assertTrue(favorites.favorites.value.isEmpty())
        val persisted = storage.persisted()
        assertNull(persisted[key("h_fav_nawawi_1")])
        assertNull(persisted[key("h_fav_qudsi_7")])
        assertEquals("1", persisted[key("h_read_nawawi_1")])
    }

    @Test
    fun `Сброс прогресса сборника не трогает избранное`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)
        val favorites = HadithFavorites(storage.storage)
        progress.toggleMemorized(nawawi)
        favorites.toggle(nawawi)

        progress.reset(HadithCollection.Nawawi)

        assertTrue(favorites.isFavorite(nawawi))
        assertEquals("1", storage.persisted()[key("h_fav_nawawi_1")])
    }

    @Test
    fun `Посторонние ключи и значения пропускаются, очистка убирает и их`() = TestStorage.run { storage ->
        val seeded = storage.seed {
            it[key("h_fav_nawawi_2")] = "1"
            it[key("h_fav_nawawi_3")] = "0"
            it[key("h_fav_bukhari_1")] = "1"
            it[key("h_fav_qudsi_0")] = "1"
            it[key("h_fav_qudsi_x")] = "1"
        }
        val favorites = HadithFavorites(seeded)

        assertEquals(setOf(HadithId(HadithCollection.Nawawi, 2)), favorites.favorites.value)

        favorites.clear()
        val persisted = storage.persisted()
        assertTrue(persisted.asMap().keys.none { it.name.matches(Regex("""^h_fav_\w+_\d+$""")) })
        assertEquals("1", persisted[key("h_fav_qudsi_x")], "не наш формат — не трогаем")
    }
}
