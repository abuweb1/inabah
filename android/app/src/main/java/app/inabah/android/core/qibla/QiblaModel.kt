package app.inabah.android.core.qibla

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * Состояние экрана «Къибла» из местоположения и датчика. Подписки на датчик и поиск координат живут ровно
 * столько, сколько экран собирает [state] (`collectAsStateWithLifecycle` снимает их на `ON_STOP`);
 * при новой подписке координаты ищутся заново. Координаты не сохраняются и не пишутся в журнал.
 *
 * @param now монотонные часы - те же, что время в [LocationFix] и [MagneticHeading].
 */
class QiblaModel(
    private val location: LocationSource,
    private val heading: HeadingSource,
    private val declination: MagneticDeclination,
    private val now: () -> Long,
    private val tuning: QiblaTuning = QiblaTuning.Default,
) {
    private val refreshes = MutableStateFlow(0)

    /** «Обновить» / «Повторить»: новый поиск координат у всех, кто собирает [state]. */
    fun refreshLocation() = refreshes.update { it + 1 }

    /** Поток состояния при доступе [access]; без доступа датчик и поиск не запускаются. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun state(access: LocationAccess): Flow<QiblaUiState> {
        if (!access.granted) {
            return flowOf(qiblaUiState(access, LocationStatus.Searching, HeadingStatus.Waiting, null, now(), tuning))
        }
        return flow {
            // Свой трекер на каждую подписку: после возврата на экран метка встаёт сразу.
            val tracker = QiblaTracker(tuning)
            val locations = refreshes.flatMapLatest { location.locate() }
            val headings: Flow<MagneticHeading?> =
                if (heading.available) heading.headings().onStart<MagneticHeading?> { emit(null) } else flowOf(null)
            var declinationFix: LocationFix? = null
            var declinationDegrees = 0.0
            val states = combine(locations, headings) { status, raw ->
                val fix = (status as? LocationStatus.Found)?.fix
                if (fix == null || raw == null || !heading.available) {
                    val headingStatus = if (heading.available) HeadingStatus.Waiting else HeadingStatus.NoSensor
                    return@combine qiblaUiState(access, status, headingStatus, null, now(), tuning)
                }
                if (fix != declinationFix) {
                    declinationFix = fix
                    declinationDegrees = declination.at(fix)
                }
                val sample = HeadingSample(
                    trueHeading = normalizeDegrees(raw.magneticAzimuth + declinationDegrees),
                    accuracyDegrees = raw.accuracyDegrees,
                    lowAccuracy = raw.lowAccuracy,
                    tiltDegrees = raw.tiltDegrees,
                    timeMillis = raw.timeMillis,
                )
                val target = signedDelta(sample.trueHeading, qiblaBearing(fix.latitude, fix.longitude))
                val pointer = tracker.update(target, sample)
                qiblaUiState(access, status, HeadingStatus.Reading(sample), pointer, now(), tuning)
            }
            emitAll(states)
        }
    }
}
