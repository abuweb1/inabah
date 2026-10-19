package app.inabah.android.feature.makharij

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import app.inabah.android.core.designsystem.components.ArabicText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import app.inabah.android.core.designsystem.components.ScheherazadeNew
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import app.inabah.android.R
import app.inabah.android.core.content.model.LetterForm
import app.inabah.android.core.content.model.ReadingStep
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Palette
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.FixedTextSize
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.designsystem.components.surface

// Цвета «Учимся читать» - производные от цвета раздела (`tabMakharij`), чтобы следовать палитре
// (наброски docs/design/makharij-audio-themes.html: фон, рамки, подсветка и кнопки - от темы,
// пергамент - от «Фона арабского текста»). HEX здесь нет.

/** Цвета раздела, посчитанные один раз от палитры. */
@Immutable
data class ReadingColors(
    /** Акцент раздела: номера, рамка текущего шага, звучащий элемент. */
    val tint: Color,
    /** Текст на заливке [tint] (кнопки). */
    val onTint: Color,
    /** Плитки и карточки на фоне раздела. */
    val tile: Color,
    val tileBorder: Color,
    /** Выделение: текущий шаг, выбранная буква. */
    val highlight: Color,
    val highlightBorder: Color,
    /** Карточка «Учимся читать» на главной. */
    val card: Brush,
    /** Лист буквы: тёмный оттенок цвета раздела (набросок - карточка темы с чёрным). */
    val sheet: Color,
    /** Буква в слове-примере на пергаменте (формы букв). */
    val letterHighlight: Color,
    /** Ячейка на пергаменте (слог), её рамка и подпись под слогом. */
    val parchmentCell: Color,
    val parchmentCellBorder: Color,
    val parchmentCaption: Color,
) {
    companion object {
        fun from(palette: Palette): ReadingColors {
            val tint = palette.tabMakharij
            return ReadingColors(
                tint = tint,
                onTint = lerp(tint, Color.Black, ON_TINT_SHADE),
                tile = tint.copy(alpha = TILE_ALPHA),
                tileBorder = tint.copy(alpha = TILE_BORDER_ALPHA),
                highlight = tint.copy(alpha = HIGHLIGHT_ALPHA),
                highlightBorder = tint,
                card = Brush.linearGradient(
                    listOf(lerp(tint, Color.Black, CARD_SHADE_START), lerp(tint, Color.Black, CARD_SHADE_END)),
                ),
                sheet = lerp(Color.Black, tint, SHEET_TINT),
                // Свой цвет у каждого фона арабского текста (рамка на «Сепии» и «Дымчатом» сливалась с текстом).
                letterHighlight = palette.parchmentMark,
                // Ячейка темнее пергамента и с рамкой: светлая ячейка на светлой доске не видна.
                parchmentCell = palette.parchmentText.copy(alpha = PARCHMENT_CELL_ALPHA),
                parchmentCellBorder = palette.parchmentText.copy(alpha = PARCHMENT_CELL_BORDER_ALPHA),
                parchmentCaption = palette.parchmentText.copy(alpha = PARCHMENT_CAPTION_ALPHA),
            )
        }

        private const val ON_TINT_SHADE = 0.85f
        // Плитки отделены от фона и тоном, и рамкой (просьба пользователя 2026-10-15: было почти не видно).
        private const val TILE_ALPHA = 0.1f
        private const val TILE_BORDER_ALPHA = 0.4f
        private const val HIGHLIGHT_ALPHA = 0.16f
        // Белый текст карточки - контраст не ниже 4,5:1 и на светлом конце градиента.
        private const val CARD_SHADE_START = 0.66f
        private const val CARD_SHADE_END = 0.46f
        private const val SHEET_TINT = 0.22f
        private const val PARCHMENT_CELL_ALPHA = 0.06f
        private const val PARCHMENT_CELL_BORDER_ALPHA = 0.18f
        private const val PARCHMENT_CAPTION_ALPHA = 0.75f
    }
}

/** Рамка плиток букв, слогов и знаков - толще волосяной линии. */
val ReadingTileBorder = 1.5.dp

/** Цвета раздела из текущей палитры. */
val readingColors: ReadingColors
    @Composable @ReadOnlyComposable get() = ReadingColors.from(InabahTheme.palette)

/** Транскрипция видна или прозрачна («глаз»): место под ней остаётся - текст не прыгает. */
fun transliterationAlpha(hidden: Boolean): Float = if (hidden) 0f else 1f

/** Глаз в навбаре урока: показать / скрыть транскрипцию. */
@Composable
fun TransliterationToggle(hidden: Boolean, onToggle: () -> Unit) {
    GlassIconButton(
        onClick = onToggle,
        iconRes = if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
        contentDescription = stringResource(
            if (hidden) R.string.makharij_translit_show else R.string.makharij_translit_hide,
        ),
        tint = readingColors.tint,
    )
}

/** Лампочка в навбаре «Читаем слова» (страницы без транскрипции): показать её на миг. */
@Composable
fun TransliterationHint(onClick: () -> Unit) {
    GlassIconButton(
        onClick = onClick,
        iconRes = R.drawable.ic_lightbulb,
        contentDescription = stringResource(R.string.makharij_words_hint),
        tint = readingColors.tint,
    )
}

/** Главная кнопка раздела: заливка цветом раздела, тёмный текст (набросок, `.btn`). */
@Composable
fun ReadingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = FixedTextSize {
    val colors = readingColors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressFeedback(interaction, scale = PressFeedback.WIDE_SCALE, opacity = PressFeedback.WIDE_OPACITY)
            .defaultMinSize(minHeight = Size.primaryButtonHeight)
            .surface(colors.tint, Radius.box)
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = colors.onTint,
            textAlign = TextAlign.Center,
            style = InabahType.body.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Второе действие: контур цветом раздела. */
@Composable
fun ReadingOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = FixedTextSize {
    val colors = readingColors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .pressFeedback(interaction, scale = PressFeedback.WIDE_SCALE, opacity = PressFeedback.WIDE_OPACITY)
            .defaultMinSize(minHeight = Size.primaryButtonHeight)
            .border(Size.hairline, colors.tint, RoundedCornerShape(Radius.box))
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = colors.tint,
            textAlign = TextAlign.Center,
            style = InabahType.body.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * Пергамент под одной крупной буквой или слогом (лист буквы, вопрос проверки): градиент и рамка
 * [ParchmentPanel] без полей и украшений - буква по центру. Строка арабского шрифта выше кегля
 * (место под огласовки) - текст не обрезается рамкой, а выходит за неё невидимой частью строки.
 */
@Composable
fun ParchmentTile(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Radius.card,
) {
    val palette = InabahTheme.palette
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .background(InabahTheme.gradients.parchment, shape)
            .border(Size.parchmentBorder, palette.parchmentAccent, shape),
        contentAlignment = Alignment.Center,
    ) {
        ArabicText(
            text,
            size,
            palette.parchmentText,
            Modifier.wrapContentSize(unbounded = true),
            bold = true,
            maxLines = 1,
        )
    }
}


/**
 * Русский текст с арабскими вставками («А 4 (ط ظ ض ص) из них…»): каждая арабская часть изолирована
 * знаками направления (U+2067 … U+2069) - скобки и порядок слов вокруг не переворачиваются - и набрана
 * Scheherazade чуть крупнее (в Inter арабского нет - иначе мелкий запасной шрифт).
 */
fun withArabicRuns(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    for (match in ArabicRun.findAll(text)) {
        append(text.substring(index, match.range.first))
        append(RIGHT_TO_LEFT_ISOLATE)
        withStyle(SpanStyle(fontFamily = ScheherazadeNew, fontSize = ARABIC_RUN_SCALE.em)) { append(match.value) }
        append(POP_DIRECTIONAL_ISOLATE)
        index = match.range.last + 1
    }
    append(text.substring(index))
}

/** Арабские буквы и знаки, с пробелами между ними (но не по краям). */
private val ArabicRun = Regex("""[؀-ۿ]+(?:\s+[؀-ۿ]+)*""")
private const val RIGHT_TO_LEFT_ISOLATE = "\u2067"
private const val POP_DIRECTIONAL_ISOLATE = "\u2069"
private const val ARABIC_RUN_SCALE = 1.3f

/** Подпись столбца таблицы форм. */
val LetterForm.column: Int
    get() = when (this) {
        LetterForm.Isolated -> R.string.makharij_form_isolated
        LetterForm.Initial -> R.string.makharij_form_initial
        LetterForm.Medial -> R.string.makharij_form_medial
        LetterForm.Final -> R.string.makharij_form_final
    }

/** Где стоит буква этой формы - для TalkBack и объяснения в проверке. */
val LetterForm.place: Int
    get() = when (this) {
        LetterForm.Isolated -> R.string.makharij_form_isolated_place
        LetterForm.Initial -> R.string.makharij_form_initial_place
        LetterForm.Medial -> R.string.makharij_form_medial_place
        LetterForm.Final -> R.string.makharij_form_final_place
    }

/** «Шаг 4 · Огласовки»; у итога номера нет - «Итог · Читаем зикр». */
@Composable
@ReadOnlyComposable
fun stepTitle(step: ReadingStep): String =
    if (step.isFinal) stringResource(R.string.makharij_final_title, step.title)
    else stringResource(R.string.makharij_step_title, step.number, step.title)

/** Слово, в котором часть [range] (буква со знаками) выделена цветом [color]; нет диапазона - как есть. */
fun highlightedWord(word: String, range: IntRange?, color: Color): AnnotatedString = buildAnnotatedString {
    append(word)
    if (range != null) addStyle(SpanStyle(color = color), range.first, range.last + 1)
}
