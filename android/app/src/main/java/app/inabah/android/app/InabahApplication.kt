package app.inabah.android.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Приложение: создаёт [AppContainer] и пересчитывает периоды азкаров, когда могло наступить обнуление —
 * при возврате приложения на экран и при смене системного времени или часового пояса.
 */
class InabahApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()

        val processLifecycle = ProcessLifecycleOwner.get().lifecycle
        AzkarReconcileTriggers(::reconcileAzkar).install(this, processLifecycle)
        processLifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.services.value?.audioPlayer?.onForeground()
                }

                // Одиночная запись в фоне — на паузу (как в iOS), плейлист играет дальше.
                override fun onStop(owner: LifecycleOwner) {
                    container.services.value?.audioPlayer?.onBackground()
                }
            },
        )
    }

    /** До первого чтения настроек сторов ещё нет — отрезки проверит их загрузка. */
    private fun reconcileAzkar() {
        container.services.value?.azkarStore?.reconcile()
    }
}
