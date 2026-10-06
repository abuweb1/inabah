package app.inabah.android.core.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Настоящий `PackageManager`: алиасы из манифеста переключаются, ярлык запуска есть всегда. */
@RunWith(AndroidJUnit4::class)
class PackageManagerAppIconSwitcherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val switcher = PackageManagerAppIconSwitcher(context)

    @After
    fun restoreClassic() {
        switcher.select(AppIconOption.Classic)
    }

    @Test
    fun selectEnablesExactlyOneAliasAndKeepsLaunchIntent() {
        for (option in listOf(AppIconOption.Niche, AppIconOption.Dawn, AppIconOption.Classic)) {
            switcher.select(option)

            assertEquals(option, switcher.current())
            assertEquals(listOf(option), enabledAliases())
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            assertNotNull(launch)
            assertEquals("app.inabah.android.app.${option.aliasName}", launch?.component?.className)
        }
    }

    private fun enabledAliases() = AppIconOption.entries.filter { option ->
        val component = ComponentName(context.packageName, "app.inabah.android.app.${option.aliasName}")
        when (context.packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> option == AppIconOption.Classic
            else -> false
        }
    }
}
