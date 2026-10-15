package app.inabah.android.feature.makharij

import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingCourse
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Курс «Учимся читать» в памяти (как `HadithStore`); хранения нет. Вызывать с главного потока. */
class ReadingStore(private val repository: ContentRepository) {
    private val _course = MutableStateFlow<Loadable<ReadingCourse>>(Loadable.Idle)

    val course: StateFlow<Loadable<ReadingCourse>> = _course.asStateFlow()

    /** Загружен или загружается - ничего; после ошибки - повторная попытка. */
    suspend fun load() {
        if (_course.value.isLoadingOrLoaded) return
        _course.value = Loadable.Loading
        _course.value = try {
            Loadable.Loaded(repository.reading())
        } catch (error: ContentError) {
            Loadable.Failed(error)
        } catch (cancellation: CancellationException) {
            // Загрузку отменили - следующая попытка должна начаться заново.
            _course.value = Loadable.Idle
            throw cancellation
        }
    }
}
