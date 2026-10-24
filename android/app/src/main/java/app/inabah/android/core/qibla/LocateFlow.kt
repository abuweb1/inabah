package app.inabah.android.core.qibla

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Один поиск координат ([LocationSource.locate]) без Android: геолокация выключена - [LocationStatus.Off];
 * иначе сразу последние известные (или «ищем»), затем свежие; не нашлось за [timeoutMillis] - старые остаются,
 * а без старых - [LocationStatus.Failed]. Отмена коллектора отменяет [current].
 */
fun locateFlow(
    enabled: () -> Boolean,
    lastKnown: () -> LocationFix?,
    current: suspend () -> LocationFix?,
    timeoutMillis: Long,
): Flow<LocationStatus> = flow {
    if (!enabled()) {
        emit(LocationStatus.Off)
        return@flow
    }
    val last = lastKnown()
    emit(last?.let { LocationStatus.Found(it) } ?: LocationStatus.Searching)
    val fresh = withTimeoutOrNull(timeoutMillis) { current() }
    when {
        fresh != null -> emit(LocationStatus.Found(fresh))
        last == null -> emit(LocationStatus.Failed)
    }
}
