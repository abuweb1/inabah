package app.inabah.android.core.content

import app.inabah.android.core.content.dto.CollectionInfoDto
import app.inabah.android.core.content.dto.HadithDto
import app.inabah.android.core.content.dto.ZikrDto
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.CollectionAuthor
import app.inabah.android.core.content.model.CollectionInfo
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import app.inabah.android.core.content.model.HadithNote
import app.inabah.android.core.content.model.HadithTranslation
import app.inabah.android.core.content.model.Zikr
import app.inabah.android.core.content.model.ZikrId
import app.inabah.android.core.content.model.ZikrTranslation

// DTO → доменные модели (iOS ContentMapping): перевод выбирается по приоритету языков
// для каждой записи отдельно.

internal fun ZikrDto.toZikr(section: AzkarSection, priority: ContentLanguagePriority): Zikr {
    val selected = priority.select(translations.keyedByLanguage())
    return Zikr(
        id = ZikrId(section, id),
        arabic = arabic,
        repetitions = max.coerceAtLeast(1),
        audioFileName = audio,
        optional = optional,
        quran = quran,
        translation = selected?.let { (language, dto) ->
            ZikrTranslation(
                language = language,
                text = dto.text,
                transliteration = dto.translit.nonBlankOrNull(),
                source = dto.source.nonBlankOrNull(),
                narrator = dto.rawi.nonBlankOrNull(),
            )
        },
    )
}

internal fun HadithDto.toHadith(collection: HadithCollection, priority: ContentLanguagePriority): Hadith {
    val selected = priority.select(translations.keyedByLanguage())
    return Hadith(
        id = HadithId(collection, id),
        arabic = arabic,
        translation = selected?.let { (language, dto) ->
            HadithTranslation(
                language = language,
                narrator = dto.rawi.nonBlankOrNull(),
                text = dto.text,
                source = dto.source.nonBlankOrNull(),
                notes = dto.notes.mapNotNull { note ->
                    val term = note.term.nonBlankOrNull() ?: return@mapNotNull null
                    note.text.nonBlankOrNull()?.let { HadithNote(term, it) }
                },
            )
        },
    )
}

/** Составитель с пустым именем — заготовка, не показывается; пустые годы и абзацы отбрасываются. */
internal fun CollectionInfoDto.toCollectionInfo(priority: ContentLanguagePriority): CollectionInfo {
    val (language, dto) = priority.select(translations.keyedByLanguage()) ?: return CollectionInfo.Empty
    return CollectionInfo(
        language = language,
        authors = dto.authors.mapNotNull { author ->
            author.name.nonBlankOrNull()?.let { CollectionAuthor(name = it, years = author.years.nonBlankOrNull()) }
        },
        about = dto.about.mapNotNull { it.nonBlankOrNull() },
    )
}

/** Ключи JSON → языки; при совпадении после приведения к нижнему регистру — первый по порядку файла. */
internal fun <T> Map<String, T>.keyedByLanguage(): Map<ContentLanguage, T> {
    val result = LinkedHashMap<ContentLanguage, T>(size)
    for ((code, value) in this) result.putIfAbsent(ContentLanguage.of(code), value)
    return result
}

/** Обрезать пробелы и переводы строк; пустая строка → `null`. */
private fun String?.nonBlankOrNull(): String? = this?.trim()?.ifEmpty { null }
