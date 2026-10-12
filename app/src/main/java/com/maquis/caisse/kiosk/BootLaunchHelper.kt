package com.maquis.caisse.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.maquis.caisse.MainActivity

/**
 * Lance NexaGes après boot malgré les restrictions Android 10+
 * (interdiction fréquente de startActivity depuis un BroadcastReceiver).
 */
object BootLaunchHelper {
    private const val TAG = "NexaBootLaunch"
    private const val REQ_IMMEDIATE = 7101
    private const val REQ_DELAYED_SHORT = 7102
    private const val REQ_DELAYED_LONG = 7103

    fun launchApp(context: Context, fromBoot: Boolean) {
        val app = context.applicationContext
        val launch = buildLaunchIntent(app, fromBoot)

        var started = false
        try {
            app.startActivity(launch)
            started = true
            Log.i(TAG, "startActivity OK")
        } catch (e: Exception) {
            Log.w(TAG, "startActivity bloqué: ${e.message}")
        }

        // Filet de secours : alarmes (souvent autorisées après BOOT_COMPLETED).
        schedule(app, launch, REQ_DELAYED_SHORT, delayMs = 2_500L)
        schedule(app, launch, REQ_DELAYED_LONG, delayMs = 10_000L)
        if (!started) {
            schedule(app, launch, REQ_IMMEDIATE, delayMs = 500L)
        }
    }

    fun buildLaunchIntent(context: Context, fromBoot: Boolean): Intent =
        Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            if (fromBoot) {
                putExtra(BootCompletedReceiver.EXTRA_FROM_BOOT, true)
            }
        }

    private fun schedule(context: Context, launch: Intent, requestCode: Int, delayMs: Long) {
        try {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getActivity(
                context,
                requestCode,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val trigger = SystemClock.elapsedRealtime() + delayMs
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                } else {
                    @Suppress("DEPRECATION")
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                }
            } catch (_: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                } else {
                    @Suppress("DEPRECATION")
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                }
            }
            Log.i(TAG, "Alarme planifiée +${delayMs}ms (code=$requestCode)")
        } catch (e: Exception) {
            Log.w(TAG, "Alarme impossible: ${e.message}")
        }
    }
}
