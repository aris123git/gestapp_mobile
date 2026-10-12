package com.maquis.caisse.data.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * Impression du ticket sous forme d'IMAGE (raster ESC/POS « GS v 0 »).
 *
 * Le texte est dessiné par Android (accents, €, etc. corrects) puis envoyé en
 * points noir/blanc : plus aucun problème de page de codes ni de mode Kanji.
 */
object TicketRasterizer {

    /** Largeur imprimable en points : 58 mm = 384, 80 mm = 576 (multiples de 8). */
    fun widthDots(paperMm: Int): Int = if (paperMm >= 80) 576 else 384

    /** Nombre de colonnes de texte (identique aux lignes déjà formatées par center()). */
    fun columns(paperMm: Int): Int = if (paperMm >= 80) 48 else 32

    /** Dessine les lignes en police à chasse fixe sur fond blanc. */
    fun render(lines: List<String>, paperMm: Int): Bitmap {
        val width = widthDots(paperMm)
        val cols = columns(paperMm)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }
        // Ajuste la taille pour que `cols` caractères remplissent exactement la largeur.
        val charW = paint.measureText("0").coerceAtLeast(1f)
        paint.textSize = 20f * (width.toFloat() / cols) / charW

        // Retour à la ligne si une ligne dépasse la largeur.
        val rows = lines.flatMap { wrap(it, cols) }

        val fm = paint.fontMetrics
        val lineH = (fm.descent - fm.ascent + 4f)
        val padding = 8
        val height = (rows.size * lineH).toInt() + padding * 2 + 24 // marge de fin avant coupe

        val bmp = Bitmap.createBitmap(width, height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        var y = padding - fm.ascent
        for (row in rows) {
            canvas.drawText(row, 0f, y, paint)
            y += lineH
        }
        return bmp
    }

    internal fun wrap(line: String, cols: Int): List<String> {
        val clean = line.replace(' ', ' ').replace(' ', ' ').replace("\t", " ")
        if (clean.length <= cols) return listOf(clean)
        return clean.chunked(cols)
    }

    /**
     * Convertit des pixels ARGB en commandes GS v 0 (bandes de [stripRows] lignes).
     * Pixel noir si la luminosité (sur fond blanc) < 128.
     */
    fun toRasterCommands(
        pixels: IntArray,
        width: Int,
        height: Int,
        stripRows: Int = 128,
    ): ByteArray {
        require(width % 8 == 0) { "La largeur doit être un multiple de 8" }
        val bytesPerRow = width / 8
        val out = ByteArrayOutputStream()
        var y0 = 0
        while (y0 < height) {
            val h = minOf(stripRows, height - y0)
            // GS v 0 m xL xH yL yH d1..dk   (m = 0 : densité normale)
            out.write(0x1D); out.write(0x76); out.write(0x30); out.write(0x00)
            out.write(bytesPerRow and 0xFF); out.write((bytesPerRow shr 8) and 0xFF)
            out.write(h and 0xFF); out.write((h shr 8) and 0xFF)
            for (y in y0 until y0 + h) {
                for (bx in 0 until bytesPerRow) {
                    var b = 0
                    for (bit in 0 until 8) {
                        val argb = pixels[y * width + bx * 8 + bit]
                        if (isBlack(argb)) b = b or (0x80 shr bit)
                    }
                    out.write(b)
                }
            }
            y0 += h
        }
        return out.toByteArray()
    }

    fun toRasterCommands(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        return toRasterCommands(px, w, h)
    }

    private fun isBlack(argb: Int): Boolean {
        val a = (argb ushr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // Composite sur blanc puis luminosité.
        val rr = (r * a + 255 * (255 - a)) / 255
        val gg = (g * a + 255 * (255 - a)) / 255
        val bb = (b * a + 255 * (255 - a)) / 255
        return (rr * 299 + gg * 587 + bb * 114) / 1000 < 128
    }
}
