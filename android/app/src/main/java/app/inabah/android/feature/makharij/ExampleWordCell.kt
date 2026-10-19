package app.inabah.android.feature.makharij

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
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
) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val hasAudio = audioFile in clips
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val listen = stringResource(R.string.makharij_word_listen)
    val action: (() -> Unit)? = if (hasAudio) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onPlay(audioFile)
        }
    } else {
        onClickWithoutAudio
    }
    Box(
        modifier
            .then(
                if (action != null) {
                    Modifier
                        .clickable(interaction, indication = null, role = Role.Button, onClick = action)
                        .pressFeedback(interaction, scale = PressFeedback.ICON_SCALE)
                } else {
                    Modifier
                },
            )
            .surface(colors.parchmentCell, Radius.box, border = colors.parchmentCellBorder)
            .padding(top = Spacing.xs, bottom = Spacing.xxs)
            .clearAndSetSemantics {
                contentDescription = word
                if (action != null) onClick(if (hasAudio) listen else null) { action(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        ArabicText(
            highlightedWord(word, letterRange(word, letter.letter, form), colors.letterHighlight),
            size,
            palette.parchmentText,
            bold = true,
            maxLines = 1,
        )
        if (hasAudio) {
            Icon(
                painterResource(R.drawable.ic_volume_up),
                contentDescription = null,
                tint = colors.parchmentCaption,
                modifier = Modifier.align(Alignment.TopStart).padding(start = Spacing.xxs).size(SpeakerSize),
            )
        }
    }
}

/** Значок 🔈 «есть запись» - у слова-примера и у плитки буквы. */
internal val SpeakerSize = 12.dp
