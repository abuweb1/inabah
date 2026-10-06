package app.inabah.android.core.settings

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Вариант иконки приложения (iOS `AppIconOption`), порядок — как в iOS. [aliasName] — `activity-alias`
 * в манифесте: включён ровно один, его значок и показывает лаунчер.
 */
enum class AppIconOption(val aliasName: String) {
    Classic("IconClassic"),
    Niche("IconNiche"),
    Beads("IconBeads"),
    Dawn("IconDawn"),
}

/** Платформа: какая иконка включена сейчас и переключение. Блокирующие вызовы — не с главного потока. */
interface AppIconSwitcher {
    fun current(): AppIconOption

    fun select(option: AppIconOption)
}

/**
 * Выбранная иконка (iOS `AppIconSettings`). Своего хранения нет: источник истины — какой алиас включён,
 * [refresh] читает его. Выбор применяется сразу (решение пользователя 2026-10-06): часть лаунчеров при этом
 * закрывает приложение — об этом подпись под сеткой.
 */
class AppIconSettings(
    private val switcher: AppIconSwitcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val onUnreadable: (RuntimeException) -> Unit,
) {
    private val _current = MutableStateFlow(AppIconOption.Classic)
    val current: StateFlow<AppIconOption> = _current.asStateFlow()

    private val _failedToChange = MutableStateFlow(false)

    /** Последняя смена не удалась — на экране сообщение вместо подписи; сбрасывается следующим выбором. */
    val failedToChange: StateFlow<Boolean> = _failedToChange.asStateFlow()

    private var isChanging = false

    /** Сколько раз выбирали — чтение [refresh], начатое до выбора, не перезапишет его своим старым ответом. */
    private var selections = 0

    /**
     * Читает включённую иконку (при запуске приложения). Не читается — остаётся [AppIconOption.Classic],
     * причина — в [onUnreadable]: приложение работает, просто отметка может не совпасть с лаунчером.
     */
    suspend fun refresh() {
        val selectionsBefore = selections
        val enabled = try {
            withContext(ioDispatcher) { switcher.current() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            onUnreadable(error)
            return
        }
        if (selections == selectionsBefore && !isChanging) _current.value = enabled
    }

    /** Выбор текущей иконки или во время смены — ничего не делает. Ошибка — откат отметки и [failedToChange]. */
    suspend fun select(option: AppIconOption) {
        if (option == _current.value || isChanging) return
        val previous = _current.value
        selections++
        isChanging = true
        _current.value = option
        _failedToChange.value = false
        try {
            withContext(ioDispatcher) { switcher.select(option) }
        } catch (error: CancellationException) {
            // Экран закрыли во время смены: вызов платформы уже идёт и завершится сам — отметку не откатываем.
            throw error
        } catch (error: RuntimeException) {
            _current.value = previous
            _failedToChange.value = true
        } finally {
            isChanging = false
        }
    }
}
