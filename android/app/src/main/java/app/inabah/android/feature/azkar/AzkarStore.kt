package app.inabah.android.feature.azkar

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.ContentError
import app.inabah.android.core.content.ContentRepository
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarPeriod
import app.inabah.android.core.settings.AzkarWindowSchedule
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.PreferencesStorage
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
/** [isStarted] — есть хоть одно нажатие: «Сбросить» в настройках активна и при частичном счёте. */
data class SectionProgress(val completed: Int, val total: Int, val isStarted: Boolean = completed > 0) {
    val fraction: Double get() = if (total > 0) completed.toDouble() / total else 0.0
    val isFinished: Boolean get() = total > 0 && completed == total
}

/**
 * Счёт азкаров обоих разделов на всё время работы приложения (iOS `AzkarStore`).
 *
 * Прогресс живёт во **времени азкаров** из [windowSettings] (docs/android/03-domain-logic.md, 3.3):
 * у раздела хранится отрезок — окно или промежуток до него; на каждой границе счётчики обнуляются,
 * вне окна счёт не идёт ни в отметку, ни в оверлей, ни в [history]. Сверка с расписанием —
 * [reconcile] (возврат приложения, смена времени и пояса системы, уход с экрана настроек) и таймер
 * [runBoundaryTimer], который запускает владелец стора. Вызывать с главного потока.
 */
class AzkarStore(
    private val repository: ContentRepository,
    private val storage: PreferencesStorage,
    private val windowSettings: AzkarWindowSettings,
    private val history: AzkarHistory,
    private val now: () -> Instant,
    private val zone: () -> ZoneId,
    /** Сохранённый прогресс испорчен — раздел начинает отрезок заново; сообщить в лог. */
    private val onUnreadableProgress: (AzkarSection, Exception) -> Unit,
) {
    private val sections = AzkarSection.entries.associateWith {
        MutableStateFlow<Loadable<List<ZikrSession>>>(Loadable.Idle)
    }
    private val progressBySection = AzkarSection.entries.associateWith { MutableStateFlow(SectionProgress(0, 0)) }
    private val inWindowBySection = AzkarSection.entries.associateWith { MutableStateFlow(false) }

    // Одни и те же объекты на каждый вызов: Compose собирает поток по ключу-объекту и иначе
    // перезапускал бы сборщик на каждой перерисовке.
    private val stateViews = sections.mapValues { (_, flow) -> flow.asStateFlow() }
    private val progressViews = progressBySection.mapValues { (_, flow) -> flow.asStateFlow() }
    private val inWindowViews = inWindowBySection.mapValues { (_, flow) -> flow.asStateFlow() }

    /** Раздел загружен — у него есть отрезок. */
    private val periods = mutableMapOf<AzkarSection, AzkarPeriod>()

    /** Разделы, за выполнение которых уже показан оверлей «مَا شَاءَ اللَّهُ» в этом окне. */
    private val acknowledgedCompletions = mutableSetOf<AzkarSection>()

    /**
     * Счётчик перепланирований для [runBoundaryTimer]: растёт при каждом, даже если ближайшая граница
     * та же самая, — таймер пересчитывает ожидание от нового «сейчас» (часы могли сдвинуться вперёд).
     */
    private val timerRequests = MutableStateFlow(0L)

    fun state(section: AzkarSection): StateFlow<Loadable<List<ZikrSession>>> = stateViews.getValue(section)

    fun sessions(section: AzkarSection): List<ZikrSession> =
        (sections.getValue(section).value as? Loadable.Loaded)?.value.orEmpty()

    /** Меняется при выполнении или сбросе зикра и при первом нажатии в разделе, а не на каждое нажатие. */
    fun progress(section: AzkarSection): StateFlow<SectionProgress> = progressViews.getValue(section)

    /** Сейчас время азкаров раздела (раздел загружен): прогресс идёт в отметку и историю. Меняется только на границах. */
    fun isInWindow(section: AzkarSection): StateFlow<Boolean> = inWindowViews.getValue(section)

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
        adopt(restored.period, section)
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

    /** Ручной сброс из настроек: загруженный — обнулить и сохранить (отрезок тот же), иначе забыть сохранённое. */
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

    /**
     * Сверяет отрезок каждого загруженного раздела с расписанием на сейчас. Тот же отрезок (окно или
     * промежуток с той же датой) — счёт сохраняется, обновляется только граница; другой — счётчики
     * и пометка оверлея обнуляются.
     *
     * Вызывается таймером на границе, при возврате приложения, при смене системного времени или пояса
     * и при уходе с экрана настроек азкаров — прокрутка колеса времени ничего не стирает, результат
     * зависит только от итогового значения.
     */
    fun reconcile() {
        val instant = now()
        for ((section, loaded) in periods.toMap()) {
            val expected = schedule(section).period(instant)
            if (expected == loaded) continue
            adopt(expected, section)
            if (!expected.isSameSpan(loaded)) {
                sessions(section).forEach(ZikrSession::discardProgress)
                acknowledgedCompletions -= section
                updateProgress(section)
            }
            save(section)
        }
        scheduleNextRefresh()
    }

    /** Показать оверлей завершения — один раз за окно и только во время азкаров. */
    fun shouldPresentCompletion(section: AzkarSection): Boolean =
        inWindowBySection.getValue(section).value &&
            progressBySection.getValue(section).value.isFinished &&
            section !in acknowledgedCompletions

    /** Оверлей показан — запомнить до конца окна (сохраняется: после перезапуска не показывать снова). */
    fun acknowledgeCompletion(section: AzkarSection) {
        if (acknowledgedCompletions.add(section)) save(section)
    }

    /** Раздел снова не выполнен — следующее завершение покажет оверлей снова. */
    fun resetCompletionAcknowledgement(section: AzkarSection) {
        if (acknowledgedCompletions.remove(section)) save(section)
    }

    /**
     * Спит до ближайшей границы среди загруженных разделов и сверяет отрезки ([reconcile]);
     * перепланируется после каждой загрузки и сверки. Работает, пока не отменена вызывающая корутина.
     * Пока процесс спит, не срабатывает — для этого [reconcile] при возврате приложения.
     */
    suspend fun runBoundaryTimer() {
        timerRequests.collectLatest {
            val deadline = periods.values.minOfOrNull { it.validUntil } ?: return@collectLatest
            // Повторная проверка: часы устройства могли сдвинуться назад, пока корутина спала.
            while (true) {
                val wait = Duration.between(now(), deadline)
                if (wait <= Duration.ZERO) break
                delay(wait.toMillis().coerceAtLeast(1))
            }
            reconcile()
        }
    }

    private fun adopt(period: AzkarPeriod, section: AzkarSection) {
        periods[section] = period
        inWindowBySection.getValue(section).value = period.isWindow
    }

    private fun schedule(section: AzkarSection) = AzkarWindowSchedule(windowSettings.window(section), zone())

    private fun countDidChange(section: AzkarSection) {
        updateProgress(section)
        save(section)
    }

    /** Пересчёт выполнения раздела; во время азкаров выполнение уходит в историю (запись — только при изменении). */
    private fun updateProgress(section: AzkarSection) {
        val sessions = sessions(section)
        val progress = SectionProgress(
            completed = sessions.count { it.isCompleted },
            total = sessions.size,
            isStarted = sessions.any { it.count > 0 },
        )
        progressBySection.getValue(section).value = progress
        val period = periods[section]
        if (period != null && period.isWindow && progress.total > 0) {
            history.record(progress.completed, progress.total, section, period.day)
        }
    }

    private fun scheduleNextRefresh() {
        timerRequests.update { it + 1 }
    }

    // Хранение: azkar.progress.<раздел> — JSON с отрезком, счётом зикров (> 0) и пометкой оверлея.

    private class RestoredProgress(
        val period: AzkarPeriod,
        val counts: Map<Int, Int> = emptyMap(),
        val completionShown: Boolean = false,
        /** Отрезок новый или его граница сдвинулась — записать. */
        val needsSave: Boolean = true,
    )

    /** Сохранение того же отрезка — восстановить счёт; нет, не читается или отрезок другой — пустой счёт. */
    private fun restoredProgress(section: AzkarSection): RestoredProgress {
        val expected = schedule(section).period(now())
        val stored = readStored(section)
        if (stored == null || !stored.period.isSameSpan(expected)) return RestoredProgress(expected)
        return RestoredProgress(
            period = expected,
            counts = stored.counts,
            completionShown = stored.completionShown,
            needsSave = stored.period != expected,
        )
    }

    /** Сохранённый прогресс раздела; нет, формат версии 1.0.0 (молча) или испорчен (в лог) — `null`. */
    private fun readStored(section: AzkarSection): RestoredProgress? {
        val text = storage.snapshot[progressKey(section)] ?: return null
        return try {
            val stored = json.decodeFromString(StoredProgress.serializer(), text)
            RestoredProgress(
                period = stored.period.toPeriod() ?: return null,
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

    /**
     * Отрезок: вид (`window` / `gap`), дата начала окна (`yyyy-MM-dd`), граница — миллисекунды эпохи UTC.
     * Без вида и даты — сохранение версии 1.0.0 (`scheduledReset`, `timeZone`): у него другой смысл.
     */
    @Serializable
    private data class StoredPeriod(val kind: String? = null, val day: String? = null, val validUntil: Long) {
        /** Формат 1.0.0 — `null`; неизвестный вид — [IllegalArgumentException], неверная дата — [DateTimeException]. */
        fun toPeriod(): AzkarPeriod? {
            if (kind == null || day == null) return null
            val parsedKind = requireNotNull(AzkarPeriod.Kind.fromKey(kind)) { "Неизвестный вид отрезка: $kind" }
            return AzkarPeriod(parsedKind, LocalDate.parse(day), Instant.ofEpochMilli(validUntil))
        }

        companion object {
            fun from(period: AzkarPeriod) = StoredPeriod(
                kind = period.kind.key,
                day = period.day.toString(),
                validUntil = period.validUntil.toEpochMilli(),
            )
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun progressKey(section: AzkarSection) = stringPreferencesKey("azkar.progress.${section.key}")
    }
}
