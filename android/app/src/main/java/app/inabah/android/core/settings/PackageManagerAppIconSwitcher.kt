package app.inabah.android.core.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Смена иконки через `activity-alias` (docs/android/08, «Смена иконки на Android»): у каждого варианта
 * свой алиас `MainActivity` с фильтром LAUNCHER, включён ровно один. Вызовы `PackageManager` — межпроцессные,
 * поэтому [AppIconSettings] делает их не с главного потока.
 */
class PackageManagerAppIconSwitcher(private val context: Context) : AppIconSwitcher {
    private val packageManager = context.packageManager

    /** Первый включённый алиас; `DEFAULT` — значение из манифеста (там включён только [AppIconOption.Classic]). */
    override fun current(): AppIconOption =
        AppIconOption.entries.firstOrNull { option ->
            when (packageManager.getComponentEnabledSetting(component(option))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> option == AppIconOption.Classic
                else -> false
            }
        } ?: AppIconOption.Classic

    /** Сначала включить новый алиас, затем выключить остальные — ни на миг не остаться без значка в лаунчере. */
    override fun select(option: AppIconOption) {
        setEnabled(option, true)
        AppIconOption.entries.filter { it != option }.forEach { setEnabled(it, false) }
    }

    private fun setEnabled(option: AppIconOption, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(component(option), state, PackageManager.DONT_KILL_APP)
    }

    // Имя класса алиаса — от namespace (пакет кода), пакет компонента — applicationId.
    private fun component(option: AppIconOption) = ComponentName(context.packageName, "$ALIAS_PACKAGE.${option.aliasName}")

    private companion object {
        const val ALIAS_PACKAGE = "app.inabah.android.app"
    }
}
