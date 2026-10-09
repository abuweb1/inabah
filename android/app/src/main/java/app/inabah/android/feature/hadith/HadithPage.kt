package app.inabah.android.feature.hadith

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import app.inabah.android.R
import app.inabah.android.core.content.model.Hadith
import app.inabah.android.core.content.model.HadithTranslation
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.Tracking
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.AudioSoonPlaceholder
import app.inabah.android.core.designsystem.components.BareIconButton
import app.inabah.android.core.designsystem.components.ParchmentPanel
import app.inabah.android.core.designsystem.components.StatusGlyph
import app.inabah.android.core.designsystem.components.StatusGlyphKind
import app.inabah.android.core.designsystem.components.ToggleTile
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.HadithStatus
import app.inabah.android.core.share.shareText
import app.inabah.android.core.share.storeLinksBlock
import kotlin.math.roundToInt

/** Значки «Избранное» и «Поделиться» — как ▶ заглушки аудио (`AudioSoonPlaceholder`): в sp, растут с шагом интерфейса. */
private const val ACTION_ICON_SIZE = 20f

/**
 * Страница экрана хадиса (iOS `HadithPage`): арабский текст на пергаменте, отметки, заглушка аудио,
 * перевод со сносками (скрыты, справа «N ⌄»), закладкой «Избранное» и «Поделиться». Статус, закладка
 * и кегль приходят значениями — отметка другого хадиса страницу не перестраивает. Раскрытые сноски
 * сворачиваются, когда страница перестаёт быть текущей ([isCurrentPage]): пейджер держит соседнюю
 * в композиции.
 */
@Composable
fun HadithPage(
    hadith: Hadith,
    status: HadithStatus,
    isFavorite: Boolean,
    arabicFontSize: Float,
    onToggleRead: () -> Unit,
    onToggleMemorized: () -> Unit,
    onToggleFavorite: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
) {
    var notesExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) notesExpanded = false }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.m)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ArabicPanel(hadith, arabicFontSize)
        HadithStatusButtons(status, onToggleRead, onToggleMemorized)
        AudioSoonPlaceholder(stringResource(R.string.hadith_audio_title, hadith.number))
        val onShare = hadithShareAction(hadith)
        val actions: @Composable (Modifier) -> Unit = { HadithActions(isFavorite, onToggleFavorite, onShare, it) }
        val translation = hadith.translation
        if (translation != null) {
            HadithTranslationCard(translation, notesExpanded, onToggleNotes = { notesExpanded = !notesExpanded }, actions)
        } else {
            // Без перевода — кнопки справа внизу страницы.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { actions(Modifier) }
        }
    }
}

/** «Поделиться» хадисом: подписи — из ресурсов в композиции, текст собирается по нажатию. */
@Composable
private fun hadithShareAction(hadith: Hadith): () -> Unit {
    val context = LocalContext.current
    val header = stringResource(R.string.share_hadith_header, stringResource(hadith.id.collection.title), hadith.number)
    val narrator = hadith.translation?.narrator?.let { stringResource(R.string.hadith_detail_narrator, it) }
    val source = hadith.translation?.source?.let { stringResource(R.string.hadith_detail_source, it) }
    val storeLinks = storeLinksBlock()
    val notesTitle = stringResource(R.string.share_text_hadith_notes)
    val noteLine = stringResource(R.string.share_text_hadith_note_line)
    return {
        val notes = hadithNotesBlock(hadith.translation?.notes.orEmpty(), notesTitle) { noteLine.format(it.term, it.text) }
        context.shareText(hadithShareText(hadith, header, narrator, source, notes, storeLinks))
    }
}

/**
 * Закладка «Избранное» и «Поделиться» — значки без подложки в правом нижнем углу хадиса (решения
 * пользователя 2026-10-06 и 2026-10-10); закладка в избранном — залитая, для TalkBack — переключатель.
 */
@Composable
private fun HadithActions(isFavorite: Boolean, onToggleFavorite: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { ACTION_ICON_SIZE.sp.toDp() }
    // Правый значок — вровень с краем текста; лишняя зона касания уходит в поле карточки.
    Row(modifier.offset(x = (Size.visibleTapTarget - iconSize) / 2)) {
        BareIconButton(
            onClick = onToggleFavorite,
            icon = painterResource(if (isFavorite) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark),
            contentDescription = stringResource(R.string.hadith_favorite),
            foreground = if (isFavorite) palette.gold else palette.onAccentSecondary,
            iconSize = iconSize,
            modifier = Modifier.semantics { toggleableState = ToggleableState(isFavorite) },
        )
        BareIconButton(
            onClick = onShare,
            icon = painterResource(R.drawable.ic_share),
            contentDescription = stringResource(R.string.share_action),
            foreground = palette.onAccentSecondary,
            iconSize = iconSize,
        )
    }
}

/** «ХАДИС 3» и арабский текст одним абзацем. */
@Composable
private fun ArabicPanel(hadith: Hadith, arabicFontSize: Float) {
    val palette = InabahTheme.palette
    ParchmentPanel(Modifier.fillMaxWidth(), topCornerRadius = Radius.card, bottomCornerRadius = Radius.card) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(
                stringResource(R.string.hadith_detail_number, hadith.number).uppercase(),
                color = palette.parchmentText,
                style = InabahType.caption2.copy(fontWeight = FontWeight.Bold, letterSpacing = Tracking.label),
            )
            ArabicText(hadith.arabicDisplayText, arabicFontSize, palette.parchmentText, Modifier.fillMaxWidth())
        }
    }
}

/** «Прочитать / Прочитан» и «Выучить / Выучен»; правило «выучен ⇒ прочитан» — в `HadithProgress`. */
@Composable
private fun HadithStatusButtons(status: HadithStatus, onToggleRead: () -> Unit, onToggleMemorized: () -> Unit) {
    val palette = InabahTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ToggleTile(
            checked = status.isRead,
            onCheckedChange = { onToggleRead() },
            label = stringResource(if (status.isRead) R.string.hadith_status_read else R.string.hadith_status_mark_read),
            accessibilityLabel = stringResource(R.string.hadith_status_read),
            tint = palette.statusRead,
            fill = palette.statusReadTint,
            activeFill = palette.statusReadStrong,
            modifier = Modifier.weight(1f),
        ) { color, size -> StatusGlyph(StatusGlyphKind.Read, color, size, isFilled = status.isRead) }
        ToggleTile(
            checked = status.isMemorized,
            onCheckedChange = { onToggleMemorized() },
            label = stringResource(if (status.isMemorized) R.string.hadith_status_memorized else R.string.hadith_status_memorize),
            accessibilityLabel = stringResource(R.string.hadith_status_memorized),
            tint = palette.statusMemorized,
            fill = palette.statusMemorizedTint,
            activeFill = palette.statusMemorizedStrong,
            modifier = Modifier.weight(1f),
        ) { color, size -> StatusGlyph(StatusGlyphKind.Memorized, color, size, isFilled = status.isMemorized) }
    }
}

/**
 * «Передал: …», перевод и «Приводится: …» — стили переводов (настройка «Размер текста»);
 * под переводом — сноски, справа «N ⌄» (если есть); справа от источника, в правом нижнем углу, —
 * закладка «Избранное» и «Поделиться».
 */
@Composable
private fun HadithTranslationCard(
    translation: HadithTranslation,
    notesExpanded: Boolean,
    onToggleNotes: () -> Unit,
    actions: @Composable (Modifier) -> Unit,
) {
    val palette = InabahTheme.palette
    // Язык перевода — только для его текста (переносы); подписи — на языке интерфейса.
    val locale = LocaleList(translation.language.code)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .surface(palette.subtleFill, Radius.box)
            .padding(vertical = Spacing.l, horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        translation.narrator?.let {
            Text(stringResource(R.string.hadith_detail_narrator, it), color = palette.onAccentSecondary,
                style = InabahType.contentNote.copy(fontWeight = FontWeight.SemiBold))
        }
        Text(translation.text, color = palette.onAccent, style = InabahType.contentTranslation.copy(localeList = locale))
        if (translation.notes.isNotEmpty()) {
            HadithNotes(translation.notes, translation.text, locale, notesExpanded, onToggleNotes)
        }
        val source = translation.source
        if (source == null) {
            // Без источника — линии нет (нечего отделять), кнопки одни справа внизу.
            actions(Modifier.align(Alignment.End))
        } else {
            // Линия над источником, под ней отступ 8 (iOS: overlay сверху у padding(.top)).
            Column {
                Box(Modifier.fillMaxWidth().height(Size.hairline).background(palette.hairline))
                SourceWithActions(stringResource(R.string.hadith_detail_source, source), actions)
            }
        }
    }
}

/**
 * «Приводится: …» и справа закладка с «Поделиться» — значки на уровне последней строки источника
 * (их бывает 3–4), в правом нижнем углу карточки; отступ 8 под линией — как без кнопок.
 */
@Composable
private fun SourceWithActions(text: String, actions: @Composable (Modifier) -> Unit) {
    val style = InabahType.contentNote.copy(fontStyle = FontStyle.Italic)
    // Середина значка — на середине строчных букв последней строки: выше базовой линии на ~треть кегля.
    val baselineToCenter = with(LocalDensity.current) { (style.fontSize.toPx() * X_HEIGHT_CENTER).roundToInt() }
    Row(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        Text(text, color = InabahTheme.palette.onAccentTertiary, style = style,
            modifier = Modifier.weight(1f).alignBy(LastBaseline))
        actions(Modifier.alignBy { it.measuredHeight / 2 + baselineToCenter })
    }
}

/** Середина строчных букв над базовой линией — доля кегля (x-height Inter ≈ 0,55 → половина). */
private const val X_HEIGHT_CENTER = 0.27f
