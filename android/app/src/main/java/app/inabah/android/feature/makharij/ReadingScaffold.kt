package app.inabah.android.feature.makharij

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingCourse
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.ContentError
import app.inabah.android.core.designsystem.components.InabahTopBar
import kotlinx.coroutines.launch

/**
 * Каркас экранов «Учимся читать»: фон раздела, навбар (прозрачный - поверх фона), загрузка курса
 * («Повторить» при ошибке) и [content] с загруженным курсом.
 */
@Composable
fun ReadingScaffold(
    store: ReadingStore,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (ReadingCourse) -> Unit,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val state by store.course.collectAsStateWithLifecycle()
    // Повторная попытка живёт, пока экран на месте: ушли - загрузка отменяется (стор вернёт Idle).
    val scope = rememberCoroutineScope()
    // Ключ - «не загружен»: загрузку, начатую другим экраном, отменили - начать снова.
    LaunchedEffect(store, state is Loadable.Idle) { store.load() }

    Column(modifier.fillMaxSize().background(theme.gradients.makharijBackground)) {
        InabahTopBar(
            title = title,
            tint = palette.tabMakharij,
            onBack = onBack,
            subtitle = subtitle?.let { { Text(it, color = palette.onAccentSecondary, style = InabahType.caption, maxLines = 1) } },
            actions = actions,
        )
        Box(Modifier.fillMaxSize()) {
            when (val current = state) {
                Loadable.Idle, Loadable.Loading -> CircularProgressIndicator(
                    color = palette.onAccent,
                    modifier = Modifier.align(Alignment.Center),
                )
                is Loadable.Failed -> ContentError(
                    onRetry = { scope.launch { store.load() } },
                    modifier = Modifier.align(Alignment.Center),
                )
                is Loadable.Loaded -> content(current.value)
            }
        }
    }
}
