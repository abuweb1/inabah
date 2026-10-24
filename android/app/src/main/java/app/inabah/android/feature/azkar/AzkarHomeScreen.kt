package app.inabah.android.feature.azkar

import kotlinx.coroutines.delay
import app.inabah.android.core.settings.OneTimeHint
import app.inabah.android.core.designsystem.components.HintBubble
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.R
import app.inabah.android.core.content.Loadable
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.FeaturedVerse
import app.inabah.android.core.designsystem.components.GlassIconButton
import app.inabah.android.core.designsystem.components.NavCardRing
import app.inabah.android.core.designsystem.components.NoticeCard
import app.inabah.android.core.designsystem.components.PrimaryButton
import app.inabah.android.core.designsystem.components.SectionBrand
import app.inabah.android.core.designsystem.components.SectionHomeLayout
import app.inabah.android.core.designsystem.components.SectionNavCard
import app.inabah.android.core.designsystem.components.TextButton
import app.inabah.android.core.reminders.ReminderChannels
import app.inabah.android.core.settings.AzkarHistory
import app.inabah.android.core.settings.AzkarWindowSettings
import app.inabah.android.core.settings.azkarStreak
import app.inabah.android.core.settings.ReminderSettings
import app.inabah.android.core.updates.AvailableUpdate
import app.inabah.android.core.updates.UpdateSource
import app.inabah.android.feature.settings.openSafely
import app.inabah.android.feature.settings.rememberNotificationPermission

// Арабский контент главной — не переводится.
private const val BRAND_ARABIC = "إنابة"
private const val VERSE_ARABIC = "وَأَنِيبُوا إِلَىٰ رَبِّكُمْ وَأَسْلِمُوا لَهُ"

/** Страница приложения в Google Play — по имени пакета. */
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id="
private val UpdateDotSize = 10.dp

/** Кааба в кнопке «Къибла» (решение пользователя 2026-10-24: 28 dp в круге 40). */
private val QiblaIconSize = 28.dp

/** Подсказка у «Къиблы»: через полсекунды после открытия главной, 4 с - как в «Учимся читать». */
private const val QIBLA_HINT_DELAY_MILLIS = 500L
private const val QIBLA_HINT_MILLIS = 4_000L

/** Главная «Азкары» (iOS `AzkarHomeView`): бренд, аят Аз-Зумар 39:54, серия дней, карточки разделов. */
@Composable
fun AzkarHomeScreen(
    store: AzkarStore,
    history: AzkarHistory,
    windowSettings: AzkarWindowSettings,
    reminderSettings: ReminderSettings,
    updateSource: UpdateSource,
    onOpenSection: (AzkarSection) -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenQibla: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** `null` - без подсказки у «Къиблы» (тесты других частей главной). */
    qiblaHint: OneTimeHint? = null,
    /** Поверх открыто окно («Что нового») - подсказка ждёт. */
    hintBlocked: Boolean = false,
) {
    val theme = LocalInabahTheme.current
    // До 1.2.0 в релизе обновления «нет» всегда (NoUpdateSource) — значка не видно.
    val update by produceState<AvailableUpdate?>(null, updateSource) { value = updateSource.availableUpdate() }
    // Подсказка у кнопки «Къибла» - один раз, как в «Учимся читать» (решение пользователя 2026-10-24).
    var hostBounds by remember { mutableStateOf(Rect.Zero) }
    var qiblaBounds by remember { mutableStateOf(Rect.Zero) }
    var hintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(qiblaHint, hintBlocked) {
        if (qiblaHint == null || qiblaHint.seen || hintBlocked) return@LaunchedEffect
        delay(QIBLA_HINT_DELAY_MILLIS)
        qiblaHint.markSeen()
        hintVisible = true
        delay(QIBLA_HINT_MILLIS)
        hintVisible = false
    }
    Box(modifier.onGloballyPositioned { hostBounds = it.boundsInRoot() }) {
        SectionHomeLayout(
            background = theme.gradients.azkarBackground,
            brand = SectionBrand(
                arabicName = BRAND_ARABIC,
                tagline = stringResource(R.string.home_brand_tagline),
                latinName = stringResource(R.string.home_brand_latin),
            ),
            verse = FeaturedVerse(
                arabic = VERSE_ARABIC,
                translation = stringResource(R.string.home_verse_translation),
                reference = stringResource(R.string.home_verse_reference),
            ),
            contentPadding = contentPadding,
            leadingAction = update?.let { available -> { UpdateButton(available) } },
            // «Къибла» (1.3.0, набросок docs/design/qibla-entry.html): Кааба в своих цветах, стекло - цвета раздела.
            topAction = {
                GlassIconButton(
                    onClick = {
                        hintVisible = false
                        qiblaHint?.markSeen()
                        onOpenQibla()
                    },
                    iconRes = R.drawable.qibla_kaaba_icon,
                    contentDescription = stringResource(R.string.qibla_title),
                    tint = theme.palette.tabAzkar,
                    iconSize = QiblaIconSize,
                    tinted = false,
                    modifier = Modifier.onGloballyPositioned { qiblaBounds = it.boundsInRoot() },
                )
            },
        ) {
            val today by store.streakDay.collectAsStateWithLifecycle()
            val days by history.days.collectAsStateWithLifecycle()
            // До загрузки вечерних «сегодня» неизвестно — капсулы нет, чтобы не мелькнул ноль.
            today?.let { day ->
                val streak = remember(days, day) { azkarStreak(days, day) }
                AzkarStreakCapsule(streak.current, onClick = onOpenCalendar, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            AzkarSection.entries.forEach { section ->
                AzkarSectionCard(store, windowSettings, section, onClick = { onOpenSection(section) })
            }
            val offerAnswered by reminderSettings.offerAnswered.collectAsStateWithLifecycle()
            if (!offerAnswered) ReminderOfferCard(reminderSettings)
        }
        AnimatedVisibility(hintVisible, enter = fadeIn(), exit = fadeOut()) {
            HintBubble(stringResource(R.string.azkar_qibla_hint), qiblaBounds, hostBounds, theme.palette.tabAzkar, theme.palette.background, below = true)
        }
    }
}

/**
 * Один раз — «Напоминать об азкарах?» (решение пользователя 2026-10-10): «Включить» — разрешение и
 * напоминания в начале утреннего и вечернего времени; любой ответ — карточка больше не показывается.
 */
@Composable
private fun ReminderOfferCard(settings: ReminderSettings) {
    // С каналом «Азкары»: выключен — «Включить» не включает напоминания, которые не придут.
    val permission = rememberNotificationPermission(ReminderChannels.AZKAR)
    NoticeCard(
        icon = R.drawable.ic_schedule,
        title = stringResource(R.string.reminders_offer_title),
        message = stringResource(R.string.reminders_offer_message),
    ) {
        PrimaryButton(
            onClick = {
                permission.request { granted ->
                    if (granted) AzkarSection.entries.forEach { settings.setAzkarStart(it, true) }
                    settings.answerOffer()
                }
            },
            text = stringResource(R.string.reminders_offer_enable),
        )
        TextButton(onClick = settings::answerOffer, text = stringResource(R.string.reminders_offer_later))
    }
}

/**
 * «Обновить» — вышла новая версия: стеклянная кнопка со стрелкой в лоток и золотой точкой, ведёт на страницу
 * приложения в Google Play (в 1.2.0 — Play In-App Updates вместо ссылки).
 */
@Composable
private fun UpdateButton(update: AvailableUpdate) {
    val palette = LocalInabahTheme.current.palette
    val uriHandler = LocalUriHandler.current
    val packageName = LocalContext.current.packageName
    Box {
        GlassIconButton(
            onClick = { uriHandler.openSafely("$PLAY_STORE_URL$packageName") },
            iconRes = R.drawable.ic_update,
            contentDescription = stringResource(R.string.update_available, update.version),
            tint = palette.tabAzkar,
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(UpdateDotSize)
                .background(palette.gold, CircleShape),
        )
    }
}

@Composable
private fun AzkarSectionCard(store: AzkarStore, windowSettings: AzkarWindowSettings, section: AzkarSection, onClick: () -> Unit) {
    val theme = LocalInabahTheme.current
    val state by store.state(section).collectAsStateWithLifecycle()
    val progress by store.progress(section).collectAsStateWithLifecycle()
    val isInWindow by store.isInWindow(section).collectAsStateWithLifecycle()
    val windows by windowSettings.windows.collectAsStateWithLifecycle()
    // Число зикров известно после загрузки; до неё — без подписи и кольца.
    val count = (state as? Loadable.Loaded)?.value?.size ?: 0
    val isLoaded = progress.total > 0
    SectionNavCard(
        title = stringResource(section.title),
        icon = painterResource(section.icon),
        iconColor = section.iconColor(theme),
        gradient = section.cardGradient(theme),
        shadow = section.cardShadow(theme),
        onClick = onClick,
        meta = if (count > 0) pluralStringResource(section.cardMeta, count, count) else null,
        // Кольцо — только во время азкаров; вне его — время окна.
        ring = if (isLoaded && isInWindow) NavCardRing(progress.fraction, section.ringStyle(theme)) else null,
        trailingNote = if (isLoaded && !isInWindow) windows.getValue(section).rangeText() else null,
    )
}
