package com.maquis.caisse.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

object KioskWatchdog {
    private const val TAG = "NexaKioskWatchdog"
    private const val REQ = 7201
    /** Intervalle de surveillance (ms). */
    const val INTERVAL_MS = 45_000L

    fun schedule(context: Context) {
        val app = context.applicationContext
        val store = KioskSecureStore(app)
        if (!store.enabled) {
            cancel(app)
            return
        }
        try {
            val am = app.getSystemService(AlarmManager::class.java) ?: return
            val pi = pendingIntent(app)
            val trigger = SystemClock.elapsedRealtime() + INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                }
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            }
            Log.i(TAG, "Watchdog planifié +${INTERVAL_MS}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Watchdog impossible: ${e.message}")
        }
    }

    fun cancel(context: Context) {
        try {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(pendingIntent(context.applicationContext))
        } catch (_: Exception) {
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, KioskWatchdogReceiver::class.java).setAction(KioskWatchdogReceiver.ACTION)
        return PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
