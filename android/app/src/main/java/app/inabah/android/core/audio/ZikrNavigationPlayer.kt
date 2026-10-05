package app.inabah.android.core.audio

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Плеер для сессии: ⏮ ⏭ уведомления, экрана блокировки и наушников переходят **по зикрам** — к первому
 * повтору соседнего, а не к следующему элементу (повтору или паузе). ±10 с на экране блокировки нет:
 * система показывает либо их, либо переходы — выбраны переходы (`docs/android/04-audio.md`).
 */
@OptIn(UnstableApi::class)
class ZikrNavigationPlayer(player: Player) : ForwardingPlayer(player) {
    private fun neighbour(step: Int): Int? {
        val ids = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }
        return neighbourZikrItemByMediaId(ids, currentMediaItemIndex, step)
    }

    override fun getAvailableCommands(): Player.Commands {
        val hasNext = neighbour(1) != null
        val hasPrevious = neighbour(-1) != null
        return super.getAvailableCommands().buildUpon()
            .remove(COMMAND_SEEK_BACK)
            .remove(COMMAND_SEEK_FORWARD)
            .setAll(hasNext, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setAll(hasPrevious, COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    // Обёртку слушателей с `Player.Listener by listener` не делать: Kotlin не пробрасывает через делегирование
    // методы Java-интерфейса с реализацией по умолчанию — сессия переставала получать события плеера
    // («играет», «доиграла», смена элемента), плеер в приложении застывал (найдено на эмуляторе 2026-10-05).
    // Системная сессия (экран блокировки, уведомление) читает getAvailableCommands на каждом событии;
    // контроллеры Media3 могут видеть устаревший набор на краях очереди — переход тогда просто ничего не делает.

    override fun hasNextMediaItem(): Boolean = neighbour(1) != null

    override fun hasPreviousMediaItem(): Boolean = neighbour(-1) != null

    override fun seekToNext() = seekToNextMediaItem()

    override fun seekToNextMediaItem() {
        neighbour(1)?.let { seekTo(it, 0) }
    }

    override fun seekToPrevious() = seekToPreviousMediaItem()

    override fun seekToPreviousMediaItem() {
        neighbour(-1)?.let { seekTo(it, 0) }
    }

    private fun Player.Commands.Builder.setAll(enabled: Boolean, vararg commands: Int): Player.Commands.Builder =
        apply { commands.forEach { if (enabled) add(it) else remove(it) } }
}
