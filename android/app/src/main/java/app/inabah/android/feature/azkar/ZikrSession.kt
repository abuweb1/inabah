package app.inabah.android.feature.azkar

import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.content.model.ZikrId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Состояние карточки зикра: счёт, кнопка «Аа», раскрытие выполненной карточки. */
data class ZikrState(
    val count: Int,
    val isTranslationVisible: Boolean = false,
    val isExpanded: Boolean = false,
)

enum class IncrementResult { Counted, Completed, AlreadyCompleted }

/**
 * Держатель состояния одного зикра (iOS `ZikrSession`): нажатие меняет только его [state],
 * перерисовывается одна карточка. [onCountChange] — стор сохраняет раздел и пересчитывает прогресс.
 * Вызывать с главного потока.
 */
class ZikrSession(
    val zikr: Zikr,
    count: Int = 0,
    private val onCountChange: () -> Unit = {},
) {
    private val _state = MutableStateFlow(ZikrState(count = count.coerceIn(0, zikr.repetitions)))
    val state: StateFlow<ZikrState> = _state.asStateFlow()

    val id: ZikrId get() = zikr.id
    val count: Int get() = _state.value.count
    val isCompleted: Boolean get() = count >= zikr.repetitions

    /** Выполнен — ничего; иначе +1, а ставший выполненным сворачивается. */
    fun increment(): IncrementResult {
        if (isCompleted) return IncrementResult.AlreadyCompleted
        _state.update { it.copy(count = it.count + 1) }
        onCountChange()
        if (!isCompleted) return IncrementResult.Counted
        _state.update { it.copy(isExpanded = false) }
        return IncrementResult.Completed
    }

    /** Сброс карточки; сохраняется, только если счёт был больше нуля. */
    fun reset() {
        val hadProgress = count > 0
        _state.update { it.copy(count = 0, isExpanded = false) }
        if (hadProgress) onCountChange()
    }

    fun setTranslationVisible(visible: Boolean) {
        _state.update { it.copy(isTranslationVisible = visible) }
    }

    fun setExpanded(expanded: Boolean) {
        _state.update { it.copy(isExpanded = expanded) }
    }

    /** Обнуление всем разделом (новый период, ручной сброс): стор сохраняет раздел сам, один раз. */
    internal fun discardProgress() {
        _state.update { it.copy(count = 0, isExpanded = false) }
    }
}
