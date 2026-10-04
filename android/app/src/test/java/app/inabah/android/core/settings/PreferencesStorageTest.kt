package app.inabah.android.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Ошибки диска в `PreferencesStorage`: память и диск не расходятся, записи не теряются. */
class PreferencesStorageTest {
    private val key = stringPreferencesKey("test.value")

    /** DataStore в памяти с ошибками по требованию. */
    private class FlakyDataStore(
        initial: Preferences = emptyPreferences(),
        var failReads: Boolean = false,
        var failWrites: Int = 0,
    ) : DataStore<Preferences> {
        var disk: Preferences = initial
        var writes = 0

        override val data: Flow<Preferences> = flow {
            if (failReads) throw IOException("чтение")
            emit(disk)
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failWrites > 0) {
                failWrites--
                throw IOException("запись")
            }
            writes++
            disk = transform(disk)
            return disk
        }
    }

    @Test
    fun `Неудачная запись повторяется, значение доходит до диска`() = runTest {
        val dataStore = FlakyDataStore(failWrites = 1)
        val errors = mutableListOf<IOException>()
        val storage = PreferencesStorage(dataStore) { errors += it }
        storage.load()
        backgroundScope.launch { storage.runWriter() }

        storage.edit { it[key] = "1" }
        runCurrent()
        assertEquals(1, errors.size)
        assertNull(dataStore.disk[key])
        assertEquals("1", storage.snapshot[key])

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals("1", dataStore.disk[key])
    }

    @Test
    fun `Несколько правок подряд — одна запись всего снимка`() = runTest {
        val dataStore = FlakyDataStore(initial = emptyPreferences().toMutablePreferences().apply { this[key] = "старое" })
        val storage = PreferencesStorage(dataStore) { throw AssertionError(it) }
        storage.load()

        storage.edit { it[intPreferencesKey("a")] = 1 }
        storage.edit { it[intPreferencesKey("b")] = 2 }
        backgroundScope.launch { storage.runWriter() }
        runCurrent()

        assertEquals(1, dataStore.writes)
        assertEquals(1, dataStore.disk[intPreferencesKey("a")])
        assertEquals(2, dataStore.disk[intPreferencesKey("b")])
        assertEquals("старое", dataStore.disk[key], "прочитанное при запуске сохраняется")
    }

    @Test
    fun `Чтение не удалось — сессия только в памяти, диск не затирается`() = runTest {
        val dataStore = FlakyDataStore(
            initial = emptyPreferences().toMutablePreferences().apply { this[key] = "настоящее" },
            failReads = true,
        )
        val errors = mutableListOf<IOException>()
        val storage = PreferencesStorage(dataStore) { errors += it }
        storage.load()
        backgroundScope.launch { storage.runWriter() }

        storage.edit { it[intPreferencesKey("a")] = 1 }
        runCurrent()

        assertEquals(1, errors.size)
        assertEquals(1, storage.snapshot[intPreferencesKey("a")])
        assertEquals(0, dataStore.writes)
        assertEquals("настоящее", dataStore.disk[key])
    }

    @Test
    fun `Правка после закрытия хранилища — ошибка, а не тихая потеря`() = runTest {
        val storage = PreferencesStorage(FlakyDataStore()) { throw AssertionError(it) }
        storage.load()
        storage.close()

        assertFailsWith<IllegalStateException> { storage.edit { it[key] = "1" } }
    }
}
