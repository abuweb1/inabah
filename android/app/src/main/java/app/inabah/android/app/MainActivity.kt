package app.inabah.android.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.inabah.android.core.designsystem.InabahTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Заставку будет держать первое чтение DataStore (setKeepOnScreenCondition) — этап 1.
        installSplashScreen()
        // Только тёмная тема: светлые значки строки состояния и навигации на прозрачном фоне.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            InabahTheme {
                RootScreen()
            }
        }
    }
}
