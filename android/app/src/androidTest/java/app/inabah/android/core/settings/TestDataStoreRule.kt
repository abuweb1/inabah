package app.inabah.android.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * DataStore во временной папке на тест (как JVM-помощник `TestStorage`). Подключать **внешним**
 * правилом (`@get:Rule(order = 0)`, Compose — `order = 1`): экран при уходе ещё пишет в хранилище
 * (сверка времени азкаров), поэтому закрывать его — после Compose.
 *
 * Закрытие: дописать очередь → дождаться писателя → остановить DataStore → удалить папку. Раньше
 * область отменялась без ожидания и папка удалялась посреди записи: `IOException` в писателе →
 * `AssertionError` в корутине → падал весь процесс тестов (аудит 2026-10-06).
 */
class TestDataStoreRule : ExternalResource() {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scope: CoroutineScope
    private lateinit var directory: File
    private lateinit var writer: Job

    lateinit var storage: PreferencesStorage
        private set

    override fun before() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        directory = File(context.cacheDir, "datastore-test-${System.nanoTime()}").apply { mkdirs() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "test.preferences_pb") }
        storage = PreferencesStorage(dataStore) { throw AssertionError("Ошибка хранилища", it) }
        runBlocking { storage.load() }
        writer = scope.launch { storage.runWriter() }
    }

    override fun after() {
        storage.close()
        runBlocking {
            writer.join()
            scope.coroutineContext.job.cancelAndJoin()
        }
        directory.deleteRecursively()
    }
}
