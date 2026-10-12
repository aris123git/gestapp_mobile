package com.maquis.caisse.kiosk

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class KioskState(
    val enabled: Boolean = false,
    val autoStart: Boolean = false,
    val temporarilyUnlocked: Boolean = false,
    val hasAdminPin: Boolean = false,
    val lastError: String? = null,
) {
    val shouldLockNow: Boolean get() = enabled && !temporarilyUnlocked
}

/**
 * Stockage local du mode kiosque et du secret PIN admin (hash + sel uniquement).
 */
@Singleton
class KioskSecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<KioskState> = _state.asStateFlow()

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).commit()
            publish()
        }

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_START, value).commit()
            publish()
        }

    /**
     * Sortie admin temporaire avec expiration (pas jusqu'au reboot).
     * Si le délai est dépassé, [consumeExpiredUnlock] rétablit le verrou.
     */
    var temporarilyUnlocked: Boolean
        get() {
            consumeExpiredUnlock()
            return prefs.getBoolean(KEY_TEMP_UNLOCK, false)
        }
        set(value) {
            val editor = prefs.edit().putBoolean(KEY_TEMP_UNLOCK, value)
            if (value) {
                editor.putLong(KEY_TEMP_UNLOCK_UNTIL, System.currentTimeMillis() + TEMP_UNLOCK_MS)
            } else {
                editor.putLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
            }
            editor.commit()
            publish()
        }

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_ERROR, value).commit()
            publish()
        }

    fun hasAdminPin(): Boolean =
        !prefs.getString(KEY_PIN_HASH, null).isNullOrBlank() &&
            !prefs.getString(KEY_PIN_SALT, null).isNullOrBlank()

    fun setAdminPin(pin: String) {
        require(pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it.isDigit() }) {
            "PIN administrateur : $MIN_PIN_LENGTH à $MAX_PIN_LENGTH chiffres"
        }
        val salt = KioskPinCrypto.generateSalt()
        val hash = KioskPinCrypto.hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, KioskPinCrypto.toHex(salt))
            .putString(KEY_PIN_HASH, hash)
            .putInt(KEY_FAILED, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
        publish()
    }

    fun verifyAdminPin(pin: String): PinVerifyResult {
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockoutUntil) {
            val secs = ((lockoutUntil - now) / 1000L).coerceAtLeast(1L)
            return PinVerifyResult.LockedOut(secs)
        }
        if (!hasAdminPin()) {
            return PinVerifyResult.NoPinSet
        }
        val salt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        val hash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        val ok = KioskPinCrypto.verify(pin, salt, hash)
        return if (ok) {
            prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).commit()
            PinVerifyResult.Ok
        } else {
            val fails = prefs.getInt(KEY_FAILED, 0) + 1
            if (fails >= MAX_ATTEMPTS) {
                val until = now + lockoutDurationMs(fails)
                prefs.edit()
                    .putInt(KEY_FAILED, fails)
                    .putLong(KEY_LOCKOUT_UNTIL, until)
                    .commit()
                PinVerifyResult.LockedOut(((until - now) / 1000L).coerceAtLeast(1L))
            } else {
                prefs.edit().putInt(KEY_FAILED, fails).commit()
                PinVerifyResult.Wrong(remaining = MAX_ATTEMPTS - fails)
            }
        }
    }

    fun clearTemporarilyUnlockedOnBoot() {
        temporarilyUnlocked = false
    }

    /** true si une sortie temporaire venait d'expirer (relock nécessaire). */
    fun consumeExpiredUnlock(): Boolean {
        if (!prefs.getBoolean(KEY_TEMP_UNLOCK, false)) return false
        val until = prefs.getLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
        if (until > 0L && System.currentTimeMillis() >= until) {
            prefs.edit()
                .putBoolean(KEY_TEMP_UNLOCK, false)
                .putLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
                .commit()
            publish()
            return true
        }
        return false
    }

    fun tempUnlockRemainingMs(): Long {
        if (!prefs.getBoolean(KEY_TEMP_UNLOCK, false)) return 0L
        val until = prefs.getLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun clearLastError() {
        lastError = null
    }

    private fun lockoutDurationMs(failCount: Int): Long {
        val minutes = when {
            failCount >= MAX_ATTEMPTS + 4 -> 30
            failCount >= MAX_ATTEMPTS + 2 -> 15
            else -> 5
        }
        return minutes * 60_000L
    }

    private fun readState(): KioskState {
        // Expire avant lecture UI.
        if (prefs.getBoolean(KEY_TEMP_UNLOCK, false)) {
            val until = prefs.getLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
            if (until > 0L && System.currentTimeMillis() >= until) {
                prefs.edit()
                    .putBoolean(KEY_TEMP_UNLOCK, false)
                    .putLong(KEY_TEMP_UNLOCK_UNTIL, 0L)
                    .commit()
            }
        }
        return KioskState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            autoStart = prefs.getBoolean(KEY_AUTO_START, false),
            temporarilyUnlocked = prefs.getBoolean(KEY_TEMP_UNLOCK, false),
            hasAdminPin = hasAdminPin(),
            lastError = prefs.getString(KEY_LAST_ERROR, null),
        )
    }

    private fun publish() {
        _state.update { readState() }
    }

    sealed class PinVerifyResult {
        data object Ok : PinVerifyResult()
        data object NoPinSet : PinVerifyResult()
        data class Wrong(val remaining: Int) : PinVerifyResult()
        data class LockedOut(val secondsRemaining: Long) : PinVerifyResult()
    }

    companion object {
        const val PREFS_NAME = "nexapos_kiosk_secure"
        private const val KEY_ENABLED = "kiosk_enabled"
        private const val KEY_AUTO_START = "kiosk_auto_start"
        private const val KEY_TEMP_UNLOCK = "kiosk_temp_unlock"
        private const val KEY_TEMP_UNLOCK_UNTIL = "kiosk_temp_unlock_until"
        private const val KEY_PIN_HASH = "admin_pin_hash"
        private const val KEY_PIN_SALT = "admin_pin_salt"
        private const val KEY_FAILED = "admin_pin_failed"
        private const val KEY_LOCKOUT_UNTIL = "admin_pin_lockout_until"
        private const val KEY_LAST_ERROR = "kiosk_last_error"

        /** Sortie temporaire : 3 minutes puis re-verrouillage auto. */
        const val TEMP_UNLOCK_MS = 3L * 60L * 1000L

        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
        const val MAX_ATTEMPTS = 5
    }
}
