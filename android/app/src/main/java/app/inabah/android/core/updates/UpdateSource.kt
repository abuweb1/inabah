package app.inabah.android.core.updates

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Вышла новая версия в магазине: [version] — для подписи значка («Доступна версия 1.2.0»). */
data class AvailableUpdate(val version: String)

/**
 * Есть ли новая версия — для значка «Обновить» на главной «Азкаров». До 1.2.0 — только заготовка:
 * в приложении [NoUpdateSource] (интернет не трогаем), в 1.2.0 — Play In-App Updates.
 */
fun interface UpdateSource {
    suspend fun availableUpdate(): AvailableUpdate?
}

/** «Обновления нет» — без сети; реализация 1.1.1. */
object NoUpdateSource : UpdateSource {
    override suspend fun availableUpdate(): AvailableUpdate? = null
}

/**
 * Только для debug-сборки — показать значок для проверки и скриншотов: номер версии в файле
 * `files/debug-fake-update` (`adb shell run-as app.inabah.android sh -c 'echo 1.2.0 > files/debug-fake-update'`).
 * Нет файла или он пустой — обновления нет.
 */
class DebugFileUpdateSource(private val file: File, private val ioDispatcher: CoroutineDispatcher) : UpdateSource {
    override suspend fun availableUpdate(): AvailableUpdate? = withContext(ioDispatcher) {
        try {
            file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let(::AvailableUpdate)
        } catch (_: IOException) {
            null
        }
    }

    companion object {
        const val FILE_NAME = "debug-fake-update"
    }
}
