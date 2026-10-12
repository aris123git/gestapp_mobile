package com.maquis.caisse.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Relance NexaGes après démarrage de la tablette.
 *
 * Conditions : option « démarrer automatiquement » et/ou mode kiosque activé.
 * Les réglages sont persistants (SharedPreferences) et restent valables après reboot.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (!isBootAction(action)) return

        val pending = goAsync()
        try {
            val store = KioskSecureStore(context.applicationContext)
            // Annule une sortie temporaire : le kiosque reste activé et se relock.
            store.clearTemporarilyUnlockedOnBoot()

            val shouldLaunch = store.autoStart || store.enabled
            if (!shouldLaunch) {
                Log.i(TAG, "Auto-start/kiosque désactivés — ignoré (action=$action)")
                return
            }

            Log.i(
                TAG,
                "Démarrage auto NexaGes (action=$action, autoStart=${store.autoStart}, kiosk=${store.enabled})",
            )
            BootLaunchHelper.launchApp(context, fromBoot = true)
            if (store.enabled) {
                KioskWatchdog.schedule(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Échec boot launch: ${e.message}", e)
        } finally {
            pending.finish()
        }
    }

    private fun isBootAction(action: String): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_QUICKBOOT ||
            action == ACTION_HTC_QUICKBOOT ||
            action == Intent.ACTION_REBOOT

    companion object {
        private const val TAG = "NexaKioskBoot"
        const val EXTRA_FROM_BOOT = "kiosk_from_boot"
        private const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
