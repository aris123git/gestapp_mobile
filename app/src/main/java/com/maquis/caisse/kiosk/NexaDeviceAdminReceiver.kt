package com.maquis.caisse.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver Device Admin / Device Owner pour Lock Task Mode renforcé.
 * Activation Device Owner (tablette dédiée caisse) :
 *   adb shell dpm set-device-owner com.maquis.caisse/.kiosk.NexaDeviceAdminReceiver
 */
class NexaDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin disabled")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.i(TAG, "Entering lock task for $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.i(TAG, "Exiting lock task")
    }

    companion object {
        private const val TAG = "NexaKioskAdmin"
    }
}
