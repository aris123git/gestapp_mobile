package com.maquis.caisse.data.local.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage privé des images produit : copie depuis une Uri (galerie/caméra),
 * redimensionnement + compression JPEG (~200–300 Ko), chemin relatif persisté.
 */
@Singleton
class ProductImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val imagesDir: File
        get() = File(context.filesDir, RELATIVE_DIR).also { it.mkdirs() }

    /**
     * Compresse [sourceUri] et l'enregistre sous `product_images/…`.
     * @return chemin relatif à [Context.getFilesDir]
     * @throws IllegalStateException si l'image ne peut pas être lue/écrite
     */
    suspend fun saveFromUri(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(sourceUri)
            ?: error("Impossible de lire l'image sélectionnée")

        val scaled = scaleToMaxSide(bitmap, MAX_SIDE_PX)
        if (scaled !== bitmap) bitmap.recycle()

        val jpegBytes = compressToTarget(scaled)
        scaled.recycle()

        val fileName = "${UUID.randomUUID()}.jpg"
        val outFile = File(imagesDir, fileName)
        outFile.outputStream().use { it.write(jpegBytes) }
        "$RELATIVE_DIR/$fileName"
    }

    /** Supprime une image existante (remplacement ou produit sans photo). */
    suspend fun deleteIfExists(relativePath: String?) = withContext(Dispatchers.IO) {
        if (relativePath.isNullOrBlank()) return@withContext
        val file = resolveFile(relativePath)
        if (file.exists()) file.delete()
    }

    /** Résout un chemin relatif en fichier absolu du stockage privé. */
    fun resolveFile(relativePath: String): File = File(context.filesDir, relativePath)

    private fun decodeBitmap(sourceUri: Uri): Bitmap? {
        // 1) ImageDecoder (HEIC / formats modernes, API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } catch (_: Exception) {
                // fallback BitmapFactory
            }
        }

        // 2) BitmapFactory avec inSampleSize
        // IMPORTANT : avec inJustDecodeBounds=true, decodeStream renvoie toujours null —
        // ne pas combiner avec `?: return null` sur le résultat du decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return copyRawAsBitmap(sourceUri)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return copyRawAsBitmap(sourceUri)
        }

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxSide = MAX_SIDE_PX,
        )
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        }
        return decoded ?: copyRawAsBitmap(sourceUri)
    }

    /** Dernier recours : recopier les bytes bruts puis tenter un decode fichier. */
    private fun copyRawAsBitmap(sourceUri: Uri): Bitmap? {
        return try {
            val temp = File(context.cacheDir, "img_import_${UUID.randomUUID()}")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            val bmp = BitmapFactory.decodeFile(temp.absolutePath)
            temp.delete()
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleToMaxSide(source: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSide) return source
        val ratio = maxSide.toFloat() / longest
        val w = (source.width * ratio).toInt().coerceAtLeast(1)
        val h = (source.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun compressToTarget(bitmap: Bitmap): ByteArray {
        var quality = 85
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            val ok = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            if (!ok) error("Compression JPEG impossible")
            bytes = stream.toByteArray()
            quality -= 10
        } while (bytes.size > TARGET_MAX_BYTES && quality >= MIN_QUALITY)
        return bytes
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxSide || h / 2 >= maxSide) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }

    companion object {
        const val RELATIVE_DIR = "product_images"
        const val MAX_SIDE_PX = 1024
        const val TARGET_MAX_BYTES = 300 * 1024
        const val MIN_QUALITY = 45
    }
}
