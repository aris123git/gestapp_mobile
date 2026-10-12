package com.maquis.caisse.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.maquis.caisse.data.local.AppDatabase
import com.maquis.caisse.data.local.image.ProductImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sauvegarde / restauration.
 *
 * Sources acceptées :
 * - ZIP exporté depuis Paramètres (`db/…`, `images/…`)
 * - Fichier `.db` brut
 * - ZIP ou **dossier** de données Android de l'app
 *   (`databases/maquis_caisse.db`, `files/product_images/`, `shared_prefs/`)
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(Date())
        return "nexages_backup_$stamp.zip"
    }

    suspend fun exportToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            checkpointWal()
            val dbFile = context.getDatabasePath(DB_NAME)
            require(dbFile.exists()) { "Base de données introuvable" }
            require(dbFile.length() > 100L) { "Base de données vide ou corrompue" }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                    putFile(zip, dbFile, "db/$DB_NAME")
                    val imagesDir = File(context.filesDir, ProductImageStore.RELATIVE_DIR)
                    if (imagesDir.isDirectory) {
                        imagesDir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relative = file.relativeTo(imagesDir).path.replace('\\', '/')
                            putFile(zip, file, "images/$relative")
                        }
                    }
                    sharedPrefsDir()?.listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".xml") }
                        ?.forEach { putFile(zip, it, "shared_prefs/${it.name}") }

                    zip.putNextEntry(ZipEntry("meta.txt"))
                    zip.write(
                        buildString {
                            appendLine("maquis_caisse_backup")
                            appendLine("version=3")
                            appendLine("db=$DB_NAME")
                            appendLine("created=${System.currentTimeMillis()}")
                            appendLine("app=${context.packageName}")
                        }.toByteArray(Charsets.UTF_8),
                    )
                    zip.closeEntry()
                }
            } ?: error("Impossible d'écrire le fichier de sauvegarde")
            Unit
        }.onFailure { Log.e(TAG, "Export échoué", it) }
            .map { }
    }

    /** Restaure depuis un fichier (.zip / .db). */
    suspend fun importFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = newStaging()
            try {
                materializeUriToStaging(uri, staging)
                applyRestoreFromStaging(staging)
            } finally {
                staging.deleteRecursively()
            }
        }.onFailure { Log.e(TAG, "Restauration fichier échouée", it) }
            .map { }
    }

    /**
     * Restaure depuis un **dossier** de données app (copie USB / extrait Replit / backup).
     * Exemple :
     * `com.maquis.caisse/databases/maquis_caisse.db` + `files/product_images/` + `shared_prefs/`
     */
    suspend fun importFromTreeUri(treeUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Dossier inaccessible. Autorise l'accès au dossier dans le sélecteur.")
            val staging = newStaging()
            try {
                copyDocumentTree(root, staging)
                require(staging.walkTopDown().any { it.isFile }) { "Dossier vide ou illisible" }
                applyRestoreFromStaging(staging)
            } finally {
                staging.deleteRecursively()
            }
        }.onFailure { Log.e(TAG, "Restauration dossier échouée", it) }
            .map { }
    }

    fun restartApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Relance après restore échouée", e)
        }
        Process.killProcess(Process.myPid())
        Runtime.getRuntime().exit(0)
    }

    private fun newStaging(): File =
        File(context.cacheDir, "restore_${System.currentTimeMillis()}").also { it.mkdirs() }

    private fun materializeUriToStaging(uri: Uri, staging: File) {
        val displayName = (
            uri.lastPathSegment
                ?: context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
                }
            ).orEmpty().lowercase(Locale.ROOT)

        val looksLikeDb = displayName.endsWith(".db") ||
            (displayName.contains("maquis_caisse") && !displayName.endsWith(".zip"))

        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffered = BufferedInputStream(input)
            buffered.mark(8)
            val header = ByteArray(4)
            val n = buffered.read(header)
            buffered.reset()
            val isZip = n >= 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
            when {
                isZip -> unzip(buffered, staging)
                looksLikeDb || header.decodeToString().startsWith("SQLi") -> {
                    val raw = File(staging, "databases/$DB_NAME")
                    raw.parentFile?.mkdirs()
                    FileOutputStream(raw).use { out -> buffered.copyTo(out) }
                }
                else -> unzip(buffered, staging)
            }
        } ?: error(
            "Impossible de lire le fichier. " +
                "Si tu as un dossier app, utilise « Restaurer depuis un dossier ».",
        )
    }

    private fun applyRestoreFromStaging(staging: File) {
        val dbSrc = findDatabaseFile(staging)
            ?: error(
                "Base « $DB_NAME » introuvable.\n" +
                    "Dans ton ancien dossier, cherche :\n" +
                    "• databases/maquis_caisse.db\n" +
                    "• ou db/maquis_caisse.db\n" +
                    "Puis sélectionne ce dossier (ou zippe-le).",
            )
        validateSqliteFile(dbSrc)

        val walSrc = findSideFile(staging, dbSrc, "-wal")
        val shmSrc = findSideFile(staging, dbSrc, "-shm")
        val imagesStaging = findImagesDir(staging)
        val prefsStaging = findSharedPrefsDir(staging)

        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        } catch (e: Exception) {
            Log.w(TAG, "Checkpoint avant restore ignoré: ${e.message}")
        }
        database.close()

        val dbPath = context.getDatabasePath(DB_NAME)
        val dbDir = dbPath.parentFile ?: error("Répertoire base introuvable")
        dbDir.mkdirs()

        deleteQuietly(File(dbDir, DB_NAME))
        deleteQuietly(File(dbDir, "$DB_NAME-wal"))
        deleteQuietly(File(dbDir, "$DB_NAME-shm"))
        deleteQuietly(File(dbDir, "$DB_NAME-journal"))

        copyAtomic(dbSrc, File(dbDir, DB_NAME))
        if (walSrc != null && walSrc.length() > 0L) {
            copyAtomic(walSrc, File(dbDir, "$DB_NAME-wal"))
            if (shmSrc != null && shmSrc.length() > 0L) {
                copyAtomic(shmSrc, File(dbDir, "$DB_NAME-shm"))
            }
        }

        if (imagesStaging != null && imagesStaging.isDirectory) {
            val imagesDir = File(context.filesDir, ProductImageStore.RELATIVE_DIR)
            if (imagesDir.exists()) imagesDir.deleteRecursively()
            imagesDir.mkdirs()
            imagesStaging.copyRecursively(imagesDir, overwrite = true)
        }

        val destPrefs = sharedPrefsDir()
        if (prefsStaging != null && prefsStaging.isDirectory && destPrefs != null) {
            destPrefs.mkdirs()
            prefsStaging.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
                ?.forEach { src -> copyAtomic(src, File(destPrefs, src.name)) }
        }

        Log.i(
            TAG,
            "Restauration OK db=${dbSrc.length()}B images=${imagesStaging != null} prefs=${prefsStaging != null}",
        )
    }

    private suspend fun checkpointWal() {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
        database.withTransaction { }
    }

    private fun putFile(zip: ZipOutputStream, file: File, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun unzip(input: java.io.InputStream, destDir: File) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                val name = normalizeZipPath(entry.name)
                if (name.isBlank() || name.startsWith("__MACOSX/") || name.endsWith(".DS_Store")) {
                    zip.closeEntry()
                    entry = zip.nextEntry
                    continue
                }
                val outFile = File(destDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    val canonicalDest = destDir.canonicalFile
                    val canonicalOut = outFile.canonicalFile
                    require(canonicalOut.path.startsWith(canonicalDest.path + File.separator)) {
                        "Entrée ZIP invalide : ${entry.name}"
                    }
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                    count++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
            require(count > 0) { "Archive ZIP vide ou illisible" }
        }
    }

    private fun copyDocumentTree(root: DocumentFile, destDir: File) {
        fun walk(node: DocumentFile, target: File) {
            if (node.isDirectory) {
                target.mkdirs()
                node.listFiles().forEach { child ->
                    val name = child.name ?: return@forEach
                    if (name.startsWith(".")) return@forEach
                    walk(child, File(target, name))
                }
            } else if (node.isFile) {
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(node.uri)?.use { input ->
                    FileOutputStream(target).use { out -> input.copyTo(out) }
                }
            }
        }
        walk(root, destDir)
    }

    private fun normalizeZipPath(raw: String): String =
        raw.replace('\\', '/')
            .trimStart('/')
            .removePrefix("./")

    private fun findDatabaseFile(staging: File): File? {
        val candidates = staging.walkTopDown()
            .filter { it.isFile && it.length() > 100L }
            .filter { file ->
                val n = file.name.lowercase(Locale.ROOT)
                !n.contains("-wal") && !n.contains("-shm") && !n.contains("-journal") &&
                    (n == DB_NAME.lowercase(Locale.ROOT) ||
                        n == "maquis_caisse.db" ||
                        n == "nexages.db" ||
                        n.endsWith(".db"))
            }
            .toList()

        fun parentIs(file: File, name: String) =
            file.parentFile?.name.equals(name, ignoreCase = true) == true

        candidates.firstOrNull {
            parentIs(it, "databases") && it.name.equals(DB_NAME, true)
        }?.let { return it }

        candidates.firstOrNull {
            parentIs(it, "db") && it.name.equals(DB_NAME, true)
        }?.let { return it }

        candidates.firstOrNull { it.name.equals(DB_NAME, true) }?.let { return it }
        candidates.firstOrNull { it.name.equals("maquis_caisse.db", true) }?.let { return it }

        val sqliteOnly = candidates.filter {
            runCatching { validateSqliteFile(it); true }.getOrDefault(false)
        }
        if (sqliteOnly.size == 1) return sqliteOnly.first()
        return sqliteOnly.maxByOrNull { it.length() }
            ?: candidates.maxByOrNull { it.length() }
    }

    private fun findSideFile(staging: File, dbFile: File, suffix: String): File? {
        val sibling = File(dbFile.parentFile, dbFile.name + suffix)
        if (sibling.isFile) return sibling
        return staging.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals(DB_NAME + suffix, ignoreCase = true) }
    }

    private fun findImagesDir(staging: File): File? {
        staging.walkTopDown()
            .firstOrNull {
                it.isDirectory && it.name.equals(ProductImageStore.RELATIVE_DIR, ignoreCase = true)
            }?.let { return it }
        File(staging, "images").takeIf { it.isDirectory }?.let { return it }
        File(staging, "files/product_images").takeIf { it.isDirectory }?.let { return it }
        return staging.walkTopDown()
            .firstOrNull { it.isDirectory && it.name.equals("images", ignoreCase = true) }
    }

    private fun findSharedPrefsDir(staging: File): File? {
        File(staging, "shared_prefs").takeIf { it.isDirectory }?.let { return it }
        return staging.walkTopDown()
            .firstOrNull { it.isDirectory && it.name.equals("shared_prefs", ignoreCase = true) }
    }

    private fun sharedPrefsDir(): File? {
        val dataDir = context.applicationInfo.dataDir ?: return null
        return File(dataDir, "shared_prefs")
    }

    private fun validateSqliteFile(file: File) {
        require(file.isFile && file.length() > 100L) {
            "Fichier base trop petit ou manquant (${file.length()} octets)"
        }
        FileInputStream(file).use { input ->
            val header = ByteArray(16)
            val read = input.read(header)
            require(read == 16) { "Impossible de lire l'en-tête SQLite" }
            val magic = header.decodeToString()
            require(magic.startsWith("SQLite format 3")) {
                "Le fichier n'est pas une base SQLite valide"
            }
        }
    }

    private fun copyAtomic(src: File, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        deleteQuietly(tmp)
        src.copyTo(tmp, overwrite = true)
        FileOutputStream(tmp, true).use { it.fd.sync() }
        if (dest.exists()) deleteQuietly(dest)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            deleteQuietly(tmp)
            FileOutputStream(dest, true).use { it.fd.sync() }
        }
        require(dest.exists() && dest.length() == src.length()) {
            "Échec copie (${dest.length()} ≠ ${src.length()})"
        }
    }

    private fun deleteQuietly(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        private const val TAG = "NexaBackup"
        const val DB_NAME = "maquis_caisse.db"
        const val MIME_ZIP = "application/zip"
    }
}
