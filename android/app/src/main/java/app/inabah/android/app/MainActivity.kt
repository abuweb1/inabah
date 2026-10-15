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
import app.inabah.android.core.designsystem.withTranslationShade
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
        // Пересоздание активности приносит то же намерение — экран из уведомления уже открывали.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val services by container.services.collectAsStateWithLifecycle()
            // До первого чтения настроек экран закрыт заставкой — рисовать нечего.
            services?.let { ready ->
                val style by ready.appearanceSettings.style.collectAsStateWithLifecycle()
                val parchment by ready.parchmentSettings.style.collectAsStateWithLifecycle()
                val translationShade by ready.translationShadeSettings.shade.collectAsStateWithLifecycle()
                val contentSize by ready.textSizeSettings.content.collectAsStateWithLifecycle()
                // Выбор палитры, фона под арабским текстом и фона перевода — плавно, ко всему приложению сразу (300 мс).
                InabahTheme(theme = animateTheme(style.theme.withParchment(parchment).withTranslationShade(translationShade))) {
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

    /**
     * Нажатие на уведомление: медиа — раздел звучащей записи с открытым плеером; напоминание азкаров —
     * список раздела, «серия под угрозой» — главная «Азкаров»; хадис дня — этот хадис (поверх списка своего
     * сборника). Цель — [notificationTarget].
     */
    private fun handleIntent(intent: Intent?) {
        val target = intent?.notificationTarget() ?: return
        val container = (application as InabahApplication).container
        lifecycleScope.launch {
            val services = container.services.filterNotNull().first()
            val router = services.router
            when (target) {
                NotificationTarget.Player -> {
                    val player = services.audioPlayer
                    player.state.value.track?.id?.let(::azkarSectionOf)?.let { router.open(AzkarRoute.SectionList(it)) }
                    player.showPanel()
                }
                NotificationTarget.AzkarHome -> router.openRoot(AppTab.Azkar)
                is NotificationTarget.AzkarList -> router.open(AzkarRoute.SectionList(target.section))
                is NotificationTarget.Hadith -> router.open(HadithRoute.Detail(target.id))
            }
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "app.inabah.android.action.OPEN_PLAYER"
        const val ACTION_OPEN_AZKAR = "app.inabah.android.action.OPEN_AZKAR"
        const val ACTION_OPEN_AZKAR_HOME = "app.inabah.android.action.OPEN_AZKAR_HOME"
        const val ACTION_OPEN_HADITH = "app.inabah.android.action.OPEN_HADITH"
        const val EXTRA_SECTION = "section"
        const val EXTRA_COLLECTION = "collection"
        const val EXTRA_NUMBER = "number"
    }
}
