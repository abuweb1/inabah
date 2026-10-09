package app.inabah.android.feature.hadith

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.content.model.HadithNote
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Motion
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.collapsible
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.monospacedDigits

/** Стрелка сносок — как значки действий хадиса: в sp, растёт с шагом интерфейса. */
private const val CHEVRON_SIZE = 20f
private const val HALF_TURN = 180f

/** Скругление правых углов подложки сноски — как у пузыря перевода азкаров. */
private val BubbleRadius = 8.dp

private val NonWord = Regex("""[^\p{L}\p{N}]+""")

/**
 * Слово сноски взято из перевода — показывается в кавычках; иначе это пояснение ко всему хадису
 * («Различия передач») — без кавычек. Сравнение — как в `check-data`: без регистра, «ё», знаков.
 */
internal fun isQuotedTerm(term: String, translation: String): Boolean {
    fun fold(text: String) = text.lowercase().replace('ё', 'е').replace(NonWord, " ").trim()
    val folded = fold(term)
    return folded.isNotEmpty() && " ${fold(translation)} ".contains(" $folded ")
}

/**
 * Сноски к переводу (решения пользователя 2026-10-10, `docs/06-data-structure.md`): справа «N ⌄»,
 * по нажатию плавно раскрывается список — пузыри в стиле перевода азкаров (полоса слева, курсив), но
 * в тон раздела хадисов (`noteStripe` / `noteFill` / `noteText`). Состояние — у страницы ([expanded]).
 */
@Composable
internal fun HadithNotes(
    notes: List<HadithNote>,
    translation: String,
    locale: LocaleList,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        HadithNotesToggle(notes.size, expanded, onToggle)
        // Свёрнутое не занимает места и не читается TalkBack (`collapsible`).
        Column(
            Modifier.collapsible(expanded).padding(top = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            notes.forEach { HadithNoteBubble(it, quoted = isQuotedTerm(it.term, translation), locale) }
        }
    }
}

@Composable
private fun HadithNotesToggle(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val palette = InabahTheme.palette
    val chevronSize = with(LocalDensity.current) { CHEVRON_SIZE.sp.toDp() }
    val rotation by animateFloatAsState(if (expanded) HALF_TURN else 0f, Motion.collapse(), label = "notesChevron")
    val interaction = remember { MutableInteractionSource() }
    val state = stringResource(if (expanded) R.string.hadith_notes_expanded else R.string.hadith_notes_collapsed)
    // На экране — только число и стрелка (решение пользователя 2026-10-10); для TalkBack — «Сноски: N».
    val label = stringResource(R.string.hadith_notes_toggle, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Size.minTapTarget)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onToggle)
            .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY)
            .semantics {
                contentDescription = label
                stateDescription = state
            },
        verticalAlignment = Alignment.CenterVertically,
        // Справа (решение пользователя 2026-10-10); нажимается вся строка.
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs, Alignment.End),
    ) {
        Text(
            count.toString(),
            color = palette.onAccentSecondary,
            style = InabahType.contentNote.copy(fontWeight = FontWeight.SemiBold).monospacedDigits(),
            // Число уже в подписи строки — отдельно не читается.
            modifier = Modifier.clearAndSetSemantics {},
        )
        Icon(
            painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            tint = palette.onAccentSecondary,
            modifier = Modifier.size(chevronSize).graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * Слово полужирным (из перевода — в кавычках, [quoted]), под ним пояснение — курсив цветом сносок;
 * язык — язык перевода (переносы).
 */
@Composable
private fun HadithNoteBubble(note: HadithNote, quoted: Boolean, locale: LocaleList) {
    val palette = InabahTheme.palette
    val stripeColor = palette.noteStripe
    val style = InabahType.contentTransliteration.copy(localeList = locale)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.noteFill, RoundedCornerShape(topEnd = BubbleRadius, bottomEnd = BubbleRadius))
            .drawBehind {
                val stripe = Size.accentStripe.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - stripe
                drawRect(stripeColor, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(stripe, size.height))
            }
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        val term = if (quoted) stringResource(R.string.hadith_note_quote, note.term) else note.term
        Text(term, color = palette.noteText, style = style.copy(fontWeight = FontWeight.SemiBold))
        Text(note.text, color = palette.noteText, style = style)
    }
}
