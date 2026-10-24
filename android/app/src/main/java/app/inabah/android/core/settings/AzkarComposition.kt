package app.inabah.android.core.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Zikr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Выбор пользователя в «Составе азкаров» по разделу: порядок набора и убранные им зикры (номера).
 * `null` у раздела — пользователь ничего не менял, действует набор по умолчанию.
 */
data class StoredComposition(val order: List<Int>, val removed: Set<Int>)

/** Набор по умолчанию — все зикры раздела без `optional`, в порядке данных. */
fun defaultComposition(all: List<Zikr>): List<Int> = all.filterNot { it.optional }.map { it.number }

/**
 * Номера набора по порядку чтения: сохранённый порядок (неизвестные и повторы отброшены) и в конец —
 * зикры без `optional`, появившиеся в данных позже и не убранные пользователем. Пусто — набор по умолчанию.
 */
fun resolveComposition(all: List<Zikr>, stored: StoredComposition?): List<Int> {
    if (stored == null) return defaultComposition(all)
    val known = all.mapTo(HashSet()) { it.number }
    val order = stored.order.filter { it in known }.distinct()
    val added = all.filter { !it.optional && it.number !in order && it.number !in stored.removed }.map { it.number }
    return (order + added).ifEmpty { defaultComposition(all) }
}

/**
 * «Состав азкаров» (2026-10-24, `docs/android/03-domain-logic.md`): какие зикры раздела читать и в каком порядке.
 * Хранится строками номеров через запятую (`azkar.composition.<раздел>` и `….removed`); набор по умолчанию
 * не сохраняется — тогда будущие зикры без `optional` попадают в него сами. Применяет состав [app.inabah.android.feature.azkar.AzkarStore.applyComposition].
 */
class AzkarComposition(private val storage: PreferencesStorage) {
    private val stored = AzkarSection.entries.associateWith { MutableStateFlow(read(it)) }
    private val storedViews = stored.mapValues { (_, flow) -> flow.asStateFlow() }

    fun stored(section: AzkarSection): StateFlow<StoredComposition?> = storedViews.getValue(section)

    fun numbers(section: AzkarSection, all: List<Zikr>): List<Int> = resolveComposition(all, stored.getValue(section).value)

    /** Сохранить набор [numbers] (по порядку); совпал с набором по умолчанию — забыть выбор. Пустой не сохраняется. */
    fun set(section: AzkarSection, numbers: List<Int>, all: List<Zikr>) {
        val known = all.mapTo(HashSet()) { it.number }
        val order = numbers.filter { it in known }.distinct()
        if (order.isEmpty()) return
        val next = if (order == defaultComposition(all)) null else StoredComposition(order, known - order.toSet())
        write(section, next)
    }

    fun restoreDefault(section: AzkarSection) = write(section, null)

    private fun write(section: AzkarSection, next: StoredComposition?) {
        val flow = stored.getValue(section)
        if (flow.value == next) return
        flow.value = next
        storage.edit {
            if (next == null) {
                it.remove(orderKey(section))
                it.remove(removedKey(section))
            } else {
                it[orderKey(section)] = next.order.joinToString(SEPARATOR)
                it[removedKey(section)] = next.removed.sorted().joinToString(SEPARATOR)
            }
        }
    }

    private fun read(section: AzkarSection): StoredComposition? {
        val order = storage.snapshot[orderKey(section)] ?: return null
        return StoredComposition(parse(order), parse(storage.snapshot[removedKey(section)].orEmpty()).toSet())
    }

    private companion object {
        const val SEPARATOR = ","

        fun orderKey(section: AzkarSection) = stringPreferencesKey("azkar.composition.${section.key}")
        fun removedKey(section: AzkarSection) = stringPreferencesKey("azkar.composition.${section.key}.removed")

        /** Испорченные значения пропускаются. */
        fun parse(value: String): List<Int> = value.split(SEPARATOR).mapNotNull { it.trim().toIntOrNull() }
    }
}
