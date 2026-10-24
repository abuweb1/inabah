package app.inabah.android.core.designsystem.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.ShadowToken
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import kotlinx.coroutines.launch

/** Затемнение под панелью и порог смахивания - как у листа буквы «Учимся читать». */
private const val SCRIM_ALPHA = 0.45f
private val DISMISS_DISTANCE = 96.dp

/** Доля высоты экрана под панель: длинный текст прокручивается внутри. */
private const val MAX_HEIGHT_FRACTION = 0.85f

/**
 * Панель снизу поверх экрана (справка «Как повысить точность»): затемнение, выезд снизу, закрывается
 * смахиванием вниз, нажатием на затемнение и «Назад». Содержимое прокручивается; [contentPadding] -
 * отступ снизу от панели вкладок. [title] - заголовок панели для TalkBack.
 */
@Composable
fun BoxScope.SlideUpSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    contentPadding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = InabahTheme.palette
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(Motion.collapse()), exit = fadeOut(Motion.collapse())) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.shadow.copy(alpha = SCRIM_ALPHA))
                // Для TalkBack затемнение не элемент: закрыть - кнопкой панели или «Назад».
                .clearAndSetSemantics {}
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Motion.collapse()) { it },
        exit = slideOutVertically(Motion.collapse()) { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val offset = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val dismissDistance = with(LocalDensity.current) { DISMISS_DISTANCE.toPx() }
        val maxHeight = with(LocalDensity.current) {
            (LocalWindowInfo.current.containerSize.height * MAX_HEIGHT_FRACTION).toDp()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .graphicsLayer { translationY = offset.value }
                .surface(
                    SolidColor(palette.background),
                    RoundedCornerShape(topStart = Radius.panel, topEnd = Radius.panel),
                    border = palette.hairline,
                    shadow = ShadowToken.floating(palette),
                )
                .semantics {
                    isTraversalGroup = true
                    paneTitle = title
                }
                // Касания внутри панели не доходят до затемнения.
                .pointerInput(Unit) { detectTapGestures { } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > dismissDistance) onDismiss() else offset.animateTo(0f, Motion.collapse())
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, Motion.collapse()) } },
                    ) { _, delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) } }
                }
                .padding(top = Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(Size.grabberWidth, Size.grabberHeight)
                    .background(palette.track, RoundedCornerShape(Size.grabberHeight)),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.xxl)
                    .padding(top = Spacing.l, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                content = content,
            )
        }
    }
}
