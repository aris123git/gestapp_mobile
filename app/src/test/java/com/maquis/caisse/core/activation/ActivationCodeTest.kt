package com.maquis.caisse.core.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ActivationCodeTest {

    @Test
    fun decodedKeyMatchesExpectedFingerprint() {
        // Vérifie le décodage sans exposer le littéral maître dans le code prod.
        val decoded = ActivationService.decodeMasterKey()
        val token = sha256(decoded.filterNot { it.isWhitespace() }.uppercase())
        assertEquals(ActivationService.expectedTokenForTests(), token)
        assertTrue(decoded.length >= 12)
        assertTrue(decoded.contains('-'))
    }

    @Test
    fun wrongCodeDoesNotMatchFingerprint() {
        val wrong = "ARIS-0000-NEXA-0000"
        val token = sha256(wrong.filterNot { it.isWhitespace() }.uppercase())
        assertFalse(token == ActivationService.expectedTokenForTests())
    }

    @Test
    fun normalizeIgnoresCaseAndSpaces() {
        val a = ActivationService.decodeMasterKey()
        val spaced = " ${a.lowercase()} "
        assertEquals(
            sha256(a.filterNot { it.isWhitespace() }.uppercase()),
            sha256(spaced.filterNot { it.isWhitespace() }.uppercase()),
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
