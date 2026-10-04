package app.inabah.android.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.inabah.android.core.designsystem.InabahTheme

class MainActivity : ComponentActivity() {
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
        setContent {
            val services by container.services.collectAsStateWithLifecycle()
            // До первого чтения настроек экран закрыт заставкой — рисовать нечего.
            services?.let { ready ->
                val style by ready.appearanceSettings.style.collectAsStateWithLifecycle()
                InabahTheme(theme = style.theme) {
                    RootScreen(services = ready)
                }
            }
        }
    }
}
