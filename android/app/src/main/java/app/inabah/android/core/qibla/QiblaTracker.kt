package app.inabah.android.core.qibla

import kotlin.math.abs
import kotlin.math.exp

/** В какую сторону поворачиваться к Къибле. */
enum class TurnSide { Left, Right }

/** Насколько: «Поверните направо» / «Правее» / «Немного правее». */
enum class TurnAmount { Far, Mid, Near }

/**
 * Метка Къиблы на кольце относительно указателя (верха телефона).
 *
 * @property offset сглаженное отклонение метки, -180…180: плюс - справа от указателя.
 * @property angle тот же угол без перескока через 0°/360°: копится по кратчайшим поворотам,
 *   поэтому анимация к нему никогда не делает полный оборот.
 * @property side сторона поворота; около 180° держится прежней ([QiblaTuning.flipZoneDegrees]).
 * @property amount насколько поворачиваться - с запасом у границ [QiblaTuning.turnFarDegrees] / [QiblaTuning.turnNearDegrees].
 * @property tilted телефон наклонён (с гистерезисом [QiblaTuning.tiltMaxDegrees] / [QiblaTuning.tiltExitDegrees]).
 * @property lowAccuracy компас неточен: статус датчика или оценка точности (с гистерезисом).
 * @property aligned направление найдено - с гистерезисом и только при надёжном показании.
 * @property alignedEntries сколько раз метка входила в совпадение после настоящего ухода из него
 *   ([QiblaTuning.alignRearmMillis]): по росту счёта экран даёт один виброотклик.
 * @property generation номер подписки (трекера): у новой подписки счёт входов с нуля - экран сравнивает счёт
 *   только внутри одного номера (аудит 2026-10-24: после ↻ отклик был в момент нажатия).
 * @property heldByLocation метка у указателя и показание надёжно, но координатам нельзя доверить подтверждение.
 */
data class QiblaPointer(
    val offset: Double,
    val angle: Double,
    val side: TurnSide,
    val aligned: Boolean,
    val alignedEntries: Int,
    val tilted: Boolean = false,
    val lowAccuracy: Boolean = false,
    val amount: TurnAmount = TurnAmount.Far,
    val generation: Int = 0,
    val heldByLocation: Boolean = false,
)

/**
 * Сглаживает показания датчика и решает, найдено ли направление. Своё состояние - между показаниями;
 * один трекер на подписку экрана, время показаний - монотонное (мс).
 */
class QiblaTracker(private val tuning: QiblaTuning = QiblaTuning.Default, private val generation: Int = 0) {
    private var angle: Double? = null
    private var lastMillis = 0L
    private var side = TurnSide.Right
    private var aligned = false
    private var alignedEndedMillis: Long? = null
    private var entries = 0
    private var tilted = false
    private var inaccurate = false
    private var interference = false
    private var amount = TurnAmount.Far

    /**
     * Новое показание [sample]: метка должна стоять на [target] градусов от указателя (направление на Каабу
     * минус направление телефона, любой угол). [locationConfirmable] - координатам можно доверить подтверждение
     * ([LocationNote.confirmable]).
     */
    fun update(target: Double, sample: HeadingSample, locationConfirmable: Boolean = true): QiblaPointer {
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
        interference = interferenceFrom(sample)
        val estimate = sample.accuracyDegrees
        inaccurate = sample.lowAccuracy || interference ||
            (estimate != null && estimate > (if (inaccurate) tuning.headingAccuracyExitDegrees else tuning.headingAccuracyMaxDegrees))

        val limit = if (aligned) tuning.alignExitDegrees else tuning.alignEnterDegrees
        val onTarget = !tilted && !inaccurate && distance <= limit
        val nowAligned = onTarget && locationConfirmable
        if (nowAligned && !aligned) {
            val ended = alignedEndedMillis
            if (ended == null || at - ended >= tuning.alignRearmMillis) entries++
        } else if (!nowAligned && aligned) {
            alignedEndedMillis = at
        }
        aligned = nowAligned
        amount = turnAmount(distance)
        return QiblaPointer(offset, next, side, aligned, entries, tilted, inaccurate, amount, generation, heldByLocation = onTarget && !locationConfirmable)
    }

    /** Помеха: сила поля далеко от ожидаемой в этой точке; гистерезис - флаг не мигает у края. */
    private fun interferenceFrom(sample: HeadingSample): Boolean {
        val field = sample.fieldMicroTesla ?: return false
        val expected = sample.expectedFieldMicroTesla?.takeIf { it > 0 } ?: return false
        val deviation = abs(field / expected - 1)
        return deviation > if (interference) tuning.interferenceExitRatio else tuning.interferenceEnterRatio
    }

    /** Степень поворота: граница сдвигается на запас в сторону прежней степени - надпись не мигает. */
    private fun turnAmount(distance: Double): TurnAmount {
        val margin = tuning.turnHysteresisDegrees
        val far = tuning.turnFarDegrees + if (amount == TurnAmount.Far) -margin else margin
        val near = tuning.turnNearDegrees + if (amount == TurnAmount.Near) margin else -margin
        return when {
            distance > far -> TurnAmount.Far
            distance > near -> TurnAmount.Mid
            else -> TurnAmount.Near
        }
    }

    /** Доля пути к новому показанию за [elapsed] мс: экспоненциальное сглаживание, не зависит от частоты датчика. */
    private fun smoothingFactor(elapsed: Long): Double =
        if (tuning.smoothingMillis == 0L) 1.0 else 1.0 - exp(-elapsed.toDouble() / tuning.smoothingMillis)

    private companion object {
        const val HALF_TURN = 180.0
    }
}
