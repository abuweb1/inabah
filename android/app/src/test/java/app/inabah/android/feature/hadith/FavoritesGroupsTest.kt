package app.inabah.android.feature.hadith

import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlin.test.assertEquals
import org.junit.Test

/** Группы «Избранного»: порядок сборников, номера внутри, несуществующие номера, загрузка. */
class FavoritesGroupsTest {
    private fun hadiths(collection: HadithCollection, count: Int) =
        (1..count).map { Hadith(HadithId(collection, it), arabic = "حديث", translation = null) }

    private fun id(collection: HadithCollection, number: Int) = HadithId(collection, number)

    @Test
    fun `Группы — в порядке главной, внутри — по номеру, без пустых сборников`() {
        val favorites = setOf(
            id(HadithCollection.Nawawi, 3), id(HadithCollection.Nawawi, 1), id(HadithCollection.Ajurri, 2),
        )
        val states = HadithCollection.entries.associateWith { Loadable.Loaded(hadiths(it, 5)) }

        val groups = favoritesGroups(favorites, listOf(HadithCollection.Ajurri, HadithCollection.Qudsi, HadithCollection.Nawawi), states)

        assertEquals(listOf(HadithCollection.Ajurri, HadithCollection.Nawawi), groups.map { it.collection })
        val nawawi = groups[1] as FavoritesGroup.Hadiths
        assertEquals(listOf(1, 3), nawawi.hadiths.map { it.number })
    }

    @Test
    fun `Закладки на номера, которых нет в сборнике, не показываются`() {
        val favorites = setOf(id(HadithCollection.Qudsi, 44))
        val states = mapOf(HadithCollection.Qudsi to Loadable.Loaded(hadiths(HadithCollection.Qudsi, 40)))

        assertEquals(emptyList(), favoritesGroups(favorites, HadithCollection.entries, states))
    }

    @Test
    fun `Сборник не загружен — группа ждёт, ошибка — с повтором`() {
        val favorites = setOf(id(HadithCollection.Nawawi, 1), id(HadithCollection.Qudsi, 1))
        val states = mapOf(HadithCollection.Qudsi to Loadable.Failed(ContentError.ResourceMissing("qudsi.json")))

        val groups = favoritesGroups(favorites, listOf(HadithCollection.Nawawi, HadithCollection.Qudsi), states)

        assertEquals(
            listOf(
                FavoritesGroup.Pending(HadithCollection.Nawawi, failed = false),
                FavoritesGroup.Pending(HadithCollection.Qudsi, failed = true),
            ),
            groups,
        )
    }
}
