package com.maquis.caisse.core.activation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Activation hors-ligne liée à l'appareil.
 *
 * Le code maître n'est **jamais** stocké ni exposé en clair :
 * seule une empreinte SHA-256 (dérivée de segments obfuscated) est utilisée.
 */
@Singleton
class ActivationService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun currentDeviceId(): String {
        val imei = readImei(context)
        val androidId = androidId(context)
        return if (!imei.isNullOrBlank()) "imei:$imei" else "aid:$androidId"
    }

    /** Identifiant appareil masqué pour l'UI (jamais l'IMEI brut). */
    fun maskedDeviceLabel(): String {
        val raw = currentDeviceId()
        val body = raw.substringAfter(':')
        if (body.length <= 6) return "Appareil ••••"
        return "Appareil …${body.takeLast(4)}"
    }

    fun isActivated(): Boolean {
        val token = prefs.getString(KEY_TOKEN, null) ?: return false
        if (token != expectedToken()) return false

        val storedAid = prefs.getString(KEY_ANDROID_ID, null) ?: return false
        val currentAid = androidId(context)
        if (storedAid != currentAid) return false

        val storedImei = prefs.getString(KEY_IMEI, null)
        val currentImei = readImei(context)
        if (!storedImei.isNullOrBlank() && !currentImei.isNullOrBlank() && storedImei != currentImei) {
            return false
        }
        if (storedImei.isNullOrBlank() && !currentImei.isNullOrBlank()) {
            prefs.edit().putString(KEY_IMEI, currentImei).apply()
        }
        return true
    }

    fun isDeviceMismatch(): Boolean {
        val token = prefs.getString(KEY_TOKEN, null) ?: return false
        if (token != expectedToken()) return false
        if (prefs.getString(KEY_ANDROID_ID, null) == null) return false
        return !isActivated()
    }

    fun verifyCode(code: String): Boolean =
        sha256(normalize(code)) == expectedToken()

    fun activate(code: String): Boolean {
        if (!verifyCode(code)) return false
        prefs.edit()
            .putString(KEY_TOKEN, expectedToken())
            .putString(KEY_ANDROID_ID, androidId(context))
            .putString(KEY_IMEI, readImei(context))
            .putLong(KEY_ACTIVATED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    fun clearActivation() {
        prefs.edit().clear().apply()
    }

    private fun expectedToken(): String = sha256(normalize(decodeMasterKey()))

    private fun normalize(code: String): String =
        code.filterNot { it.isWhitespace() }.uppercase()

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "nexages_activation"
        private const val KEY_TOKEN = "token"
        private const val KEY_ANDROID_ID = "android_id"
        private const val KEY_IMEI = "imei"
        private const val KEY_ACTIVATED_AT = "activated_at"

        /**
         * Segments XOR-obfusqués — le code complet n'apparaît jamais en clair dans le bytecode
         * sous forme de littéral unique.
         */
        private val OBF_A = intArrayOf(0x16, 0x05, 0x1E, 0x04, 0x7A)
        private val OBF_B = intArrayOf(0x65, 0x67, 0x65, 0x61, 0x7A)
        private val OBF_C = intArrayOf(0x19, 0x12, 0x0F, 0x16, 0x7A)
        private val OBF_D = intArrayOf(0x62, 0x64, 0x61, 0x64)
        private const val XOR_KEY = 0x57

        /** Décode le code maître en mémoire uniquement (jamais loggé / exposé). */
        internal fun decodeMasterKey(): String {
            fun decode(parts: IntArray): String =
                parts.map { (it xor XOR_KEY).toChar() }.joinToString("")
            return decode(OBF_A) + decode(OBF_B) + decode(OBF_C) + decode(OBF_D)
        }

        /** Empreinte attendue (pour tests) — pas le code en clair. */
        internal fun expectedTokenForTests(): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(decodeMasterKey().filterNot { it.isWhitespace() }.uppercase().toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        @SuppressLint("HardwareIds")
        fun androidId(context: Context): String =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                .orEmpty()
                .ifBlank { "unknown" }

        @SuppressLint("HardwareIds", "MissingPermission")
        fun readImei(context: Context): String? {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return null
            return try {
                val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        tm.imei?.takeIf { it.isNotBlank() }
                            ?: tm.meid?.takeIf { it.isNotBlank() }
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        tm.deviceId?.takeIf { it.isNotBlank() }
                    }
                }
            } catch (_: SecurityException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}
