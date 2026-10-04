package app.inabah.android.feature.azkar

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.settings.AzkarPeriod
import app.inabah.android.core.settings.AzkarResetSchedule
import app.inabah.android.core.settings.AzkarResetSettings
import app.inabah.android.core.settings.PreferencesStorage
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Прогресс раздела: сколько зикров выполнено из скольких. */
data class SectionProgress(val completed: Int, val total: Int) {
    val fraction: Double get() = if (total > 0) completed.toDouble() / total else 0.0
    val isFinished: Boolean get() = total > 0 && completed == total
}

/**
 * Счёт азкаров обоих разделов на всё время работы приложения (iOS `AzkarStore`).
 *
 * Прогресс сохраняется между запусками и обнуляется каждый день во время из [resetSettings]
 * (docs/android/03-domain-logic.md, 3.3): у раздела хранится период «действителен до».
 * Пересчёт — [refreshPeriods] (возврат приложения, смена времени и пояса системы) и таймер
 * [runResetTimer], который запускает владелец стора. Вызывать с главного потока.
 */
class AzkarStore(
    private val repository: ContentRepository,
    private val storage: PreferencesStorage,
    private val resetSettings: AzkarResetSettings,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
    /** Сохранённый прогресс не читается — раздел начинает новый период; сообщить в лог. */
    private val onUnreadableProgress: (AzkarSection, Exception) -> Unit,
) {
    private val sections = AzkarSection.entries.associateWith {
        MutableStateFlow<Loadable<List<ZikrSession>>>(Loadable.Idle)
    }
    private val progressBySection = AzkarSection.entries.associateWith { MutableStateFlow(SectionProgress(0, 0)) }

    // Одни и те же объекты на каждый вызов: Compose собирает поток по ключу-объекту и иначе
    // перезапускал бы сборщик на каждой перерисовке.
    private val stateViews = sections.mapValues { (_, flow) -> flow.asStateFlow() }
    private val progressViews = progressBySection.mapValues { (_, flow) -> flow.asStateFlow() }

    /** Раздел загружен — у него есть период. */
    private val periods = mutableMapOf<AzkarSection, AzkarPeriod>()

    /** Разделы, за выполнение которых уже показан оверлей «مَا شَاءَ اللَّهُ» в этом периоде. */
    private val acknowledgedCompletions = mutableSetOf<AzkarSection>()

    /**
     * Счётчик перепланирований для [runResetTimer]: растёт при каждом, даже если ближайшее обнуление
     * то же самое, — таймер пересчитывает ожидание от нового «сейчас» (часы могли сдвинуться вперёд).
     */
    private val timerRequests = MutableStateFlow(0L)

    init {
        resetSettings.onResetTimeChange(::adoptNewResetTimes)
    }

    fun state(section: AzkarSection): StateFlow<Loadable<List<ZikrSession>>> = stateViews.getValue(section)

    fun sessions(section: AzkarSection): List<ZikrSession> =
        (sections.getValue(section).value as? Loadable.Loaded)?.value.orEmpty()

    /** Меняется только при выполнении или сбросе зикра, а не на каждое нажатие. */
    fun progress(section: AzkarSection): StateFlow<SectionProgress> = progressViews.getValue(section)

    /** Загружен или загружается — ничего; после ошибки — повторная попытка. Повторная загрузка не сбрасывает счёт. */
    suspend fun load(section: AzkarSection) {
        val state = sections.getValue(section)
        if (state.value.isLoadingOrLoaded) return
        state.value = Loadable.Loading
        val azkar = try {
            repository.azkar(section)
        } catch (error: ContentError) {
            state.value = Loadable.Failed(error)
            return
        } catch (cancellation: CancellationException) {
            // Загрузку отменили (ушли с экрана) — следующая попытка должна начаться заново.
            state.value = Loadable.Idle
            throw cancellation
        }
        val restored = restoredProgress(section)
        val sessions = azkar.map { zikr ->
            ZikrSession(zikr, count = restored.counts[zikr.number] ?: 0) { countDidChange(section) }
        }
        periods[section] = restored.period
        if (restored.completionShown) acknowledgedCompletions += section
        state.value = Loadable.Loaded(sessions)
        updateProgress(section)
        if (restored.needsSave) save(section)
        scheduleNextRefresh()
    }

    suspend fun loadAll() {
        AzkarSection.entries.forEach { load(it) }
    }

    /** Есть ли что сбрасывать: у загруженного — счёт в памяти, у незагруженного — в сохранении. */
    fun hasProgress(section: AzkarSection): Boolean =
        if (section in periods) {
            sessions(section).any { it.count > 0 }
        } else {
            readStored(section)?.counts?.isNotEmpty() == true
        }

    /** Ручной сброс из настроек: загруженный — обнулить и сохранить (период тот же), иначе забыть сохранённое. */
    fun resetProgress(section: AzkarSection) {
        acknowledgedCompletions -= section
        if (section in periods) {
            sessions(section).forEach(ZikrSession::discardProgress)
            updateProgress(section)
            save(section)
        } else {
            storage.edit { it.remove(progressKey(section)) }
        }
    }

    /** Пересчёт периодов загруженных разделов: смена пояса, наступившее обнуление. */
    fun refreshPeriods() {
        val instant = now()
        for ((section, period) in periods.toMap()) {
            val schedule = schedule(section)
            val relocated = period.relocated(schedule, instant)
            if (relocated.isExpired(instant)) {
                periods[section] = AzkarPeriod.startingAt(instant, schedule)
                sessions(section).forEach(ZikrSession::discardProgress)
                acknowledgedCompletions -= section
                updateProgress(section)
                save(section)
            } else if (relocated != period) {
                periods[section] = relocated
                save(section)
            }
        }
        scheduleNextRefresh()
    }

    /** Время обнуления изменили в настройках: ближайшее обнуление не пропускается и не происходит дважды. */
    private fun adoptNewResetTimes() {
        val instant = now()
        for ((section, period) in periods.toMap()) {
            val rescheduled = period.rescheduled(schedule(section), instant)
            if (rescheduled != period) {
                periods[section] = rescheduled
                save(section)
            }
        }
        scheduleNextRefresh()
    }

    fun shouldPresentCompletion(section: AzkarSection): Boolean =
        progressBySection.getValue(section).value.isFinished && section !in acknowledgedCompletions

    /** Оверлей показан — запомнить до конца периода (сохраняется: после перезапуска не показывать снова). */
    fun acknowledgeCompletion(section: AzkarSection) {
        if (acknowledgedCompletions.add(section)) save(section)
    }

    /** Раздел снова не выполнен — следующее завершение покажет оверлей снова. */
    fun resetCompletionAcknowledgement(section: AzkarSection) {
        if (acknowledgedCompletions.remove(section)) save(section)
    }

    /**
     * Спит до ближайшего обнуления среди загруженных разделов и пересчитывает периоды;
     * перепланируется после каждой загрузки и пересчёта. Работает, пока не отменена вызывающая корутина.
     * Пока процесс спит, не срабатывает — для этого [refreshPeriods] при возврате приложения.
     */
    suspend fun runResetTimer() {
        timerRequests.collectLatest {
            val deadline = periods.values.minOfOrNull { it.validUntil } ?: return@collectLatest
            // Повторная проверка: часы устройства могли сдвинуться назад, пока корутина спала.
            while (true) {
                val wait = Duration.between(now(), deadline)
                if (wait <= Duration.ZERO) break
                delay(wait.toMillis().coerceAtLeast(1))
            }
            refreshPeriods()
        }
    }

    private fun schedule(section: AzkarSection) = AzkarResetSchedule(resetSettings.resetTime(section), zone())

    private fun countDidChange(section: AzkarSection) {
        updateProgress(section)
        save(section)
    }

    private fun updateProgress(section: AzkarSection) {
        val sessions = sessions(section)
        progressBySection.getValue(section).value =
            SectionProgress(completed = sessions.count { it.isCompleted }, total = sessions.size)
    }

    private fun scheduleNextRefresh() {
        timerRequests.update { it + 1 }
    }

    // Хранение: azkar.progress.<раздел> — JSON с периодом, счётом зикров (> 0) и пометкой оверлея.

    private class RestoredProgress(
        val period: AzkarPeriod,
        val counts: Map<Int, Int>,
        val completionShown: Boolean,
        val needsSave: Boolean,
    )

    /** Нет сохранения, не читается или период истёк — новый период с пустым счётом. */
    private fun restoredProgress(section: AzkarSection): RestoredProgress {
        val instant = now()
        val schedule = schedule(section)
        val stored = readStored(section) ?: return freshProgress(instant, schedule)
        val relocated = stored.period.relocated(schedule, instant)
        if (relocated.isExpired(instant)) return freshProgress(instant, schedule)
        return RestoredProgress(
            period = relocated,
            counts = stored.counts,
            completionShown = stored.completionShown,
            needsSave = relocated != stored.period,
        )
    }

    private fun freshProgress(instant: Instant, schedule: AzkarResetSchedule) = RestoredProgress(
        period = AzkarPeriod.startingAt(instant, schedule),
        counts = emptyMap(),
        completionShown = false,
        needsSave = true,
    )

    /** Сохранённый прогресс раздела; нет или не читается (JSON, неизвестный пояс) — `null`. */
    private fun readStored(section: AzkarSection): RestoredProgress? {
        val text = storage.snapshot[progressKey(section)] ?: return null
        return try {
            val stored = json.decodeFromString(StoredProgress.serializer(), text)
            RestoredProgress(
                period = stored.period.toPeriod(),
                counts = stored.counts,
                completionShown = stored.completionShown,
                needsSave = false,
            )
        } catch (error: IllegalArgumentException) {
            // SerializationException — подкласс IllegalArgumentException.
            onUnreadableProgress(section, error)
            null
        } catch (error: DateTimeException) {
            onUnreadableProgress(section, error)
            null
        }
    }

    private fun save(section: AzkarSection) {
        val period = periods[section] ?: return
        val stored = StoredProgress(
            period = StoredPeriod.from(period),
            counts = sessions(section).filter { it.count > 0 }.associate { it.id.number to it.count },
            completionShown = section in acknowledgedCompletions,
        )
        val text = json.encodeToString(StoredProgress.serializer(), stored)
        storage.edit { it[progressKey(section)] = text }
    }

    @Serializable
    private data class StoredProgress(
        val period: StoredPeriod,
        val counts: Map<Int, Int> = emptyMap(),
        val completionShown: Boolean = false,
    )

    /** Время — миллисекунды эпохи UTC; пояс — идентификатор (`Europe/Moscow`). */
    @Serializable
    private data class StoredPeriod(val scheduledReset: Long, val validUntil: Long, val timeZone: String) {
        /** Неизвестный пояс — [DateTimeException]. */
        fun toPeriod() =
            AzkarPeriod(Instant.ofEpochMilli(scheduledReset), Instant.ofEpochMilli(validUntil), ZoneId.of(timeZone))

        companion object {
            fun from(period: AzkarPeriod) = StoredPeriod(
                scheduledReset = period.scheduledReset.toEpochMilli(),
                validUntil = period.validUntil.toEpochMilli(),
                timeZone = period.timeZone.id,
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun progressKey(section: AzkarSection) = stringPreferencesKey("azkar.progress.${section.key}")
    }
}
