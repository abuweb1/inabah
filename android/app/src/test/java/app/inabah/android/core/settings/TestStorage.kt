package app.inabah.android.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Хранилище на настоящем DataStore во временном файле на тест (аналог iOS `IsolatedDefaults`).
 * [restart] — «перезапуск приложения»: дописать очередь, закрыть DataStore и открыть файл заново.
 */
class TestStorage private constructor(private val testScope: TestScope, private val file: File) {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: CoroutineScope
    private lateinit var writer: Job

    lateinit var storage: PreferencesStorage
        private set

    private suspend fun open(): PreferencesStorage {
        scope = CoroutineScope(testScope.backgroundScope.coroutineContext + Job(testScope.backgroundScope.coroutineContext.job))
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        storage = PreferencesStorage(dataStore) { throw AssertionError("Ошибка хранилища", it) }
        storage.load()
        writer = scope.launch { storage.runWriter() }
        return storage
    }

    /** Дописать отложенные записи и открыть хранилище заново — как после перезапуска. */
    suspend fun restart(): PreferencesStorage {
        storage.close()
        writer.join()
        scope.coroutineContext.job.cancelAndJoin()
        return open()
    }

    /** Записать «сырые» значения в обход настроек (поврежденные или старые данные) и перезапустить. */
    suspend fun seed(transform: (MutablePreferences) -> Unit): PreferencesStorage {
        storage.edit(transform)
        return restart()
    }

    /** Что лежит на диске — после записи очереди. */
    suspend fun persisted(): Preferences {
        restart()
        return dataStore.data.first()
    }

    companion object {
        fun run(block: suspend TestScope.(TestStorage) -> Unit) = runTest {
            val directory = createTempDirectory("inabah-test").toFile()
            try {
                val testStorage = TestStorage(this, File(directory, "test.preferences_pb"))
                testStorage.open()
                block(testStorage)
                testStorage.storage.close()
                testStorage.writer.join()
            } finally {
                directory.deleteRecursively()
            }
        }
    }
}
