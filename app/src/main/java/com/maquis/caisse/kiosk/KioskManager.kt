package com.maquis.caisse.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maquis.caisse.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mode kiosque tablette dédiée NexaGes : Lock Task + Home + watchdog.
 */
@Singleton
class KioskManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val store: KioskSecureStore,
) {
    private val dpm: DevicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent =
        ComponentName(appContext, NexaDeviceAdminReceiver::class.java)

    val state = store.state

    fun isEnabled(): Boolean = store.enabled

    fun isAutoStartEnabled(): Boolean = store.autoStart

    fun hasAdminPin(): Boolean = store.hasAdminPin()

    fun shouldLockNow(): Boolean {
        store.consumeExpiredUnlock()
        return store.enabled && !store.temporarilyUnlocked
    }

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(appContext.packageName)

    fun lastError(): String? = store.lastError

    fun clearLastError() = store.clearLastError()

    fun tempUnlockRemainingMs(): Long = store.tempUnlockRemainingMs()

    fun isLockTaskActive(activity: Activity): Boolean {
        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            @Suppress("DEPRECATION")
            am.isInLockTaskMode
        }
    }

    /**
     * Entre en kiosque. Retourne false si Lock Task a échoué (message dans [lastError]).
     */
    fun enterKiosk(activity: Activity): Boolean {
        store.consumeExpiredUnlock()
        if (!shouldLockNow()) return false
        prepareDeviceOwnerPolicies()
        hideSystemUi(activity)
        activity.window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        bringTaskToFront(activity)
        var ok = true
        try {
            if (!isLockTaskActive(activity)) {
                activity.startLockTask()
            }
            if (!isLockTaskActive(activity) && !isDeviceOwner()) {
                ok = false
                store.lastError =
                    "Lock Task non actif : sans Device Owner, Android peut refuser. " +
                    "Provisionne Device Owner ou définis NexaGes comme Home."
            } else {
                store.clearLastError()
            }
        } catch (e: Exception) {
            ok = false
            store.lastError = e.message ?: "Échec Lock Task"
        }
        KioskWatchdog.schedule(appContext)
        return ok
    }

    fun exitKiosk(activity: Activity) {
        store.temporarilyUnlocked = true
        try {
            if (isLockTaskActive(activity)) {
                activity.stopLockTask()
            }
        } catch (_: Exception) {
            try {
                activity.stopLockTask()
            } catch (_: Exception) {
            }
        }
        showSystemUi(activity)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Watchdog reprendra le relock après expiration (3 min).
        KioskWatchdog.schedule(appContext)
    }

    fun disableKiosk(activity: Activity) {
        store.enabled = false
        store.temporarilyUnlocked = false
        KioskWatchdog.cancel(appContext)
        try {
            if (isLockTaskActive(activity)) {
                activity.stopLockTask()
            }
        } catch (_: Exception) {
        }
        if (isDeviceOwner()) {
            try {
                dpm.setLockTaskPackages(adminComponent, emptyArray())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    dpm.setKeyguardDisabled(adminComponent, false)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setStatusBarDisabled(adminComponent, false)
                }
            } catch (_: Exception) {
            }
        }
        clearPreferredHomeIfDeviceOwner()
        showSystemUi(activity)
        store.clearLastError()
    }

    fun setEnabled(enabled: Boolean) {
        store.enabled = enabled
        if (enabled) {
            store.autoStart = true
            store.temporarilyUnlocked = false
            prepareDeviceOwnerPolicies()
            setAsPreferredHomeIfDeviceOwner()
            KioskWatchdog.schedule(appContext)
        } else {
            KioskWatchdog.cancel(appContext)
            clearPreferredHomeIfDeviceOwner()
        }
    }

    fun setAutoStart(enabled: Boolean) {
        store.autoStart = enabled
    }

    fun setAdminPin(pin: String) = store.setAdminPin(pin)

    fun verifyAdminPin(pin: String): KioskSecureStore.PinVerifyResult =
        store.verifyAdminPin(pin)

    fun onBootCompleted() {
        store.clearTemporarilyUnlockedOnBoot()
        KioskWatchdog.schedule(appContext)
    }

    /** Déconnexion / fin de session : re-verrouille si kiosque actif. */
    fun onSessionEnded() {
        if (!store.enabled) return
        store.temporarilyUnlocked = false
        store.clearLastError()
        KioskWatchdog.schedule(appContext)
    }

    fun prepareDeviceOwnerPolicies() {
        if (!isDeviceOwner()) return
        try {
            dpm.setLockTaskPackages(adminComponent, arrayOf(appContext.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
                )
                dpm.setStatusBarDisabled(adminComponent, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setKeyguardDisabled(adminComponent, true)
            }
            setAsPreferredHomeIfDeviceOwner()
        } catch (e: Exception) {
            store.lastError = "Device Owner : ${e.message}"
        }
    }

    private fun setAsPreferredHomeIfDeviceOwner() {
        if (!isDeviceOwner()) return
        try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            val activity = ComponentName(appContext, MainActivity::class.java)
            dpm.addPersistentPreferredActivity(adminComponent, filter, activity)
        } catch (_: Exception) {
        }
    }

    private fun clearPreferredHomeIfDeviceOwner() {
        if (!isDeviceOwner()) return
        try {
            dpm.clearPackagePersistentPreferredActivities(adminComponent, appContext.packageName)
        } catch (_: Exception) {
        }
    }

    fun hideSystemUi(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        // Barres système en mode immersif sticky (réapparaissent brièvement au swipe).
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    private fun showSystemUi(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun bringTaskToFront(activity: Activity) {
        try {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(activity.taskId, ActivityManager.MOVE_TASK_WITH_HOME)
        } catch (_: Exception) {
            try {
                val launch = BootLaunchHelper.buildLaunchIntent(activity, fromBoot = false)
                activity.startActivity(launch)
            } catch (_: Exception) {
            }
        }
    }

    /** Intent pour choisir NexaGes comme application d'accueil. */
    fun homeSettingsIntent(): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun requestIgnoreBatteryOptimizationsIntent(): Intent? {
        val pm = appContext.getSystemService(PowerManager::class.java) ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        if (pm.isIgnoringBatteryOptimizations(appContext.packageName)) return null
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = appContext.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    fun deviceOwnerSetupHint(): String =
        "Tablette dédiée recommandée :\n" +
            "1) adb shell dpm set-device-owner " +
            "${appContext.packageName}/.kiosk.NexaDeviceAdminReceiver\n" +
            "2) Réglages → Application d'accueil → NexaGes\n" +
            "3) Ignorer l'optimisation batterie pour NexaGes"
}
