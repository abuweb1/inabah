package app.inabah.android.feature.azkar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import app.inabah.android.R
import app.inabah.android.core.audio.AudioTrack
import app.inabah.android.core.content.model.AzkarSection
import app.inabah.android.core.content.model.Zikr

// Записи азкаров для плеера (iOS Zikr+Audio): идентификаторы — `azkar.{раздел}.{номер}`,
// плейлиста раздела — `azkar.{раздел}`.

val Zikr.audioTrackId: String get() = "azkar.${id.section.key}.${id.number}"

val AzkarSection.playlistId: String get() = "azkar.$key"

/** Раздел записи или плейлиста по идентификатору; не азкары — `null`. */
fun azkarSectionOf(audioId: String): AzkarSection? {
    val parts = audioId.split('.')
    if (parts.firstOrNull() != AUDIO_DOMAIN) return null
    return AzkarSection.entries.firstOrNull { it.key == parts.getOrNull(1) }
}

private const val AUDIO_DOMAIN = "azkar"

/**
 * Трек для плеера, подписи плеера и экрана блокировки: «Зикр №3», «Утренние азкары», «АЗКАРЫ».
 * Записи нет — `null`.
 */
@Composable
fun Zikr.toAudioTrack(repeatsByCount: Boolean = false): AudioTrack? {
    val file = audioFileName ?: return null
    return AudioTrack(
        id = audioTrackId,
        assetFile = file,
        title = stringResource(R.string.audio_zikr_title, id.number),
        subtitle = stringResource(id.section.title),
        category = stringResource(R.string.audio_category_azkar),
        repeatCount = if (repeatsByCount) repetitions else 1,
    )
}

/**
 * Состояние записи зикра для карточки — готовым значением от ленты (карточки не читают плеер):
 * [isActive] — запись выбрана и не доиграла (волна, «Открыть плеер»), [isPlaylistCurrent] — звучит
 * в «Прослушать все» (золотая рамка).
 */
@Immutable
data class ZikrAudioState(val isActive: Boolean = false, val isPlaylistCurrent: Boolean = false) {
    companion object {
        val Idle = ZikrAudioState()
    }
}
