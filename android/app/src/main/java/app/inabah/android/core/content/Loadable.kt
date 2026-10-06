package app.inabah.android.core.content

/** Состояние загрузки раздела или сборника: `Idle → Loading → Loaded | Failed`. */
sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Loaded<out T>(val value: T) : Loadable<T>
    data class Failed(val error: ContentError) : Loadable<Nothing>

    /** Повторная загрузка ничего не делает; после `Failed` — разрешена. */
    val isLoadingOrLoaded: Boolean
        get() = when (this) {
            Loading, is Loaded -> true
            Idle, is Failed -> false
        }
}

/** Ошибка чтения контента; [cause] — исходное исключение для лога. */
sealed class ContentError(message: String, cause: Throwable?) : Exception(message, cause) {
    data class ResourceMissing(val file: String) : ContentError("Нет файла $file", cause = null)

    class DecodingFailed(val file: String, cause: Throwable) :
        ContentError("Не удалось разобрать $file: ${cause.message}", cause)

    class Unknown(val description: String, cause: Throwable? = null) : ContentError(description, cause)
}
