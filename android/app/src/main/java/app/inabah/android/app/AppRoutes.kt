package app.inabah.android.app

import app.inabah.android.core.content.LicenseDocument
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.HadithCollection
import app.inabah.android.core.content.model.HadithId
import kotlinx.serialization.Serializable

// Экраны внутри вкладок (iOS AzkarRoute, HadithRoute, SettingsRoute). @Serializable — чтобы стеки
// пережили пересоздание процесса (этап 3, Navigation 3) и для будущих deep links.

@Serializable
sealed interface AzkarRoute {
    @Serializable
    data class SectionList(val section: AzkarSection) : AzkarRoute

    /** «Календарь азкаров» — из капсулы серии дней на главной. */
    @Serializable
    data object Calendar : AzkarRoute
}

@Serializable
sealed interface HadithRoute {
    @Serializable
    data class CollectionList(val collection: HadithCollection) : HadithRoute

    @Serializable
    data class Detail(val id: HadithId) : HadithRoute

    /** «О сборнике» — из шапки списка сборника. */
    @Serializable
    data class About(val collection: HadithCollection) : HadithRoute

    /** «Избранное» — из кнопки справа вверху главной хадисов. */
    @Serializable
    data object Favorites : HadithRoute
}

/** «Учимся читать» (раздел «Махрадж», 2026-10-15); номер шага - 1…10. */
@Serializable
sealed interface MakharijRoute {
    @Serializable
    data object Steps : MakharijRoute

    @Serializable
    data class Lesson(val step: Int) : MakharijRoute

    @Serializable
    data class Quiz(val step: Int) : MakharijRoute

    /** «Весь алфавит» с огласовками - из урока. */
    @Serializable
    data class Alphabet(val step: Int) : MakharijRoute

    /** «Читаем слова» шага. */
    @Serializable
    data class Words(val step: Int) : MakharijRoute
}

@Serializable
sealed interface SettingsRoute {
    @Serializable
    data object Azkar : SettingsRoute

    @Serializable
    data object Hadith : SettingsRoute

    /** Порядок сборников на главной хадисов (из настроек хадисов). */
    @Serializable
    data object HadithOrder : SettingsRoute

    /** Выбор иконки приложения (этап 9, по решению). */
    @Serializable
    data object AppIcon : SettingsRoute

    /** Выбор палитры оформления. */
    @Serializable
    data object Palette : SettingsRoute

    /** Размер текста: переводы и интерфейс. */
    @Serializable
    data object TextSize : SettingsRoute

    /** «О приложении» — ссылка внизу корня настроек. */
    @Serializable
    data object About : SettingsRoute

    /** «История версий» — все версии из `data/versions.json` (из «О приложении»). */
    @Serializable
    data object VersionHistory : SettingsRoute

    /** Полный текст лицензии шрифта или значков (из «О приложении»). */
    @Serializable
    data class License(val document: LicenseDocument) : SettingsRoute
}
