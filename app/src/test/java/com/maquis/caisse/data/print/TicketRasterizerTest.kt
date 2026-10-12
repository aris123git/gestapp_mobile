package com.maquis.caisse.data.print

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TicketRasterizerTest {

    @Test
    fun `en-tete GS v 0 et bits corrects`() {
        val w = 16
        val h = 2
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        px[0] = 0xFF000000.toInt()          // ligne 0, bit de gauche noir
        px[w + 15] = 0xFF000000.toInt()     // ligne 1, bit de droite noir
        val out = TicketRasterizer.toRasterCommands(px, w, h)
        assertArrayEquals(
            byteArrayOf(
                0x1D, 0x76, 0x30, 0x00, 2, 0, 2, 0,
                0x80.toByte(), 0x00,
                0x00, 0x01,
            ),
            out,
        )
    }

    @Test
    fun `decoupe en bandes`() {
        val w = 8
        val h = 300
        val out = TicketRasterizer.toRasterCommands(IntArray(w * h) { -1 }, w, h, stripRows = 128)
        // 3 bandes : 128 + 128 + 44, chacune = 8 octets d'en-tete + h octets
        assertEquals(3 * 8 + 300, out.size)
    }

    @Test
    fun `transparent compte comme blanc`() {
        val out = TicketRasterizer.toRasterCommands(IntArray(8) { 0 }, 8, 1)
        assertEquals(0, out.last().toInt())
    }

    @Test
    fun `retour a la ligne`() {
        assertEquals(listOf("abc", "de"), TicketRasterizer.wrap("abcde", 3))
        assertEquals(576, TicketRasterizer.widthDots(80))
        assertEquals(384, TicketRasterizer.widthDots(58))
    }
}
