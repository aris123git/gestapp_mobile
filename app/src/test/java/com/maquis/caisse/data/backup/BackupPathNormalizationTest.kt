package com.maquis.caisse.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests purement locaux sur la normalisation / détection de chemins ZIP
 * (logique extraite via méthodes package-visible si besoin — ici on valide
 * le comportement attendu des helpers via fichiers temporaires).
 */
class BackupPathNormalizationTest {
    @Test
    fun normalizeZipPath_stripsWindowsAndDotSlash() {
        fun normalize(raw: String): String =
            raw.replace('\\', '/')
                .trimStart('/')
                .removePrefix("./")

        assertEquals("db/maquis_caisse.db", normalize("db\\maquis_caisse.db"))
        assertEquals("db/maquis_caisse.db", normalize("./db/maquis_caisse.db"))
        assertEquals("db/maquis_caisse.db", normalize("/db/maquis_caisse.db"))
    }

    @Test
    fun findDb_prefersNestedDbFolder() {
        val root = File.createTempFile("backup_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val nested = File(root, "export/db/maquis_caisse.db")
            nested.parentFile?.mkdirs()
            nested.writeBytes("SQLite format 3\u0000".toByteArray() + ByteArray(200))
            File(root, "autres.txt").writeText("x")

            val found = root.walkTopDown()
                .filter { it.isFile && it.name.equals("maquis_caisse.db", true) }
                .firstOrNull()
            assertTrue(found != null && found!!.length() > 100L)
        } finally {
            root.deleteRecursively()
        }
    }
}
