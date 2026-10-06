package app.inabah.android.core.audio

/**
 * Запись для плеера (iOS `AudioTrack`): [id] — `azkar.{раздел}.{номер}`, [assetFile] — имя файла
 * в `assets/audio/`, подписи плеера и экрана блокировки, [repeatCount] — сколько раз подряд звучит
 * в «Прослушать все» (меньше 1 — как 1).
 */
data class AudioTrack(
    val id: String,
    val assetFile: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val repeatCount: Int = 1,
) {
    val repeats: Int get() = repeatCount.coerceAtLeast(1)
}

/**
 * Элемент очереди движка: повтор записи или пауза перед следующим зикром. Плейлист разворачивается
 * в такие элементы целиком (`docs/android/04-audio.md`, «Плейлист: рекомендуемая схема»): переходы,
 * повторы и паузы делает сам ExoPlayer, без таймеров приложения — и с заблокированным экраном.
 */
sealed interface PlaylistItem {
    /** Номер зикра в очереди (с 0); у паузы — зикра после неё. */
    val zikrIndex: Int

    data class Track(val track: AudioTrack, override val zikrIndex: Int, val repetition: Int) : PlaylistItem

    /** [next] — запись после паузы: экран блокировки в паузе показывает её. */
    data class Pause(override val zikrIndex: Int, val durationMs: Long, val next: AudioTrack) : PlaylistItem
}

/**
 * Развернуть очередь: каждый повтор — отдельный элемент, между **разными** зикрами — пауза.
 * Скорость ExoPlayer ускоряет и тишину, а пауза задана в секундах реального времени (как в iOS) —
 * поэтому длительность паузы = пауза × скорость. Повторы — по [AudioTrack.repeats].
 */
fun expandPlaylist(tracks: List<AudioTrack>, pauseSeconds: Double, rate: Float): List<PlaylistItem> {
    val pauseMs = (pauseSeconds * rate * MILLIS_IN_SECOND).toLong()
    return buildList {
        tracks.forEachIndexed { zikr, track ->
            if (zikr > 0 && pauseMs > 0) add(PlaylistItem.Pause(zikr, pauseMs, next = track))
            repeat(track.repeats) { add(PlaylistItem.Track(track, zikr, repetition = it + 1)) }
        }
    }
}

private const val MILLIS_IN_SECOND = 1000

/** Первый повтор зикра [zikrIndex]; нет такого — `null`. */
fun List<PlaylistItem>.firstItemOf(zikrIndex: Int): Int? =
    indexOfFirst { it is PlaylistItem.Track && it.zikrIndex == zikrIndex }.takeIf { it >= 0 }

/** Номер зикра элемента [index] (у паузы — следующего), в границах очереди. */
fun List<PlaylistItem>.zikrAt(index: Int): Int = getOrNull(index.coerceIn(0, lastIndex))?.zikrIndex ?: 0

/** Повтор элемента [index]; у паузы — 1 (дальше звучит первый повтор следующего зикра). */
fun List<PlaylistItem>.repetitionAt(index: Int): Int = (getOrNull(index) as? PlaylistItem.Track)?.repetition ?: 1

/** Число зикров в очереди. */
val List<PlaylistItem>.zikrCount: Int get() = (lastOrNull()?.zikrIndex ?: -1) + 1

/** Элемент первого повтора соседнего зикра (⌃ ⌄, ⏮ ⏭ — по зикрам, не по элементам); на краю — `null`. */
fun List<PlaylistItem>.neighbourZikrItem(index: Int, step: Int): Int? = firstItemOf(zikrAt(index) + step)

// Элементы уходят в службу воспроизведения как MediaItem: номер зикра и повтор — в mediaId,
// чтобы ⏮ ⏭ экрана блокировки (служба, ZikrNavigationPlayer) находили соседние зикры без приложения.

private const val TRACK_PREFIX = "zikr"
private const val PAUSE_PREFIX = "pause"
private const val SEPARATOR = ':'

/** `zikr:3:2:azkar.morning.4` — третий (с 0) зикр, второй повтор; `pause:3` — пауза перед ним. */
val PlaylistItem.mediaId: String
    get() = when (this) {
        is PlaylistItem.Track -> listOf(TRACK_PREFIX, zikrIndex, repetition, track.id).joinToString(SEPARATOR.toString())
        is PlaylistItem.Pause -> listOf(PAUSE_PREFIX, zikrIndex).joinToString(SEPARATOR.toString())
    }

/** Номер зикра из [mediaId]; не наш формат — `null`. */
fun zikrIndexOfMediaId(mediaId: String): Int? {
    val parts = mediaId.split(SEPARATOR)
    if (parts.firstOrNull() !in listOf(TRACK_PREFIX, PAUSE_PREFIX)) return null
    return parts.getOrNull(1)?.toIntOrNull()
}

/** Элемент — запись (не пауза). */
fun isTrackMediaId(mediaId: String): Boolean = mediaId.startsWith("$TRACK_PREFIX$SEPARATOR")

/**
 * То же, что [neighbourZikrItem], по mediaId элементов очереди плеера (служба видит только их):
 * первый повтор зикра рядом с элементом [current]; на краю или не наш плейлист — `null`.
 */
fun neighbourZikrItemByMediaId(mediaIds: List<String>, current: Int, step: Int): Int? {
    val zikr = mediaIds.getOrNull(current)?.let(::zikrIndexOfMediaId) ?: return null
    val target = zikr + step
    return mediaIds.indexOfFirst { isTrackMediaId(it) && zikrIndexOfMediaId(it) == target }.takeIf { it >= 0 }
}
