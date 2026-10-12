package com.maquis.caisse.data.backup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupZipTest {

    @Test
    fun zip_roundTrip_containsDbEntry() {
        val bytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zip ->
                zip.putNextEntry(ZipEntry("db/${BackupManager.DB_NAME}"))
                zip.write("sqlite-bytes".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("meta.txt"))
                zip.write("maquis_caisse_backup\n".toByteArray())
                zip.closeEntry()
            }
            baos.toByteArray()
        }
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        assertTrue(names.contains("db/${BackupManager.DB_NAME}"))
        assertTrue(names.contains("meta.txt"))
    }
}
