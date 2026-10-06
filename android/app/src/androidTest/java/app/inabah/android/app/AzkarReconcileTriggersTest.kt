package app.inabah.android.app

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Сверка времени азкаров помимо таймера: возврат приложения и смена времени / пояса системы
 * (аудит 2026-10-06 — подписки не проверялись; без них после ночи в фоне счётчики остались бы вчерашними).
 */
@RunWith(AndroidJUnit4::class)
class AzkarReconcileTriggersTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun everyReturnToForegroundReconciles() {
        var calls = 0
        val triggers = AzkarReconcileTriggers { calls++ }
        val owner = TestOwner()
        triggers.install(context, owner.lifecycle)
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            assertEquals(1, calls)

            // Свернули и вернулись — снова.
            owner.registry.currentState = Lifecycle.State.CREATED
            owner.registry.currentState = Lifecycle.State.STARTED
            assertEquals(2, calls)
        } finally {
            context.unregisterReceiver(triggers.timeChangeReceiver)
        }
    }

    @Test
    fun timeAndTimeZoneChangesReconcile() {
        var calls = 0
        val triggers = AzkarReconcileTriggers { calls++ }

        // Системные широковещательные тест отправить не может — проверяем фильтр и сам приёмник.
        assertTrue(triggers.timeChangeFilter.hasAction(Intent.ACTION_TIME_CHANGED))
        assertTrue(triggers.timeChangeFilter.hasAction(Intent.ACTION_TIMEZONE_CHANGED))
        triggers.timeChangeReceiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        triggers.timeChangeReceiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        assertEquals(2, calls)
    }
}
