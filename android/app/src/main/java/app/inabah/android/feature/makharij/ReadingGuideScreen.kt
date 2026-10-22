package app.inabah.android.feature.makharij

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.ReadingBlock
import app.inabah.android.core.content.model.ReadingLegendItem
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.surface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Руководство по произношению (решение пользователя 2026-10-21, «Межзубные буквы и дод»): текст с заголовками и
 * рисунки. Рисунки вырезаны без фона (`scripts/cut-reading-images.py`) и лежат на фоне `parchmentIllustration` -
 * подходят к любой теме; подписи и легенда - текст приложения. Экран один на курс: открывается с шага «Буквы»,
 * из листа и карточки буквы ([app.inabah.android.core.content.model.ReadingCourse.guideFor]).
 */
@Composable
fun ReadingGuideScreen(
    guideId: String,
    store: ReadingStore,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Чтение рисунков из ассетов. */
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val guide = (store.course.collectAsStateWithLifecycle().value as? Loadable.Loaded)?.value?.guide(guideId)
    ReadingScaffold(
        store = store,
        title = guide?.title.orEmpty(),
        subtitle = stringResource(R.string.makharij_reading_title),
        onBack = onBack,
        modifier = modifier,
    ) { course ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.s, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            for (block in course.guide(guideId)?.blocks.orEmpty()) {
                when (block) {
                    is ReadingBlock.Text -> GuideText(block)
                    is ReadingBlock.Image -> GuideImage(block, ioDispatcher)
                    // В руководстве только текст и рисунки (check-data).
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun GuideText(block: ReadingBlock.Text) {
    val colors = readingColors
    Column(
        Modifier
            .fillMaxWidth()
            .surface(colors.tile, Radius.card, border = colors.tileBorder)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        block.title?.let { GuideTitle(it) }
        Text(withArabicRuns(block.text), color = InabahTheme.palette.onAccent, style = InabahType.contentTranslation)
    }
}

/** Рисунок на своём фоне, под ним заголовок, подпись и легенда меток. */
@Composable
private fun GuideImage(block: ReadingBlock.Image, ioDispatcher: CoroutineDispatcher) {
    val palette = InabahTheme.palette
    val colors = readingColors
    val assets = LocalContext.current.assets
    val bitmap by produceState<ImageBitmap?>(null, block.image) {
        value = withContext(ioDispatcher) {
            // Файл проверяет check-data; не нашёлся - остаётся пустой фон, а не падение экрана.
            try {
                assets.open("$IMAGES_DIR/${block.image}.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (_: IOException) {
                null
            }
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .surface(colors.tile, Radius.card, border = colors.tileBorder)
            .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        // До загрузки - место по обычной пропорции рисунков, чтобы лента не прыгала сильно.
        val ratio = bitmap?.let { it.width.toFloat() / it.height } ?: PLACEHOLDER_RATIO
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(palette.parchmentIllustration, RoundedCornerShape(Radius.box)),
        ) {
            // Смысл рисунка - в подписи под ним: для TalkBack рисунок не озвучивается.
            bitmap?.let { Image(it, contentDescription = null, Modifier.fillMaxSize().padding(Spacing.xs)) }
        }
        if (block.title != null || block.text != null || block.legend.isNotEmpty()) {
            Column(Modifier.padding(horizontal = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                block.title?.let { GuideTitle(it) }
                block.text?.let { Text(withArabicRuns(it), color = palette.onAccent, style = InabahType.contentTranslation) }
                for (item in block.legend) LegendRow(item)
            }
        }
    }
}

@Composable
private fun GuideTitle(text: String) = Text(
    withArabicRuns(text),
    color = InabahTheme.palette.onAccent,
    style = InabahType.body.copy(fontWeight = FontWeight.SemiBold),
    modifier = Modifier.semantics { heading() },
)

/** Квадрат цвета метки (как на рисунке) и что она значит. */
@Composable
private fun LegendRow(item: ReadingLegendItem) {
    val swatch = with(LocalDensity.current) { LEGEND_SWATCH.sp.toDp() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(
            Modifier
                .size(swatch)
                .background(Color(item.color), RoundedCornerShape(Radius.small))
                .border(Size.hairline, readingColors.tileBorder, RoundedCornerShape(Radius.small)),
        )
        Text(item.text, color = InabahTheme.palette.onAccent, style = InabahType.contentTranslation)
    }
}

private const val IMAGES_DIR = "images/reading"
private const val PLACEHOLDER_RATIO = 1.15f

/** Квадрат легенды - в `sp`, растёт с текстом. */
private const val LEGEND_SWATCH = 18
