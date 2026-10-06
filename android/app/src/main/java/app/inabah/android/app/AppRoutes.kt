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
}

@Serializable
sealed interface HadithRoute {
    @Serializable
    data class CollectionList(val collection: HadithCollection) : HadithRoute

    @Serializable
    data class Detail(val id: HadithId) : HadithRoute
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

    /** Полный текст лицензии шрифта или значков (из «О приложении»). */
    @Serializable
    data class License(val document: LicenseDocument) : SettingsRoute
}
