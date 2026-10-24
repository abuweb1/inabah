package app.inabah.android.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Анимации не отключены в настройках телефона («Удалить анимацию» / масштаб анимации 0). Перепроверяется
 * при возврате в приложение. Бесконечные украшения (блик «ищем» на кольце Къиблы) без анимаций стоят на месте.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    LifecycleResumeEffect(Unit) {
        enabled = ValueAnimator.areAnimatorsEnabled()
        onPauseOrDispose {}
    }
    return enabled
}
