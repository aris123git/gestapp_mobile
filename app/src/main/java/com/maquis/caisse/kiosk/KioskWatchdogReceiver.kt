package com.maquis.caisse.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Relance périodique : si le kiosque est activé et l'app n'est pas au premier plan,
 * ramène NexaGes (tablette dédiée).
 */
class KioskWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = KioskSecureStore(context.applicationContext)
        store.consumeExpiredUnlock()
        if (!store.enabled && !store.autoStart) return
        if (!store.enabled) return
        if (store.temporarilyUnlocked) {
            // Sortie admin encore valide — ne pas forcer.
            if (store.tempUnlockRemainingMs() > 0L) return
            store.consumeExpiredUnlock()
        }
        Log.i(TAG, "Watchdog : rappel NexaGes au premier plan")
        BootLaunchHelper.launchApp(context, fromBoot = false)
        KioskWatchdog.schedule(context.applicationContext)
    }

    companion object {
        private const val TAG = "NexaKioskWatchdog"
        const val ACTION = "com.maquis.caisse.action.KIOSK_WATCHDOG"
    }
}
