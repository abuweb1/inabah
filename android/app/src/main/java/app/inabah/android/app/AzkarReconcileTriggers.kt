package app.inabah.android.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Когда сверять время азкаров помимо таймера (`docs/android/03-domain-logic.md`, 3.3): при возврате
 * приложения на экран (таймер в фоне не тикает — после ночи счётчики иначе остались бы вчерашними)
 * и при смене системного времени или часового пояса. Отдельно от `InabahApplication` — проверяется
 * тестом (аудит 2026-10-06: подписки не проверялись ничем).
 */
class AzkarReconcileTriggers(private val reconcile: () -> Unit) {
    /** Смена времени и пояса — системные широковещательные, только от системы (`RECEIVER_NOT_EXPORTED`). */
    val timeChangeFilter = IntentFilter().apply {
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }

    val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reconcile()
    }

    val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = reconcile()
    }

    /** Подписаться на всё время жизни [processLifecycle] и процесса — отписка не нужна. */
    fun install(context: Context, processLifecycle: Lifecycle) {
        processLifecycle.addObserver(processObserver)
        ContextCompat.registerReceiver(context, timeChangeReceiver, timeChangeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
