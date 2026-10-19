package app.inabah.android.feature.makharij

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.inabah.android.R
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingLetter
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface

/**
 * Слово-пример формы буквы на пергаменте: буква выделена; есть запись ([audioFile] в [clips]) - в углу 🔊
 * и нажатие просто проигрывает слово (решение пользователя 2026-10-15, без всплывающих окон). Записи нет -
 * нажатие - [onClickWithoutAudio] (в таблице - карточка буквы), `null` - не нажимается.
 *
 * Карточка буквы (просьба пользователя 2026-10-19): под словом - [transliteration] (видна - [showTransliteration],
 * место есть всегда), долгое нажатие - [onZoom] (где ячейка на экране) - увеличить, как в «Читаем слова».
 * [zoomed] - сама увеличенная: подпись крупнее; [contentScale] - поправка масштаба в полёте. Касание по
 * увеличенной без записи проходит к затемнению - она закрывается.
 */
@Composable
fun ExampleWordCell(
    word: String,
    letter: ReadingLetter,
    form: LetterForm,
    audioFile: String,
    clips: Set<String>,
    onPlay: (String) -> Unit,
    size: Float,
    modifier: Modifier = Modifier,
    onClickWithoutAudio: (() -> Unit)? = null,
    transliteration: String? = null,
    showTransliteration: Boolean = false,
    onZoom: ((Rect) -> Unit)? = null,
    zoomed: Boolean = false,
    contentScale: (() -> Offset)? = null,
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val hasAudio = audioFile in clips
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val listen = stringResource(R.string.makharij_word_listen)
    val zoomLabel = stringResource(R.string.makharij_words_zoom_action)
    // Где ячейка - для полёта в центр; хранится без состояния Compose: меняется при каждой раскладке.
    val bounds = remember { arrayOf(Rect.Zero) }
    val action: (() -> Unit)? = if (hasAudio) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onPlay(audioFile)
        }
    } else {
        onClickWithoutAudio
    }
    val zoom = onZoom?.let { callback ->
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            callback(bounds[0])
        }
    }
    val transliterationAlpha by animateFloatAsState(if (showTransliteration) 1f else 0f, label = "exampleTransliteration")
    val description = listOfNotNull(word, transliteration?.takeIf { showTransliteration }).joinToString(", ")
    Box(
        modifier
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .then(
                if (action != null || zoom != null) {
                    Modifier
                        .combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onLongClick = zoom,
                            onClick = { action?.invoke() },
                        )
                        // Касание без действия (нет записи, только увеличение) - ячейка не «нажимается».
                        .then(if (action != null) Modifier.pressFeedback(interaction, scale = PressFeedback.ICON_SCALE) else Modifier)
                } else {
                    Modifier
                },
            )
            .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
            .padding(top = Spacing.xs, bottom = Spacing.xxs)
            .clearAndSetSemantics {
                contentDescription = description
                if (action != null) onClick(if (hasAudio) listen else null) { action(); true }
                if (zoom != null) customActions = listOf(CustomAccessibilityAction(zoomLabel) { zoom(); true })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .contentScale(contentScale, TransformOrigin.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArabicText(
                highlightedWord(word, letterRange(word, letter.letter, form), colors.letterHighlight),
                size,
                palette.parchmentText,
                bold = true,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            if (transliteration != null) TileTransliteration(transliteration, transliterationAlpha, zoomed = zoomed)
        }
        if (hasAudio) {
            Icon(
                painterResource(R.drawable.ic_volume_up),
                contentDescription = null,
                tint = colors.parchmentCaption,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .contentScale(contentScale, TransformOrigin(0f, 0f))
                    .padding(start = Spacing.xxs)
                    .size(SpeakerSize),
            )
        }
    }
}

/** Значок 🔈 «есть запись» - у слова-примера и у плитки буквы. */
internal val SpeakerSize = 12.dp
