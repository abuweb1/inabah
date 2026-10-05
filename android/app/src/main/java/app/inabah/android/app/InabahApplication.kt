package app.inabah.android.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    refreshAzkarPeriods()
                    container.services.value?.audioPlayer?.onForeground()
                }

                // Одиночная запись в фоне — на паузу (как в iOS), плейлист играет дальше.
                override fun onStop(owner: LifecycleOwner) {
                    container.services.value?.audioPlayer?.onBackground()
                }
            },
        )
        // Приёмник живёт столько же, сколько процесс: отписка не нужна.
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = refreshAzkarPeriods()
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** До первого чтения настроек сторов ещё нет — периоды проверит их загрузка. */
    private fun refreshAzkarPeriods() {
        container.services.value?.azkarStore?.refreshPeriods()
    }
}
