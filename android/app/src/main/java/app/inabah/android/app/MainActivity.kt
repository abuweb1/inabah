package app.inabah.android.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import app.inabah.android.feature.azkar.azkarSectionOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.animateTheme
import app.inabah.android.core.designsystem.withParchment
import app.inabah.android.core.designsystem.LocalContentTextScale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Язык интерфейса — из поддержанных (сейчас русский), не язык системы: склонение и числа — русские.
        applyOverrideConfiguration(uiLocaleOverride(newBase.resources.configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as InabahApplication).container
        // Заставка держится до первого чтения настроек: иначе мигнули бы значения по умолчанию.
        installSplashScreen().setKeepOnScreenCondition { container.services.value == null }
        // Только тёмная тема: светлые значки строки состояния и навигации на прозрачном фоне.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Пересоздание активности приносит то же намерение — плеер уже открывали.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val services by container.services.collectAsStateWithLifecycle()
            // До первого чтения настроек экран закрыт заставкой — рисовать нечего.
            services?.let { ready ->
                val style by ready.appearanceSettings.style.collectAsStateWithLifecycle()
                val parchment by ready.parchmentSettings.style.collectAsStateWithLifecycle()
                val contentSize by ready.textSizeSettings.content.collectAsStateWithLifecycle()
                // Выбор палитры и фона под арабским текстом — плавно, ко всему приложению сразу (300 мс).
                InabahTheme(theme = animateTheme(style.theme.withParchment(parchment))) {
                    // Системный размер шрифта не влияет ни на что (как в iOS): свои шаги — в настройках.
                    FixedTextSize {
                        CompositionLocalProvider(LocalContentTextScale provides contentSize.scale) {
                            RootScreen(services = ready)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Нажатие на медиауведомление: раздел звучащей записи с открытым плеером. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_PLAYER) return
        val container = (application as InabahApplication).container
        lifecycleScope.launch {
            val services = container.services.filterNotNull().first()
            val player = services.audioPlayer
            player.state.value.track?.id?.let(::azkarSectionOf)?.let { services.router.open(AzkarRoute.SectionList(it)) }
            player.showPanel()
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "app.inabah.android.action.OPEN_PLAYER"
    }
}
