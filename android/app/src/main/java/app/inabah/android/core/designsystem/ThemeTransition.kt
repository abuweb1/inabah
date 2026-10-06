package app.inabah.android.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp

// Смена палитры с анимацией (docs/android/06-screens.md, 6.8 «Палитра»; iOS — withAnimation(.highlight)
// при выборе стиля): все цвета и градиенты темы плавно переходят к новому стилю за 300 мс.

/** Тема на доле [fraction] пути от [from] к [to]: каждый цвет палитры и каждый градиент. */
fun Theme.Companion.lerp(from: Theme, to: Theme, fraction: Float): Theme = when {
    fraction <= 0f -> from
    fraction >= 1f -> to
    else -> Theme(from.palette.lerp(to.palette, fraction), from.gradients.lerp(to.gradients, fraction))
}

/**
 * Тема для экрана: при смене [target] — плавный переход от того, что видно сейчас (смена посреди
 * перехода не прыгает); первый показ и пересоздание активности — сразу [target].
 */
@Composable
fun animateTheme(target: Theme): Theme {
    // Последний показанный кадр — записывается только для применённых композиций (SideEffect).
    val shown = remember { arrayOf(target) }
    // Переход создаётся в той же композиции, что увидела новый target, и начинается с 0 — первый кадр
    // смены показывает прежний вид (без этого на кадр мигала новая палитра: запись экрана 2026-10-05).
    // remember(target) откатывается вместе с отменённой композицией.
    val run = remember(target) { ThemeRun(from = shown[0], progress = Animatable(if (shown[0] == target) 1f else 0f)) }
    LaunchedEffect(run) { run.progress.animateTo(1f, Motion.highlight()) }
    val result = Theme.lerp(run.from, target, run.progress.value)
    SideEffect { shown[0] = result }
    return result
}

/** Один переход к новому стилю: откуда (видимый кадр в момент смены) и сколько пройдено. */
private class ThemeRun(val from: Theme, val progress: Animatable<Float, *>)

private fun Palette.lerp(to: Palette, t: Float): Palette = Palette(
    background = lerp(background, to.background, t),
    card = lerp(card, to.card, t),
    actionBackground = lerp(actionBackground, to.actionBackground, t),
    header = lerp(header, to.header, t),
    eveningHeader = lerp(eveningHeader, to.eveningHeader, t),
    hadithHeader = lerp(hadithHeader, to.hadithHeader, t),
    shadow = lerp(shadow, to.shadow, t),
    textPrimary = lerp(textPrimary, to.textPrimary, t),
    textSecondary = lerp(textSecondary, to.textSecondary, t),
    textTertiary = lerp(textTertiary, to.textTertiary, t),
    onAccent = lerp(onAccent, to.onAccent, t),
    accent = lerp(accent, to.accent, t),
    accentLight = lerp(accentLight, to.accentLight, t),
    accentDim = lerp(accentDim, to.accentDim, t),
    accentShadow = lerp(accentShadow, to.accentShadow, t),
    morningCardShadow = lerp(morningCardShadow, to.morningCardShadow, t),
    nawawiCardShadow = lerp(nawawiCardShadow, to.nawawiCardShadow, t),
    success = lerp(success, to.success, t),
    successLight = lerp(successLight, to.successLight, t),
    successDeep = lerp(successDeep, to.successDeep, t),
    successDim = lerp(successDim, to.successDim, t),
    statusRead = lerp(statusRead, to.statusRead, t),
    statusMemorized = lerp(statusMemorized, to.statusMemorized, t),
    gold = lerp(gold, to.gold, t),
    goldLight = lerp(goldLight, to.goldLight, t),
    goldDeep = lerp(goldDeep, to.goldDeep, t),
    sunRays = lerp(sunRays, to.sunRays, t),
    parchmentLight = lerp(parchmentLight, to.parchmentLight, t),
    parchmentMid = lerp(parchmentMid, to.parchmentMid, t),
    parchmentDeep = lerp(parchmentDeep, to.parchmentDeep, t),
    parchmentInk = lerp(parchmentInk, to.parchmentInk, t),
    parchmentHighlight = lerp(parchmentHighlight, to.parchmentHighlight, t),
    parchmentText = lerp(parchmentText, to.parchmentText, t),
    parchmentAccent = lerp(parchmentAccent, to.parchmentAccent, t),
    tabAzkar = lerp(tabAzkar, to.tabAzkar, t),
    tabHadith = lerp(tabHadith, to.tabHadith, t),
    tabMakharij = lerp(tabMakharij, to.tabMakharij, t),
    tabSettings = lerp(tabSettings, to.tabSettings, t),
    destructive = lerp(destructive, to.destructive, t),
)

private fun ThemeGradients.lerp(to: ThemeGradients, t: Float): ThemeGradients = ThemeGradients(
    azkarBackground = azkarBackground.lerp(to.azkarBackground, t),
    eveningBackground = eveningBackground.lerp(to.eveningBackground, t),
    hadithBackground = hadithBackground.lerp(to.hadithBackground, t),
    settingsBackground = settingsBackground.lerp(to.settingsBackground, t),
    makharijBackground = makharijBackground.lerp(to.makharijBackground, t),
    morningCard = morningCard.lerp(to.morningCard, t),
    eveningCard = eveningCard.lerp(to.eveningCard, t),
    nawawiCard = nawawiCard.lerp(to.nawawiCard, t),
    qudsiCard = qudsiCard.lerp(to.qudsiCard, t),
    ajurriCard = ajurriCard.lerp(to.ajurriCard, t),
    progressFill = progressFill.lerp(to.progressFill, t),
    counterButton = counterButton.lerp(to.counterButton, t),
    counterButtonDone = counterButtonDone.lerp(to.counterButtonDone, t),
    parchment = parchment.lerp(to.parchment, t),
    parchmentStripe = parchmentStripe.lerp(to.parchmentStripe, t),
)
