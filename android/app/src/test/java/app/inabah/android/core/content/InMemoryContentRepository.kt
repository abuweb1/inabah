package app.inabah.android.core.content

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.Zikr

/** Контент в памяти для тестов; задан [error] — каждый запрос бросает его. */
class InMemoryContentRepository(
    private val azkar: Map<AzkarSection, List<Zikr>> = emptyMap(),
    private val hadiths: Map<HadithCollection, List<Hadith>> = emptyMap(),
    private val error: ContentError? = null,
) : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> {
        error?.let { throw it }
        return azkar[section].orEmpty()
    }

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> {
        error?.let { throw it }
        return hadiths[collection].orEmpty()
    }
}
