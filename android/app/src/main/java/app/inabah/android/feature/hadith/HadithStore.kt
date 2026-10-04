package app.inabah.android.feature.hadith

import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Сборники хадисов в памяти (iOS `HadithStore`); хранения нет. Вызывать с главного потока. */
class HadithStore(private val repository: ContentRepository) {
    private val _collections = MutableStateFlow<Map<HadithCollection, Loadable<List<Hadith>>>>(emptyMap())

    /** Состояние каждого сборника; нет в словаре — [Loadable.Idle]. */
    val collections: StateFlow<Map<HadithCollection, Loadable<List<Hadith>>>> = _collections.asStateFlow()

    fun state(collection: HadithCollection): Loadable<List<Hadith>> =
        _collections.value[collection] ?: Loadable.Idle

    fun hadiths(collection: HadithCollection): List<Hadith> =
        (state(collection) as? Loadable.Loaded)?.value.orEmpty()

    /** Загружен или загружается — ничего; после ошибки — повторная попытка. */
    suspend fun load(collection: HadithCollection) {
        if (state(collection).isLoadingOrLoaded) return
        set(collection, Loadable.Loading)
        val result = try {
            Loadable.Loaded(repository.hadiths(collection))
        } catch (error: ContentError) {
            Loadable.Failed(error)
        } catch (cancellation: CancellationException) {
            // Загрузку отменили (ушли с экрана) — следующая попытка должна начаться заново.
            set(collection, Loadable.Idle)
            throw cancellation
        }
        set(collection, result)
    }

    suspend fun loadAll() {
        HadithCollection.entries.forEach { load(it) }
    }

    private fun set(collection: HadithCollection, state: Loadable<List<Hadith>>) {
        _collections.update { it + (collection to state) }
    }
}
