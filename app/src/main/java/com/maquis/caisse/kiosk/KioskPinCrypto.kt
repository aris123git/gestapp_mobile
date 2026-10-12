package com.maquis.caisse.kiosk

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hachage du PIN administrateur kiosque (jamais stocké en clair).
 * Indépendant des codes PIN caissier / utilisateur.
 */
object KioskPinCrypto {
    private const val HASH_ALGORITHM = "SHA-256"
    private const val SALT_BYTES = 16

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return toHex(digest.digest())
    }

    fun verify(pin: String, saltHex: String, hashHex: String): Boolean {
        if (saltHex.isBlank() || hashHex.isBlank()) return false
        val salt = fromHex(saltHex)
        val computed = hashPin(pin, salt)
        return MessageDigest.isEqual(
            computed.toByteArray(Charsets.UTF_8),
            hashHex.toByteArray(Charsets.UTF_8),
        )
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { b -> "%02x".format(b) }

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "hex invalide" }
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
