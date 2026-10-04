package app.inabah.android.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Единое хранилище настроек и прогресса над одним `DataStore<Preferences>` (аналог iOS `UserDefaults`).
 *
 * Модель в памяти ([snapshot]) меняется сразу при [edit] — UI не ждёт диска. На диск пишется
 * **весь снимок**, а не отдельные изменения: несколько правок подряд сливаются в одну запись,
 * а неудачная запись не теряет изменение — его повторит следующая запись. Пишет [runWriter],
 * который запускает владелец (`AppContainer`) в области приложения. Читать и менять — с главного потока.
 *
 * Первое чтение не удалось ([load], ошибка ввода-вывода, не повреждение файла) — сессия только
 * в памяти: значения по умолчанию, на диск ничего не пишется, чтобы не затереть настоящие данные.
 */
class PreferencesStorage(
    private val dataStore: DataStore<Preferences>,
    private val onError: (IOException) -> Unit,
) {
    /** Есть несохранённые изменения; лишние сигналы сливаются. */
    private val pendingWrite = Channel<Unit>(Channel.CONFLATED)
    private var isPersistent = true
    private var isClosed = false

    /** Текущее значение; до [load] — пустое. */
    var snapshot: Preferences = emptyPreferences()
        private set

    /** Первое чтение с диска. */
    suspend fun load() {
        snapshot = try {
            dataStore.data.first()
        } catch (error: IOException) {
            onError(error)
            isPersistent = false
            emptyPreferences()
        }
    }

    /** Изменить сразу в памяти и поставить запись на диск. */
    fun edit(transform: (MutablePreferences) -> Unit) {
        check(!isClosed) { "Хранилище закрыто — изменение не сохранится" }
        snapshot = snapshot.toMutablePreferences().apply(transform).toPreferences()
        if (isPersistent) pendingWrite.trySend(Unit)
    }

    /**
     * Пишет текущий снимок после каждого изменения, пока хранилище не закрыто ([close])
     * или корутина не отменена. Ошибка записи сообщается [onError] и повторяется с паузой
     * ([RETRY_DELAYS_MS]); не удалось — запишет следующее изменение.
     */
    suspend fun runWriter() {
        for (signal in pendingWrite) {
            for (retryDelay in RETRY_DELAYS_MS) {
                val target = snapshot
                val written = try {
                    dataStore.updateData { target }
                    true
                } catch (error: IOException) {
                    onError(error)
                    false
                }
                if (written) break
                delay(retryDelay)
            }
        }
    }

    /** Новые изменения не принимаются; [runWriter] допишет последнее и завершится. */
    fun close() {
        isClosed = true
        pendingWrite.close()
    }

    private companion object {
        val RETRY_DELAYS_MS = listOf(1_000L, 5_000L, 30_000L)
    }
}
