package app.inabah.android.feature.azkar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.Radius
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.AnchoredPopover
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.PopoverPlacement
import app.inabah.android.core.designsystem.components.surface
import app.inabah.android.core.settings.AzkarDay
import app.inabah.android.core.settings.AzkarDayRecord
import app.inabah.android.core.settings.AzkarDayStatus
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarStreak
import app.inabah.android.core.settings.azkarStreak
import app.inabah.android.core.settings.status
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Ячейки сетки месяца [month] с недели, начинающейся с [firstDayOfWeek]: `null` — пустые места до 1-го
 * числа и после последнего, до конца недели. Всегда кратно 7.
 */
internal fun monthCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstDayOfWeek.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
    val days = (1..month.lengthOfMonth()).map(month::atDay)
    val trailing = (DAYS_IN_WEEK - (leading + days.size) % DAYS_IN_WEEK) % DAYS_IN_WEEK
    return List(leading) { null } + days + List(trailing) { null }
}

/** Месяцы календаря: от месяца первой записи истории (или [today], если она пуста) до месяца [today]. */
internal fun calendarMonths(days: Map<LocalDate, AzkarDay>, today: LocalDate): List<YearMonth> {
    val current = YearMonth.from(today)
    val first = days.keys.minOrNull()?.let(YearMonth::from)?.takeIf { it < current } ?: current
    return List(ChronoUnit.MONTHS.between(first, current).toInt() + 1) { first.plusMonths(it.toLong()) }
}

/**
 * «Календарь азкаров» (решения пользователя 2026-10-11, `docs/analysis/2026-10-11-azkar-streak-calendar.md`):
 * сводка «подряд / лучшая серия / всего дней», месяц сеткой с листанием до текущего; день — круг из
 * половинок «утро / вечер»; нажатие на день — подробности над ним. «Сегодня» — `AzkarStore.streakDay`.
 */
@Composable
fun AzkarCalendarScreen(
    history: AzkarHistory,
    store: AzkarStore,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val days by history.days.collectAsStateWithLifecycle()
    val streakDay by store.streakDay.collectAsStateWithLifecycle()
    // Вечерние грузятся при старте приложения; до этого «сегодня» — по часам устройства.
    val today = streakDay ?: LocalDate.now()
    AzkarCalendarContent(days, today, onBack, contentPadding, modifier)
}

@Composable
internal fun AzkarCalendarContent(
    days: Map<LocalDate, AzkarDay>,
    today: LocalDate,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val palette = theme.palette
    val locale = LocalConfiguration.current.locales[0]
    val streak = remember(days, today) { azkarStreak(days, today) }
    val months = remember(days, today) { calendarMonths(days, today) }

    Column(modifier.fillMaxSize().background(theme.gradients.azkarBackground)) {
        InabahTopBar(
            title = stringResource(R.string.azkar_calendar_title),
            tint = palette.tabAzkar,
            onBack = onBack,
            background = palette.header,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.xl, bottom = Spacing.xl + contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            StreakSummary(streak)
            MonthPager(months, days, today, locale)
            Legend()
            if (streak.fullDays == 0) {
                Text(
                    stringResource(R.string.azkar_calendar_empty_hint),
                    color = palette.onAccentSecondary,
                    style = InabahType.footnote,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Три числа в ряд: подряд, лучшая серия, всего полных дней. */
@Composable
private fun StreakSummary(streak: AzkarStreak) {
    val palette = InabahTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .surface(palette.card, Radius.card, border = palette.hairline)
            .padding(vertical = Spacing.xl),
    ) {
        listOf(
            streak.current to R.string.azkar_calendar_current,
            streak.best to R.string.azkar_calendar_best,
            streak.fullDays to R.string.azkar_calendar_total,
        ).forEach { (value, label) ->
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(streakNumberText(value), color = palette.onAccent, style = InabahType.title2)
                Text(stringResource(label), color = palette.onAccentSecondary, style = InabahType.caption, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Заголовок «Октябрь 2026» с ‹ › и месяцы свайпом; открывается на текущем, в будущее не листается. */
@Composable
private fun MonthPager(months: List<YearMonth>, days: Map<LocalDate, AzkarDay>, today: LocalDate, locale: Locale) {
    val palette = InabahTheme.palette
    val pagerState = rememberPagerState(initialPage = months.lastIndex) { months.size }
    val scope = rememberCoroutineScope()
    val titleFormatter = remember(locale) { DateTimeFormatter.ofPattern(MONTH_TITLE_PATTERN, locale) }
    val month = months[pagerState.currentPage.coerceIn(months.indices)]
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                iconRes = R.drawable.ic_chevron_left,
                contentDescription = stringResource(R.string.azkar_calendar_previous),
                tint = palette.tabAzkar,
                enabled = pagerState.currentPage > 0,
            )
            Text(
                month.format(titleFormatter).replaceFirstChar { it.titlecase(locale) },
                color = palette.onAccent,
                style = InabahType.headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            GlassIconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                iconRes = R.drawable.ic_chevron_right,
                contentDescription = stringResource(R.string.azkar_calendar_next),
                tint = palette.tabAzkar,
                enabled = pagerState.currentPage < months.lastIndex,
            )
        }
        WeekdayHeader(firstDayOfWeek, locale)
        HorizontalPager(pagerState, verticalAlignment = Alignment.Top) { page ->
            MonthGrid(months[page], firstDayOfWeek, days, today, locale)
        }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, locale: Locale) {
    val palette = InabahTheme.palette
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        repeat(DAYS_IN_WEEK) { offset ->
            Text(
                firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                color = palette.onAccentTertiary,
                style = InabahType.subheadline,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek, days: Map<LocalDate, AzkarDay>, today: LocalDate, locale: Locale) {
    val cells = remember(month, firstDayOfWeek) { monthCells(month, firstDayOfWeek) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern(DAY_PATTERN, locale) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                day = days[date],
                                isToday = date == today,
                                isFuture = date > today,
                                label = date.format(dateFormatter),
                                isSelected = selected == date,
                                onSelect = { selected = date },
                                onDismiss = { selected = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: AzkarDay?,
    isToday: Boolean,
    isFuture: Boolean,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = InabahTheme.palette
    val description = stringResource(
        when (day?.status) {
            AzkarDayStatus.Full -> R.string.azkar_calendar_day_full
            AzkarDayStatus.MorningOnly -> R.string.azkar_calendar_day_morning
            AzkarDayStatus.EveningOnly -> R.string.azkar_calendar_day_evening
            null -> R.string.azkar_calendar_day_none
        },
        label,
    )
    Box {
        Box(
            Modifier
                .size(Size.minTapTarget)
                .then(
                    if (isFuture) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        // Число и круг молчат — TalkBack читает дату с отметкой одной фразой.
                        Modifier
                            .clickable(role = Role.Button, onClick = onSelect)
                            .semantics { contentDescription = description }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!isFuture) DayMark(day, Modifier.size(DAY_CIRCLE_SIZE), isToday = isToday)
            Text(
                date.dayOfMonth.toString(),
                color = if (isFuture) palette.onAccentTertiary else palette.onAccent,
                style = if (isToday) InabahType.callout.copy(fontWeight = FontWeight.Bold) else InabahType.callout,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        AnchoredPopover(isSelected, PopoverPlacement.AboveCenter, palette.tabAzkar, onDismiss, showsArrow = true) {
            DayDetails(label, day)
        }
    }
}

/** Что нарисовать на половине кольца. */
private enum class HalfMark { None, Started, Complete }

private val AzkarDayRecord?.mark: HalfMark
    get() = when {
        this == null -> HalfMark.None
        isComplete -> HalfMark.Complete
        else -> HalfMark.Started
    }

/**
 * Кольцо дня (решения пользователя 2026-10-11, макет «G» и телефон): под дугами — дорожка `track` по всему
 * кругу, левая дуга — утренние (`gold`), правая — вечерние (`newMoon`, 🌑 чёрный); раздел полностью —
 * сплошная дуга, начат — пунктир той же толщины, не читался — только дорожка. Толщина — [RING_FRACTION]
 * размера (9 dp у круга 44 в сетке). [isToday] — лёгкая заливка внутри кольца цветом раздела.
 */
@Composable
internal fun DayMark(day: AzkarDay?, modifier: Modifier = Modifier, isToday: Boolean = false) {
    val palette = InabahTheme.palette
    val morning = day?.morning.mark
    val evening = day?.evening.mark
    val sun = palette.gold
    val moon = palette.newMoon
    val outline = palette.track
    val today = palette.tabAzkar.copy(alpha = TODAY_FILL_ALPHA)
    Canvas(modifier.clearAndSetSemantics {}) {
        val width = size.minDimension * RING_FRACTION
        drawCircle(outline, radius = size.minDimension / 2 - width / 2, style = Stroke(width))
        if (isToday) drawCircle(today, radius = size.minDimension / 2 - width)
        val inset = Offset(width / 2, width / 2)
        val ring = androidx.compose.ui.geometry.Size(size.width - width, size.height - width)
        // Углы — по часовой от «трёх часов»: 90° — низ, 270° — верх; левая дуга — от 90°, правая — от 270°.
        drawHalf(morning, sun, startAngle = 90f, inset, ring, width)
        drawHalf(evening, moon, startAngle = 270f, inset, ring, width)
    }
}

private fun DrawScope.drawHalf(
    mark: HalfMark,
    color: Color,
    startAngle: Float,
    topLeft: Offset,
    ring: androidx.compose.ui.geometry.Size,
    width: Float,
) {
    val effect = when (mark) {
        HalfMark.None -> return
        HalfMark.Complete -> null
        HalfMark.Started -> PathEffect.dashPathEffect(floatArrayOf(width * DASH_ON, width * DASH_OFF))
    }
    drawArc(color, startAngle, HALF_TURN, useCenter = false, topLeft = topLeft, size = ring,
        style = Stroke(width, cap = StrokeCap.Butt, pathEffect = effect))
}

/** Подробности дня над ячейкой: дата и по разделу «16 из 16» или «не читались». */
@Composable
private fun DayDetails(label: String, day: AzkarDay?) {
    val palette = InabahTheme.palette
    Column(Modifier.width(DETAILS_WIDTH).padding(DETAILS_PADDING), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, color = palette.onAccent, style = InabahType.headline, modifier = Modifier.semantics { heading() })
        AzkarSection.entries.forEach { section ->
            val record = day?.record(section)
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(section.icon),
                    contentDescription = null,
                    tint = section.iconColor(LocalInabahTheme.current),
                    modifier = Modifier.size(sizeInSp(DETAILS_ICON_SIZE)),
                )
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(section.title), color = palette.onAccent, style = InabahType.subheadline, modifier = Modifier.weight(1f))
                Text(
                    if (record != null) {
                        stringResource(R.string.azkar_calendar_section_progress, record.completed, record.total)
                    } else {
                        stringResource(R.string.azkar_calendar_section_none)
                    },
                    color = if (record?.isComplete == true) palette.onAccent else palette.onAccentSecondary,
                    style = InabahType.subheadline,
                )
            }
        }
    }
}

/** «● полный день  ◐ утренние  ◑ вечерние». */
@Composable
private fun Legend() {
    val palette = InabahTheme.palette
    val full = AzkarDay(AzkarDayRecord(1, 1), AzkarDayRecord(1, 1))
    val items = listOf(
        full to R.string.azkar_calendar_legend_full,
        AzkarDay(morning = AzkarDayRecord(1, 1)) to R.string.azkar_calendar_legend_morning,
        AzkarDay(evening = AzkarDayRecord(1, 1)) to R.string.azkar_calendar_legend_evening,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterHorizontally)) {
        items.forEach { (day, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                DayMark(day, Modifier.size(sizeInSp(MARK_SIZE)))
                Text(stringResource(label), color = palette.onAccentSecondary, style = InabahType.footnote)
            }
        }
    }
}

/** Значок рядом с текстом интерфейса — в sp: растёт вместе с ним. */
@Composable
private fun sizeInSp(value: Int): Dp = with(LocalDensity.current) { value.sp.toDp() }

private const val DAYS_IN_WEEK = 7
private const val MONTH_TITLE_PATTERN = "LLLL yyyy"
private const val DAY_PATTERN = "d MMMM"
private const val MARK_SIZE = 22
private const val DETAILS_ICON_SIZE = 18
private const val HALF_TURN = 180f
private val DAY_CIRCLE_SIZE = 44.dp

/** Толщина кольца — доля размера круга: 9 dp у круга 44 («ещё толще» — место для числа хватает и на крупном шрифте). */
private const val RING_FRACTION = 9f / 44f
private const val DASH_ON = 0.6f
private const val DASH_OFF = 0.9f
private const val TODAY_FILL_ALPHA = 0.22f
private val DETAILS_WIDTH = 280.dp
private val DETAILS_PADDING = 20.dp
