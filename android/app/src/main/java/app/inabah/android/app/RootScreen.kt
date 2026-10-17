package app.inabah.android.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import app.inabah.android.core.designsystem.Motion
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inabah.android.core.designsystem.InterfaceTextScale
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.components.bottomOnly
import app.inabah.android.core.audio.ui.AudioPlayerHost
import app.inabah.android.feature.azkar.AzkarCalendarScreen
import app.inabah.android.feature.azkar.AzkarHomeScreen
import app.inabah.android.feature.azkar.AzkarListScreen
import app.inabah.android.feature.hadith.CollectionAboutScreen
import app.inabah.android.feature.hadith.FavoritesScreen
import app.inabah.android.feature.hadith.HadithDetailScreen
import app.inabah.android.feature.hadith.HadithHomeScreen
import app.inabah.android.feature.hadith.HadithListScreen
import app.inabah.android.feature.makharij.MakharijHomeScreen
import app.inabah.android.feature.makharij.ReadingZikrScreen
import app.inabah.android.feature.makharij.ReadingAlphabetScreen
import app.inabah.android.feature.makharij.ReadingExtraScreen
import app.inabah.android.feature.makharij.ReadingLetterFormsScreen
import app.inabah.android.feature.makharij.ReadingLessonScreen
import app.inabah.android.feature.makharij.ReadingQuizScreen
import app.inabah.android.feature.makharij.ReadingStepsScreen
import app.inabah.android.feature.makharij.ReadingWordsScreen
import app.inabah.android.feature.settings.AboutScreen
import app.inabah.android.feature.settings.AppIconSettingsScreen
import app.inabah.android.feature.settings.LicenseScreen
import app.inabah.android.feature.settings.AzkarSettingsScreen
import app.inabah.android.feature.settings.HadithOrderSettingsScreen
import app.inabah.android.feature.settings.HadithSettingsScreen
import app.inabah.android.feature.settings.PaletteSettingsScreen
import app.inabah.android.feature.settings.SettingsScreen
import app.inabah.android.feature.settings.TextSizeSettingsScreen
import app.inabah.android.feature.settings.VersionHistoryScreen
import app.inabah.android.feature.whatsnew.WhatsNewOverlay

/**
 * Корень (iOS `RootTabView`): вкладки, у каждой (кроме «Махраджа») — свой стек экранов (Navigation 3)
 * над стеком [AppRouter], мини-плеер над панелью вкладок — один на приложение. Стеки и вкладка
 * переживают смерть процесса (снимок роутера в сохранённом состоянии).
 */
@Composable
fun RootScreen(services: AppServices, modifier: Modifier = Modifier) {
    val router = services.router
    // Сохраняется при уходе в фон, восстанавливается до первого кадра (после смерти процесса).
    rememberSaveable(saver = Saver<Unit, String>(save = { router.snapshot() }, restore = { router.restore(it) })) {}
    // «Что нового» после обновления — поверх любой вкладки, один раз (WhatsNew.check при запуске).
    val whatsNew by services.whatsNew.pending.collectAsStateWithLifecycle()
    RootContent(
        selectedTab = router.selectedTab,
        canGoBack = router.canGoBack,
        onSelectTab = router::select,
        onBack = { router.goBack() },
        modifier = modifier,
        player = { AudioPlayerHost(services.audioPlayer) },
        overlay = whatsNew?.let { notes ->
            { backdrop ->
                // Цвет — раздела, на котором окно появилось (в единой теме у всех разделов цвет темы).
                val tab = remember(notes) { router.selectedTab }
                WhatsNewOverlay(notes, tab.tint(LocalInabahTheme.current), backdrop, onDismiss = services.whatsNew::dismiss)
            }
        },
    ) { tab, contentPadding ->
        TabContent(tab, services, contentPadding)
    }
}

/**
 * Фон раздела до краёв экрана и плавающая панель вкладок, как в iOS: содержимое прокручивается
 * под панелью (нижний отступ содержимого — высота панели). Системная «Назад»: экран вкладки
 * снимает её `NavDisplay`, с корня вкладки — на «Азкары», с корня «Азкаров» — выход (docs/android/02).
 * Вкладки сменяются перетеканием за 300 мс; состояние экранов невыбранной вкладки сохраняется.
 */
@Composable
fun RootContent(
    selectedTab: AppTab,
    canGoBack: Boolean,
    onSelectTab: (AppTab) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Мини-плеер над панелью вкладок; его высота входит в нижний отступ содержимого. */
    player: @Composable () -> Unit = {},
    /**
     * Окно поверх всего («Что нового»): получает экран вкладки как источник размытия; пока оно есть,
     * экран под ним для TalkBack скрыт.
     */
    overlay: (@Composable (backdrop: HazeState) -> Unit)? = null,
    content: @Composable (tab: AppTab, contentPadding: PaddingValues) -> Unit,
) {
    BackHandler(enabled = canGoBack, onBack = onBack)

    val theme = LocalInabahTheme.current
    val tabStates = rememberSaveableStateHolder()
    // Экран вкладки (вместе с фоном) — источник размытия под панелью вкладок, как стекло iOS.
    val backdrop = rememberHazeState()
    Box(modifier.fillMaxSize()) {
        Scaffold(
            modifier = if (overlay != null) Modifier.clearAndSetSemantics {} else Modifier,
            containerColor = Color.Transparent,
            bottomBar = {
                // Панель вкладок — после плеера: рисуется поверх, плеер при скрытии уезжает под неё.
                Column {
                    player()
                    FloatingTabBar(selectedTab = selectedTab, onSelect = onSelectTab, backdrop = backdrop)
                }
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(backdrop)
                    .background(selectedTab.background(theme)),
            ) {
                // Смена вкладки — перетеканием за 300 мс, как смена палитры: в «По умолчанию» у каждого
                // раздела свой цвет, и он плавно переходит в цвет следующего (решение пользователя 2026-10-05).
                Crossfade(targetState = selectedTab, animationSpec = Motion.highlight(), label = "tab") { tab ->
                    tabStates.SaveableStateProvider(tab.name) {
                        content(tab, innerPadding)
                    }
                }
            }
        }
        overlay?.invoke(backdrop)
    }
}

/**
 * Экраны, где горизонтальный свайп листает содержимое (страницы хадисов, группы слов, буквы группы,
 * месяцы календаря): свайпа «назад» там нет - только кнопка «‹» и системный жест (решение пользователя).
 */
private fun swipesContent(route: Any): Boolean = when (route) {
    is HadithRoute.Detail, is MakharijRoute.Words, is MakharijRoute.LetterForms, AzkarRoute.Calendar -> true
    else -> false
}

/** Корень стека вкладки — её главный экран; дальше — маршруты из [AppRouter]. */
private data object TabRoot

@Composable
private fun TabContent(tab: AppTab, services: AppServices, contentPadding: PaddingValues) {
    // Шаг интерфейса — на экраны вкладки (панель вкладок — вне его); навбар, кнопки и всплывающие
    // окна внутри экранов закреплены сами (docs/android/10-typography.md, 10.1).
    val interfaceSize by services.textSizeSettings.interfaceSize.collectAsStateWithLifecycle()
    InterfaceTextScale(interfaceSize.fontScale) {
        TabScreens(tab, services, contentPadding)
    }
}

@Composable
private fun TabScreens(tab: AppTab, services: AppServices, contentPadding: PaddingValues) {
    val router = services.router
    when (tab) {
        AppTab.Azkar -> TabNavDisplay(router.azkarStack, onBack = { router.pop(AppTab.Azkar) }) {
            entry<TabRoot> {
                AzkarHomeScreen(
                    services.azkarStore,
                    services.azkarHistory,
                    services.azkarWindowSettings,
                    services.reminderSettings,
                    services.updateSource,
                    onOpenSection = { router.push(AzkarRoute.SectionList(it)) },
                    onOpenCalendar = { router.push(AzkarRoute.Calendar) },
                    contentPadding = contentPadding,
                )
            }
            entry<AzkarRoute.Calendar> {
                AzkarCalendarScreen(
                    history = services.azkarHistory,
                    store = services.azkarStore,
                    onBack = { router.pop(AppTab.Azkar) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<AzkarRoute.SectionList> { route ->
                AzkarListScreen(
                    section = route.section,
                    store = services.azkarStore,
                    history = services.azkarHistory,
                    windowSettings = services.azkarWindowSettings,
                    readingSettings = services.readingSettings,
                    player = services.audioPlayer,
                    playlistSettings = services.playlistSettings,
                    onBack = { router.pop(AppTab.Azkar) },
                    onGoHome = { router.popToRoot(AppTab.Azkar) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
        }
        AppTab.Hadith -> TabNavDisplay(router.hadithStack, onBack = { router.pop(AppTab.Hadith) }) {
            entry<TabRoot> {
                HadithHomeScreen(
                    store = services.hadithStore,
                    progress = services.hadithProgress,
                    order = services.hadithCollectionOrder,
                    onOpenCollection = { router.push(HadithRoute.CollectionList(it)) },
                    onOpenFavorites = { router.push(HadithRoute.Favorites) },
                    contentPadding = contentPadding,
                )
            }
            entry<HadithRoute.Favorites> {
                FavoritesScreen(
                    store = services.hadithStore,
                    progress = services.hadithProgress,
                    favorites = services.hadithFavorites,
                    order = services.hadithCollectionOrder,
                    onBack = { router.pop(AppTab.Hadith) },
                    onOpenHadith = { router.push(HadithRoute.Detail(it)) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<HadithRoute.CollectionList> { route ->
                HadithListScreen(
                    collection = route.collection,
                    store = services.hadithStore,
                    progress = services.hadithProgress,
                    onBack = { router.pop(AppTab.Hadith) },
                    onOpenHadith = { router.push(HadithRoute.Detail(it)) },
                    onOpenAbout = { router.push(HadithRoute.About(route.collection)) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<HadithRoute.About> { route ->
                CollectionAboutScreen(
                    collection = route.collection,
                    store = services.hadithStore,
                    progress = services.hadithProgress,
                    onBack = { router.pop(AppTab.Hadith) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<HadithRoute.Detail> { route ->
                HadithDetailScreen(
                    id = route.id,
                    store = services.hadithStore,
                    progress = services.hadithProgress,
                    favorites = services.hadithFavorites,
                    readingSettings = services.readingSettings,
                    onBack = { router.pop(AppTab.Hadith) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
        }
        AppTab.Makharij -> {
            val clips by services.clipPlayer.available.collectAsStateWithLifecycle()
            TabNavDisplay(router.makharijStack, onBack = { router.pop(AppTab.Makharij) }) {
                entry<TabRoot> {
                    MakharijHomeScreen(
                        store = services.readingStore,
                        progress = services.readingProgress,
                        onOpenSteps = { router.push(MakharijRoute.Steps) },
                        contentPadding = contentPadding,
                    )
                }
                entry<MakharijRoute.Steps> {
                    ReadingStepsScreen(
                        store = services.readingStore,
                        progress = services.readingProgress,
                        onBack = { router.pop(AppTab.Makharij) },
                        onOpenStep = { router.push(MakharijRoute.Lesson(it)) },
                        contentPadding = contentPadding.bottomOnly(),
                    )
                }
                entry<MakharijRoute.Lesson> { route ->
                    ReadingLessonScreen(
                        stepNumber = route.step,
                        store = services.readingStore,
                        progress = services.readingProgress,
                        settings = services.readingSettings,
                        onBack = { router.pop(AppTab.Makharij) },
                        onOpenQuiz = { router.push(MakharijRoute.Quiz(route.step)) },
                        onOpenWords = { router.push(MakharijRoute.Words(route.step)) },
                        onOpenAlphabet = { router.push(MakharijRoute.Alphabet(route.step)) },
                        onOpenExtra = { router.push(MakharijRoute.Extra(route.step, it)) },
                        onOpenLetterForms = { router.push(MakharijRoute.LetterForms(route.step, it)) },
                        onOpenZikr = { router.push(MakharijRoute.Zikr(route.step, it)) },
                        clips = clips,
                        onPlayClip = services.clipPlayer::play,
                        contentPadding = contentPadding.bottomOnly(),
                    )
                }
                entry<MakharijRoute.Quiz> { route ->
                    ReadingQuizScreen(
                        stepNumber = route.step,
                        store = services.readingStore,
                        progress = services.readingProgress,
                        onBack = { router.pop(AppTab.Makharij) },
                        onOpenStep = { router.open(MakharijRoute.Lesson(it)) },
                        onOpenSteps = { router.open(MakharijRoute.Steps) },
                        onOpenWords = { router.open(MakharijRoute.Words(route.step)) },
                        contentPadding = contentPadding.bottomOnly(),
                    )
                }
                entry<MakharijRoute.LetterForms> { route ->
                    ReadingLetterFormsScreen(
                        stepNumber = route.step,
                        letter = route.letter,
                        store = services.readingStore,
                        onBack = { router.pop(AppTab.Makharij) },
                        contentPadding = contentPadding.bottomOnly(),
                        clips = clips,
                        onPlayClip = services.clipPlayer::play,
                    )
                }
                entry<MakharijRoute.Alphabet> { route ->
                    ReadingAlphabetScreen(
                        stepNumber = route.step,
                        store = services.readingStore,
                        settings = services.readingSettings,
                        onBack = { router.pop(AppTab.Makharij) },
                        contentPadding = contentPadding.bottomOnly(),
                    )
                }
                entry<MakharijRoute.Extra> { route ->
                    ReadingExtraScreen(
                        stepNumber = route.step,
                        topic = route.topic,
                        store = services.readingStore,
                        settings = services.readingSettings,
                        onBack = { router.pop(AppTab.Makharij) },
                        contentPadding = contentPadding.bottomOnly(),
                    )
                }
                entry<MakharijRoute.Zikr> { route ->
                    ReadingZikrScreen(
                        stepNumber = route.step,
                        zikrIndex = route.zikr,
                        store = services.readingStore,
                        progress = services.readingProgress,
                        settings = services.readingSettings,
                        onBack = { router.pop(AppTab.Makharij) },
                        onOpenAzkar = { router.open(AzkarRoute.SectionList(it)) },
                        onOpenStep = { router.open(MakharijRoute.Lesson(it)) },
                        onOpenSteps = { router.open(MakharijRoute.Steps) },
                        contentPadding = contentPadding.bottomOnly(),
                        clips = clips,
                        playingClip = services.clipPlayer.playing.collectAsStateWithLifecycle().value,
                        onPlayClip = services.clipPlayer::play,
                        onStopClip = services.clipPlayer::stop,
                    )
                }
                entry<MakharijRoute.Words> { route ->
                    ReadingWordsScreen(
                        stepNumber = route.step,
                        store = services.readingStore,
                        progress = services.readingProgress,
                        settings = services.readingSettings,
                        onBack = { router.pop(AppTab.Makharij) },
                        onOpenStep = { router.open(MakharijRoute.Lesson(it)) },
                        onOpenQuiz = { router.open(MakharijRoute.Quiz(route.step)) },
                        onOpenSteps = { router.open(MakharijRoute.Steps) },
                        contentPadding = contentPadding.bottomOnly(),
                    )
                }
            }
        }
        AppTab.Settings -> TabNavDisplay(router.settingsStack, onBack = { router.pop(AppTab.Settings) }) {
            entry<TabRoot> {
                SettingsScreen(onOpen = router::push, contentPadding = contentPadding.bottomOnly())
            }
            entry<SettingsRoute.Azkar> {
                AzkarSettingsScreen(
                    store = services.azkarStore,
                    history = services.azkarHistory,
                    windowSettings = services.azkarWindowSettings,
                    reminderSettings = services.reminderSettings,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.Hadith> {
                HadithSettingsScreen(
                    store = services.hadithStore,
                    progress = services.hadithProgress,
                    favorites = services.hadithFavorites,
                    order = services.hadithCollectionOrder,
                    reminderSettings = services.reminderSettings,
                    readingSettings = services.readingSettings,
                    onOpenOrder = { router.push(SettingsRoute.HadithOrder) },
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.HadithOrder> {
                HadithOrderSettingsScreen(
                    order = services.hadithCollectionOrder,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.Palette> {
                PaletteSettingsScreen(
                    settings = services.appearanceSettings,
                    parchmentSettings = services.parchmentSettings,
                    translationShadeSettings = services.translationShadeSettings,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.AppIcon> {
                AppIconSettingsScreen(
                    settings = services.appIconSettings,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.About> {
                AboutScreen(
                    onOpenLicense = { router.push(SettingsRoute.License(it)) },
                    onOpenVersionHistory = { router.push(SettingsRoute.VersionHistory) },
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.VersionHistory> {
                VersionHistoryScreen(
                    source = services.releaseNotes,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.License> { route ->
                LicenseScreen(
                    document = route.document,
                    texts = services.licenseTexts,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
            entry<SettingsRoute.TextSize> {
                TextSizeSettingsScreen(
                    settings = services.textSizeSettings,
                    azkarStore = services.azkarStore,
                    hadithStore = services.hadithStore,
                    onBack = { router.pop(AppTab.Settings) },
                    contentPadding = contentPadding.bottomOnly(),
                )
            }
        }
    }
}

/** Стек вкладки: корень [TabRoot] и маршруты [stack]; переходы — как в iOS (docs/android/05, «Плавность»). */
@Composable
private fun TabNavDisplay(
    stack: List<Any>,
    onBack: () -> Unit,
    entries: androidx.navigation3.runtime.EntryProviderScope<Any>.() -> Unit,
) {
    NavDisplay(
        backStack = listOf<Any>(TabRoot) + stack,
        // Свайп вправо - «назад», как на iPhone (решение пользователя 2026-10-15); на корне вкладки и на
        // экранах, где горизонтальный свайп листает содержимое, - нет.
        modifier = Modifier.swipeBack(enabled = stack.lastOrNull()?.let { !swipesContent(it) } ?: false),
        onBack = onBack,
        transitionSpec = { push() },
        popTransitionSpec = { pop() },
        predictivePopTransitionSpec = { pop() },
        entryProvider = entryProvider(builder = entries),
    )
}

private const val NAVIGATION_MILLIS = 350

/** Уходящий под новый экран сдвигается на 30 % ширины и притемняется. */
private const val UNDERLAP_FRACTION = 0.3f
private const val UNDERLAP_ALPHA = 0.7f

private fun <T> navigationSpec() = tween<T>(NAVIGATION_MILLIS, easing = FastOutSlowInEasing)

private fun underlapOffset(width: Int) = -(width * UNDERLAP_FRACTION).toInt()

/** Вперёд: новый экран справа на всю ширину, старый — влево на 30 % и тускнеет. */
private fun AnimatedContentTransitionScope<Scene<Any>>.push(): ContentTransform =
    slideInHorizontally(navigationSpec()) { it } togetherWith
        (slideOutHorizontally(navigationSpec(), ::underlapOffset) + fadeOut(navigationSpec(), UNDERLAP_ALPHA))

/** Назад — зеркально; уходящий экран остаётся сверху. */
private fun AnimatedContentTransitionScope<Scene<Any>>.pop(): ContentTransform =
    ((slideInHorizontally(navigationSpec(), ::underlapOffset) + fadeIn(navigationSpec(), UNDERLAP_ALPHA)) togetherWith
        slideOutHorizontally(navigationSpec()) { it })
        .apply { targetContentZIndex = -1f }
