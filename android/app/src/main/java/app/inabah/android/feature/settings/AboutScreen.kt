package app.inabah.android.feature.settings

import android.content.ActivityNotFoundException
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.inabah.android.BuildConfig
import app.inabah.android.R
import app.inabah.android.core.content.LicenseDocument
import app.inabah.android.core.designsystem.InabahTheme
import app.inabah.android.core.designsystem.InabahType
import app.inabah.android.core.designsystem.LocalInabahTheme
import app.inabah.android.core.designsystem.PressFeedback
import app.inabah.android.core.designsystem.Size
import app.inabah.android.core.designsystem.Spacing
import app.inabah.android.core.designsystem.components.ArabicText
import app.inabah.android.core.designsystem.components.InabahTopBar
import app.inabah.android.core.designsystem.components.SettingsChevron
import app.inabah.android.core.designsystem.components.SettingsGroup
import app.inabah.android.core.designsystem.components.SettingsRow
import app.inabah.android.core.designsystem.components.SettingsRowText
import app.inabah.android.core.designsystem.components.SettingsScaffold
import app.inabah.android.core.designsystem.components.pressFeedback
import app.inabah.android.core.settings.AppIconOption

/** Иконка в шапке — 72, в sp: растёт вместе с текстом интерфейса (iOS `@ScaledMetric`). */
private const val HEADER_ICON_SIZE = 72f
private const val ARABIC_NAME_SIZE = 28f

/** Значок «открыть в браузере» у ссылки — как символ footnote. */
private const val LINK_GLYPH_SIZE = 16f

/** Название приложения по-арабски — бренд, не переводится. */
private const val ARABIC_NAME = "إنابة"

/** Внешние адреса экрана (iOS `AboutLinks`). */
internal object AboutLinks {
    const val REPOSITORY = "https://github.com/abuweb1/inabah"
    const val PRIVACY = "https://github.com/abuweb1/inabah/blob/main/PRIVACY.md"
    const val ISSUES = "https://github.com/abuweb1/inabah/issues"
}

/** Версия как в iOS: «1.0.0 (1)» — versionName и versionCode. */
internal val appVersion: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

/**
 * «О приложении» (iOS `AboutView`): иконка, название и версия; откуда тексты (без ссылок на сайты); лицензии
 * шрифтов и значков ([onOpenLicense] — полный текст); политика конфиденциальности и репозиторий.
 */
@Composable
fun AboutScreen(
    onOpenLicense: (LicenseDocument) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val theme = LocalInabahTheme.current
    val uriHandler = LocalUriHandler.current
    SettingsScaffold(
        background = theme.gradients.settingsBackground,
        contentPadding = contentPadding,
        modifier = modifier,
        topBar = { InabahTopBar(title = stringResource(R.string.about_title), tint = theme.palette.tabSettings, onBack = onBack) },
    ) {
        SettingsGroup(rows = listOf { AboutHeader() })
        SettingsGroup(
            header = stringResource(R.string.about_sources_header),
            footer = stringResource(R.string.about_sources_footer),
            rows = listOf(
                { AboutTextRow(R.string.about_sources_azkar_title, R.string.about_sources_azkar_text) },
                { AboutTextRow(R.string.about_sources_hadith_title, R.string.about_sources_hadith_text) },
            ),
        )
        SettingsGroup(
            header = stringResource(R.string.about_licenses_header),
            rows = LicenseDocument.entries.map { document ->
                @Composable {
                    SettingsRow(onClick = { onOpenLicense(document) }, trailing = { SettingsChevron() }) {
                        SettingsRowText(title = stringResource(document.title), subtitle = stringResource(document.subtitle))
                    }
                }
            },
        )
        SettingsGroup(
            header = stringResource(R.string.about_app_header),
            footer = stringResource(R.string.about_app_footer),
            rows = listOf(
                { AboutLinkRow(R.string.about_link_privacy) { uriHandler.openSafely(AboutLinks.PRIVACY) } },
                { AboutLinkRow(R.string.about_link_repository) { uriHandler.openSafely(AboutLinks.REPOSITORY) } },
                { AboutLinkRow(R.string.about_link_issues) { uriHandler.openSafely(AboutLinks.ISSUES) } },
            ),
        )
    }
}

/** Иконка «Классическая», «إنابة», «Инаба» и версия — одним элементом для TalkBack. */
@Composable
private fun AboutHeader() {
    val palette = InabahTheme.palette
    val iconSize = with(LocalDensity.current) { HEADER_ICON_SIZE.sp.toDp() }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.l)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        AppIconImage(AppIconOption.Classic, iconSize)
        ArabicText(ARABIC_NAME, ARABIC_NAME_SIZE, palette.onAccent, bold = true, textAlign = TextAlign.Center)
        Text(stringResource(R.string.about_app_name), color = palette.onAccent, style = InabahType.headline)
        Text(stringResource(R.string.about_version, appVersion), color = palette.onAccentSecondary, style = InabahType.caption)
    }
}

@Composable
private fun AboutTextRow(@StringRes title: Int, @StringRes text: Int) {
    SettingsRow {
        SettingsRowText(title = stringResource(title), subtitle = stringResource(text))
    }
}

/** Ссылка наружу: подпись и «↗» (iOS `AboutLinkLabel`). */
@Composable
private fun AboutLinkRow(@StringRes title: Int, onClick: () -> Unit) {
    val glyph = with(LocalDensity.current) { LINK_GLYPH_SIZE.sp.toDp() }
    SettingsRow(
        onClick = onClick,
        trailing = {
            Icon(painterResource(R.drawable.ic_arrow_outward), contentDescription = null,
                tint = InabahTheme.palette.onAccentTertiary, modifier = Modifier.size(glyph))
        },
    ) {
        SettingsRowText(title = stringResource(title))
    }
}

/**
 * «Инаба 1.0.0 · О приложении» — мелкая ссылка по центру у нижнего края корня настроек, над панелью вкладок
 * (iOS `AboutFooterLink`, решение пользователя 2026-10-05). Для TalkBack — «О приложении».
 */
@Composable
internal fun AboutFooterLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(R.string.about_title)
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Size.minTapTarget)
            .clickable(interaction, indication = null, onClick = onClick)
            .pressFeedback(interaction, opacity = PressFeedback.ROW_OPACITY)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(bottom = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.settings_about_footer, BuildConfig.VERSION_NAME),
            color = InabahTheme.palette.onAccentTertiary,
            style = InabahType.caption,
            textAlign = TextAlign.Center,
        )
    }
}

@get:StringRes
internal val LicenseDocument.title: Int
    get() = when (this) {
        LicenseDocument.Scheherazade -> R.string.about_license_scheherazade_title
        LicenseDocument.MaterialSymbols -> R.string.about_license_material_title
        LicenseDocument.Inter -> R.string.about_license_inter_title
    }

@get:StringRes
private val LicenseDocument.subtitle: Int
    get() = when (this) {
        LicenseDocument.Scheherazade -> R.string.about_license_scheherazade_subtitle
        LicenseDocument.MaterialSymbols -> R.string.about_license_material_subtitle
        LicenseDocument.Inter -> R.string.about_license_inter_subtitle
    }

/** Браузера на телефоне нет — ссылка просто не открывается (Compose бросает исключение, а не молчит). */
private fun UriHandler.openSafely(url: String) {
    try {
        openUri(url)
    } catch (_: ActivityNotFoundException) {
    } catch (_: IllegalArgumentException) {
    }
}
