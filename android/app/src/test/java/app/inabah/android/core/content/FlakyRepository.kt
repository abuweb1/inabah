package app.inabah.android.core.content

import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.Zikr

/** Первые [failures] запросов — «нет файла», дальше — как [delegate]: проверка повторной попытки. */
class FlakyRepository(private val delegate: ContentRepository, private var failures: Int) : ContentRepository {
    override suspend fun azkar(section: AzkarSection): List<Zikr> {
        failIfNeeded("azkar.json")
        return delegate.azkar(section)
    }

    override suspend fun hadiths(collection: HadithCollection): List<Hadith> {
        failIfNeeded("${collection.key}.json")
        return delegate.hadiths(collection)
    }

    private fun failIfNeeded(file: String) {
        if (failures > 0) {
            failures--
            throw ContentError.ResourceMissing(file)
        }
    }
}
