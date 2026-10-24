package app.inabah.android.core.qibla

import kotlin.math.abs
import kotlin.math.exp

/** В какую сторону поворачиваться к Къибле. */
enum class TurnSide { Left, Right }

/**
 * Метка Къиблы на кольце относительно указателя (верха телефона).
 *
 * @property offset сглаженное отклонение метки, -180…180: плюс - справа от указателя.
 * @property angle тот же угол без перескока через 0°/360°: копится по кратчайшим поворотам,
 *   поэтому анимация к нему никогда не делает полный оборот.
 * @property side сторона поворота; около 180° держится прежней ([QiblaTuning.flipZoneDegrees]).
 * @property tilted телефон наклонён (с гистерезисом [QiblaTuning.tiltMaxDegrees] / [QiblaTuning.tiltExitDegrees]).
 * @property lowAccuracy компас неточен: статус датчика или оценка точности (с гистерезисом).
 * @property aligned направление найдено - с гистерезисом и только при надёжном показании.
 * @property alignedEntries сколько раз метка входила в совпадение после настоящего ухода из него
 *   ([QiblaTuning.alignRearmMillis]): по росту счёта экран даёт один виброотклик.
 */
data class QiblaPointer(
    val offset: Double,
    val angle: Double,
    val side: TurnSide,
    val aligned: Boolean,
    val alignedEntries: Int,
    val tilted: Boolean = false,
    val lowAccuracy: Boolean = false,
)

/**
 * Сглаживает показания датчика и решает, найдено ли направление. Своё состояние - между показаниями;
 * один трекер на подписку экрана, время показаний - монотонное (мс).
 */
class QiblaTracker(private val tuning: QiblaTuning = QiblaTuning.Default) {
    private var angle: Double? = null
    private var lastMillis = 0L
    private var side = TurnSide.Right
    private var aligned = false
    private var alignedEndedMillis: Long? = null
    private var entries = 0
    private var tilted = false
    private var inaccurate = false

    /**
     * Новое показание [sample]: метка должна стоять на [target] градусов от указателя (направление на Каабу
     * минус направление телефона, любой угол).
     */
    fun update(target: Double, sample: HeadingSample): QiblaPointer {
        val at = sample.timeMillis
        val current = angle
        val elapsed = at - lastMillis
        val next = when {
            current == null -> signedDelta(0.0, target)
            elapsed !in 0..tuning.snapAfterMillis -> current + signedDelta(current, target)
            else -> current + signedDelta(current, target) * smoothingFactor(elapsed)
        }
        angle = next
        lastMillis = at

        val offset = signedDelta(0.0, next)
        val distance = abs(offset)
        if (distance < HALF_TURN - tuning.flipZoneDegrees && offset != 0.0) {
            side = if (offset > 0) TurnSide.Right else TurnSide.Left
        }
        tilted = sample.tiltDegrees > (if (tilted) tuning.tiltExitDegrees else tuning.tiltMaxDegrees)
        val estimate = sample.accuracyDegrees
        inaccurate = sample.lowAccuracy ||
            (estimate != null && estimate > (if (inaccurate) tuning.headingAccuracyExitDegrees else tuning.headingAccuracyMaxDegrees))

        val limit = if (aligned) tuning.alignExitDegrees else tuning.alignEnterDegrees
        val nowAligned = !tilted && !inaccurate && distance <= limit
        if (nowAligned && !aligned) {
            val ended = alignedEndedMillis
            if (ended == null || at - ended >= tuning.alignRearmMillis) entries++
        } else if (!nowAligned && aligned) {
            alignedEndedMillis = at
        }
        aligned = nowAligned
        return QiblaPointer(offset, next, side, aligned, entries, tilted, inaccurate)
    }

    /** Доля пути к новому показанию за [elapsed] мс: экспоненциальное сглаживание, не зависит от частоты датчика. */
    private fun smoothingFactor(elapsed: Long): Double =
        if (tuning.smoothingMillis == 0L) 1.0 else 1.0 - exp(-elapsed.toDouble() / tuning.smoothingMillis)

    private companion object {
        const val HALF_TURN = 180.0
    }
}
