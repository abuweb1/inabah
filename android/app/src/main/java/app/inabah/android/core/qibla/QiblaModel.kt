package app.inabah.android.core.qibla

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/**
 * Состояние экрана «Къибла» из местоположения и датчика. Подписки на датчик и поиск координат живут ровно
 * столько, сколько экран собирает [state] (`collectAsStateWithLifecycle` снимает их на `ON_STOP`);
 * при новой подписке координаты ищутся заново, трекер - новый (свой номер, счёт откликов с нуля).
 * Координаты не сохраняются и не пишутся в журнал.
 *
 * @param now монотонные часы - те же, что время в [LocationFix] и [MagneticHeading].
 */
class QiblaModel(
    private val location: LocationSource,
    private val heading: HeadingSource,
    private val magnetic: MagneticModel,
    private val now: () -> Long,
    private val tuning: QiblaTuning = QiblaTuning.Default,
) {
    private val refreshes = MutableStateFlow(0)
    private val generations = AtomicInteger()

    /** «Обновить» / «Повторить»: новый поиск координат у всех, кто собирает [state]. */
    fun refreshLocation() = refreshes.update { it + 1 }

    /** Поток состояния при доступе [access]; без доступа датчик и поиск не запускаются. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun state(access: LocationAccess): Flow<QiblaUiState> {
        if (!access.granted) {
            return flowOf(qiblaUiState(access, LocationStatus.Searching, HeadingStatus.Waiting, null, now(), tuning))
        }
        return flow {
            val tracker = QiblaTracker(tuning, generations.incrementAndGet())
            val locations = refreshes.flatMapLatest { location.locate() }
            val headings: Flow<HeadingEvent> = if (heading.available) {
                heading.headings()
                    .map<MagneticHeading, HeadingEvent> { HeadingEvent.Raw(it) }
                    .onStart { emit(HeadingEvent.Waiting) }
                    // Не удалось подписаться на датчик - как без датчика.
                    .catch { emit(HeadingEvent.NoSensor) }
            } else {
                flowOf(HeadingEvent.NoSensor)
            }
            var fieldFix: LocationFix? = null
            var declination = 0.0
            var expectedField = 0.0
            val states = combine(locations, headings) { status, event ->
                val fix = (status as? LocationStatus.Found)?.fix
                val raw = (event as? HeadingEvent.Raw)?.heading
                if (fix == null || raw == null) {
                    val headingStatus = if (event == HeadingEvent.NoSensor) HeadingStatus.NoSensor else HeadingStatus.Waiting
                    return@combine qiblaUiState(access, status, headingStatus, null, now(), tuning)
                }
                if (fix != fieldFix) {
                    fieldFix = fix
                    declination = magnetic.declination(fix)
                    expectedField = magnetic.fieldMicroTesla(fix)
                }
                val sample = HeadingSample(
                    trueHeading = normalizeDegrees(raw.magneticAzimuth + declination),
                    accuracyDegrees = raw.accuracyDegrees,
                    lowAccuracy = raw.lowAccuracy,
                    tiltDegrees = raw.tiltDegrees,
                    timeMillis = raw.timeMillis,
                    fieldMicroTesla = raw.fieldMicroTesla,
                    expectedFieldMicroTesla = expectedField,
                )
                val at = now()
                val target = signedDelta(sample.trueHeading, qiblaBearing(fix.latitude, fix.longitude))
                val pointer = tracker.update(target, sample, locationNote(access, fix, at, tuning).confirmable)
                qiblaUiState(access, status, HeadingStatus.Reading(sample), pointer, at, tuning)
            }
            emitAll(states)
        }
    }

    /** Событие датчика внутри модели: показание до поправки на склонение; наружу - [HeadingStatus] с истинным азимутом. */
    private sealed interface HeadingEvent {
        data object Waiting : HeadingEvent
        data object NoSensor : HeadingEvent
        class Raw(val heading: MagneticHeading) : HeadingEvent
    }
}
