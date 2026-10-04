package app.inabah.android.core.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** «Отметки хадисов». */
class HadithProgressTest {
    private val first = HadithId(HadithCollection.Nawawi, 1)
    private val second = HadithId(HadithCollection.Nawawi, 2)

    private fun key(name: String) = stringPreferencesKey(name)

    @Test
    fun `Без отметок — статус «нет»`() = TestStorage.run { storage ->
        assertEquals(HadithStatus.None, HadithProgress(storage.storage).status(first))
    }

    @Test
    fun `«Прочитан» включается и выключается`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)

        progress.toggleRead(first)
        assertEquals(HadithStatus.Read, progress.status(first))

        progress.toggleRead(first)
        assertEquals(HadithStatus.None, progress.status(first))
    }

    @Test
    fun `«Выучен» ставит и «прочитан»`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)

        progress.toggleMemorized(first)

        assertEquals(HadithStatus.Memorized, progress.status(first))
        assertTrue(progress.status(first).isRead)
    }

    @Test
    fun `Снятие «выучен» оставляет «прочитан»`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)
        progress.toggleMemorized(first)

        progress.toggleMemorized(first)

        assertEquals(HadithStatus.Read, progress.status(first))
    }

    @Test
    fun `Снятие «прочитан» снимает и «выучен»`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)
        progress.toggleMemorized(first)

        progress.toggleRead(first)

        assertEquals(HadithStatus.None, progress.status(first))
    }

    @Test
    fun `Отметки сохраняются между запусками в ключах прототипа`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)
        progress.toggleRead(first)
        progress.toggleMemorized(second)

        val disk = storage.persisted()
        assertEquals("1", disk[key("h_read_nawawi_1")])
        assertNull(disk[key("h_mem_nawawi_1")])
        assertEquals("1", disk[key("h_read_nawawi_2")])
        assertEquals("1", disk[key("h_mem_nawawi_2")])

        val restored = HadithProgress(storage.storage)
        assertEquals(HadithStatus.Read, restored.status(first))
        assertEquals(HadithStatus.Memorized, restored.status(second))
    }

    @Test
    fun `Снятая отметка удаляется из хранилища`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)
        progress.toggleMemorized(first)

        progress.toggleRead(first)

        val disk = storage.persisted()
        assertNull(disk[key("h_read_nawawi_1")])
        assertNull(disk[key("h_mem_nawawi_1")])
    }

    @Test
    fun `Сброс сборника удаляет все его отметки и не трогает другие сборники`() = TestStorage.run { storage ->
        val progress = HadithProgress(storage.storage)
        progress.toggleRead(first)
        progress.toggleMemorized(second)
        val qudsi = HadithId(HadithCollection.Qudsi, 1)
        progress.toggleRead(qudsi)
        // Чужие ключи с похожими именами — не отметки сборника.
        storage.storage.edit {
            it[key("h_read_nawawi_x")] = "1"
            it[intPreferencesKey("azkar.reset.morning")] = 17
            // Ключи сборника вне его номеров (адресация прототипа, лишние) — тоже его отметки.
            it[key("h_read_nawawi_0")] = "1"
            it[key("h_mem_nawawi_51")] = "1"
        }

        progress.reset(HadithCollection.Nawawi)

        assertEquals(HadithStatus.None, progress.status(first))
        assertEquals(HadithStatus.None, progress.status(second))
        assertEquals(HadithStatus.Read, progress.status(qudsi))
        val disk = storage.persisted()
        assertEquals("1", disk[key("h_read_nawawi_x")])
        assertEquals(17, disk[intPreferencesKey("azkar.reset.morning")])
        assertNull(disk[key("h_mem_nawawi_2")])
        assertNull(disk[key("h_read_nawawi_0")])
        assertNull(disk[key("h_mem_nawawi_51")])
        val restored = HadithProgress(storage.storage)
        assertEquals(0, restored.progress(HadithCollection.Nawawi, total = 50).read)
        assertEquals(HadithStatus.Read, restored.status(qudsi))
    }

    @Test
    fun `Номер 0 (адресация прототипа) и номера за пределами сборника в прогресс не попадают`() =
        TestStorage.run { storage ->
            val restarted = storage.seed {
                it[key("h_read_nawawi_0")] = "1"
                it[key("h_read_nawawi_51")] = "1"
                it[key("h_read_nawawi_50")] = "1"
            }

            val progress = HadithProgress(restarted).progress(HadithCollection.Nawawi, total = 50)

            assertEquals(1, progress.read)
        }

    @Test
    fun `«Выучен» без «прочитан» в хранилище читается как «выучен»`() = TestStorage.run { storage ->
        val restarted = storage.seed { it[key("h_mem_qudsi_3")] = "1" }

        assertEquals(HadithStatus.Memorized, HadithProgress(restarted).status(HadithId(HadithCollection.Qudsi, 3)))
    }

    @Test
    fun `Отметки одного сборника не попадают в другой, посторонние значения игнорируются`() =
        TestStorage.run { storage ->
            val restarted = storage.seed {
                it[key("h_read_ajurri_1")] = "1"
                it[key("h_read_nawawi_2")] = "0"
            }

            val progress = HadithProgress(restarted)

            assertEquals(HadithStatus.None, progress.status(first))
            assertEquals(HadithStatus.None, progress.status(second))
            assertEquals(HadithStatus.Read, progress.status(HadithId(HadithCollection.Ajurri, 1)))
        }

    @Test
    fun `Прогресс сборника — выученные входят в прочитанные, другие сборники не считаются`() =
        TestStorage.run { storage ->
            val progress = HadithProgress(storage.storage)
            progress.toggleRead(first)
            progress.toggleMemorized(second)
            progress.toggleRead(HadithId(HadithCollection.Qudsi, 1))

            val nawawi = progress.progress(HadithCollection.Nawawi, total = 50)

            assertEquals(2, nawawi.read)
            assertEquals(1, nawawi.memorized)
            assertEquals(0.04, nawawi.readFraction)
        }
}

/** «Порядок сборников хадисов». */
class HadithCollectionOrderTest {
    private val nawawi = HadithCollection.Nawawi
    private val qudsi = HadithCollection.Qudsi
    private val ajurri = HadithCollection.Ajurri

    @Test
    fun `По умолчанию — исходный порядок`() = TestStorage.run { storage ->
        val order = HadithCollectionOrder(storage.storage)

        assertEquals(listOf(nawawi, qudsi, ajurri), order.collections.value)
        assertTrue(order.isDefault)
    }

    @Test
    fun `Перемещение сохраняется между запусками`() = TestStorage.run { storage ->
        HadithCollectionOrder(storage.storage).move(fromIndex = 2, toIndex = 0)

        val restored = HadithCollectionOrder(storage.restart())
        assertEquals(listOf(ajurri, nawawi, qudsi), restored.collections.value)
        assertTrue(!restored.isDefault)
    }

    @Test
    fun `Действия TalkBack «Выше» и «Ниже», за краями списка — ничего`() = TestStorage.run { storage ->
        val order = HadithCollectionOrder(storage.storage)

        assertTrue(!order.canMove(nawawi, by = -1))
        order.move(nawawi, by = -1)
        assertTrue(order.isDefault)

        order.move(ajurri, by = -1)
        assertEquals(listOf(nawawi, ajurri, qudsi), order.collections.value)
        order.move(nawawi, by = 1)
        assertEquals(listOf(ajurri, nawawi, qudsi), order.collections.value)
        assertTrue(!order.canMove(qudsi, by = 1))
        assertEquals(listOf(ajurri, nawawi, qudsi), HadithCollectionOrder(storage.restart()).collections.value)
    }

    @Test
    fun `Восстановление исходного порядка`() = TestStorage.run { storage ->
        val order = HadithCollectionOrder(storage.storage)
        order.move(fromIndex = 0, toIndex = 2)

        order.restoreDefault()

        assertTrue(order.isDefault)
        assertTrue(HadithCollectionOrder(storage.restart()).isDefault)
    }

    @Test
    fun `Неизвестные значения и повторы отбрасываются, недостающий сборник — в конец`() =
        TestStorage.run { storage ->
            val restarted = storage.seed { it[stringPreferencesKey("hadith.collectionOrder")] = "ajurri,unknown,ajurri,nawawi" }

            assertEquals(listOf(ajurri, nawawi, qudsi), HadithCollectionOrder(restarted).collections.value)
        }
}
