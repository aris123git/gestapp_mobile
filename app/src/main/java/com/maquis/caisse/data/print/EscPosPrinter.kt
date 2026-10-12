package com.maquis.caisse.data.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import com.maquis.caisse.core.SettingsKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import com.maquis.caisse.domain.model.CaisseSession
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.common.MoneyFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Impression thermique Bluetooth ESC/POS (optionnelle).
 * Ne bloque jamais l'app si désactivée ou sans imprimante.
 * 
 * VERSION: Stable avec retry robuste et normalisation des lignes pour imprimantes
 */
@Singleton
class EscPosPrinter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val maxRetries = 3
    private val retryDelayMs = 500L
    private val connectTimeoutMs = 2000L

    /** Returns the BluetoothAdapter using the non-deprecated API on Android 12+ (API 31+). */
    private fun bluetoothAdapter(): BluetoothAdapter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }

    suspend fun isEnabled(): Boolean = settings.isPrintEnabled()

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList().orEmpty()
    }

    suspend fun testPrint(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.isPrintEnabled()) {
            return@withContext Result.failure(IllegalStateException("Impression désactivée"))
        }
        printRaw(buildTestTicket())
    }

    suspend fun printOrder(order: Order): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.isPrintEnabled()) return@withContext Result.success(Unit)
        printRaw(buildOrderTicket(order))
    }

    suspend fun printSessionClosure(session: CaisseSession): Result<Unit> = withContext(Dispatchers.IO) {
        if (!settings.isPrintEnabled()) return@withContext Result.failure(IllegalStateException("Impression désactivée"))
        printRaw(buildSessionClosureTicket(session))
    }

    private suspend fun printRaw(lines: List<String>): Result<Unit> {
        val address = settings.get(SettingsKeys.PRINTER_ADDRESS, "")
        if (address.isBlank()) {
            return Result.failure(IllegalStateException("Aucune imprimante sélectionnée"))
        }

        var lastException: Exception? = null

        // Retry loop avec délai progressif
        for (attempt in 1..maxRetries) {
            try {
                val adapter = bluetoothAdapter()
                    ?: return Result.failure(IllegalStateException("Bluetooth indisponible"))

                val device = adapter.getRemoteDevice(address)

                try {
                    adapter.cancelDiscovery()

                    // Attendre un peu avant de connecter pour éviter les race conditions
                    delay(200)

                    // Création du socket (secure puis insecure en fallback si nécessaire)
                    var socket: BluetoothSocket? = null
                    var triedInsecure = false
                    try {
                        socket = device.createRfcommSocketToServiceRecord(sppUuid)
                    } catch (_: Exception) {
                        // ignore
                    }

                    // Tentative de connect
                    try {
                        socket?.connect()
                    } catch (e: Exception) {
                        // essayer une socket insecure si disponible
                        try {
                            runCatching { adapter.cancelDiscovery() }
                            val insecure = device.createInsecureRfcommSocketToServiceRecord(sppUuid)
                            insecure.connect()
                            socket = insecure
                            triedInsecure = true
                        } catch (e2: Exception) {
                            // Dernier recours : RFCOMM canal 1 (imprimantes cheap / tablettes)
                            try {
                                runCatching { adapter.cancelDiscovery() }
                                runCatching { socket?.close() }
                                val method = device.javaClass.getMethod(
                                    "createRfcommSocket",
                                    Int::class.javaPrimitiveType,
                                )
                                @Suppress("UNCHECKED_CAST")
                                val channel1 = method.invoke(device, 1) as BluetoothSocket
                                channel1.connect()
                                socket = channel1
                            } catch (e3: Exception) {
                                throw e3
                            }
                        }
                    }

                    // Petit délai après connexion pour s'assurer que le socket est vraiment prêt
                    delay(200)

                    // Écrire les données
                    val codepage = codepageSetting()
                    val raster = if (asImageSetting()) buildRaster(lines) else null
                    socket?.outputStream?.use { out ->
                        if (raster != null) writeEscPosImage(out, raster)
                        else writeEscPos(out, lines, codepage)
                    }

                    // Flush supplémentaire pour s'assurer que tout est envoyé
                    delay(100)

                    try {
                        socket?.close()
                    } catch (closeException: Exception) {
                        // Ignorer les erreurs de fermeture
                    }
                    return Result.success(Unit)

                } catch (e: Exception) {
                    lastException = e
                    try {
                        // tentative de cleanup
                        // nothing to do, socket closed in finally above
                    } catch (closeException: Exception) {
                        // Ignorer
                    }

                    // Si ce n'est pas la dernière tentative, attendre avant de réessayer
                    if (attempt < maxRetries) {
                        val delayTime = retryDelayMs * attempt
                        delay(delayTime)
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    val delayTime = retryDelayMs * attempt
                    delay(delayTime)
                }
            }
        }

        // Retourner la dernière exception après tous les essais
        return Result.failure(
            lastException ?: Exception("Impossible de connecter à l'imprimante après $maxRetries tentatives")
        )
    }

    private suspend fun codepageSetting(): Int =
        settings.get(SettingsKeys.PRINTER_CODEPAGE, "0").toIntOrNull() ?: 0

    private suspend fun asImageSetting(): Boolean =
        settings.get(SettingsKeys.PRINT_AS_IMAGE, "1") != "0"

    /** Rend le ticket en image ; null si échec (on retombe alors sur le texte). */
    private suspend fun buildRaster(lines: List<String>): ByteArray? = try {
        val width = settings.get(SettingsKeys.PRINT_WIDTH, "58").toIntOrNull() ?: 58
        val bmp = TicketRasterizer.render(lines, width)
        try {
            TicketRasterizer.toRasterCommands(bmp)
        } finally {
            bmp.recycle()
        }
    } catch (_: Throwable) {
        null
    }

    private fun writeEscPosImage(out: OutputStream, raster: ByteArray) {
        out.write(byteArrayOf(0x1B, 0x40)) // ESC @ — init
        out.write(byteArrayOf(0x1B, 0x61, 0x00)) // alignement gauche
        // Envoi par blocs pour ne pas saturer le tampon Bluetooth de l'imprimante.
        var off = 0
        val chunk = 1024
        while (off < raster.size) {
            val n = minOf(chunk, raster.size - off)
            out.write(raster, off, n)
            out.flush()
            off += n
            Thread.sleep(15)
        }
        out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 — coupe papier
        out.flush()
    }

    private fun writeEscPos(out: OutputStream, lines: List<String>, codepage: Int) {
        out.write(byteArrayOf(0x1B, 0x40)) // ESC @ — init / reset imprimante
        // FS . — quitte le mode Kanji (sinon les octets latins sortent en glyphes chinois)
        out.write(byteArrayOf(0x1C, 0x2E))
        // ESC t n — page de codes (0=PC437, 16=WPC1252, -1=ne pas envoyer)
        if (codepage >= 0) {
            out.write(byteArrayOf(0x1B, 0x74, codepage.toByte()))
        }
        lines.forEach { rawLine ->
            // ASCII-safe : accents FR -> ASCII (évite PC437/ISO mismatch et mode Kanji)
            val safeLine = asciiForPrinter(rawLine)
            out.write(safeLine.toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(0x0A))
        }
        out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        out.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 — coupe papier
        out.flush()
    }

    /**
     * Translittération ASCII pour tickets thermiques cheap (souvent bloqués en Kanji/GBK).
     * Les accents français deviennent des lettres ASCII ; le reste hors 0x20-0x7E est '?'.
     */
    internal fun asciiForPrinter(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                '\u00A0', '\u202F' -> sb.append(' ')
                in '\uFF10'..'\uFF19' -> sb.append(('0' + (c - '\uFF10')))
                'à', 'á', 'â', 'ä', 'ã', 'å', 'ā' -> sb.append('a')
                'À', 'Á', 'Â', 'Ä', 'Ã', 'Å', 'Ā' -> sb.append('A')
                'è', 'é', 'ê', 'ë', 'ē' -> sb.append('e')
                'È', 'É', 'Ê', 'Ë', 'Ē' -> sb.append('E')
                'ì', 'í', 'î', 'ï', 'ī' -> sb.append('i')
                'Ì', 'Í', 'Î', 'Ï', 'Ī' -> sb.append('I')
                'ò', 'ó', 'ô', 'ö', 'õ', 'ō' -> sb.append('o')
                'Ò', 'Ó', 'Ô', 'Ö', 'Õ', 'Ō' -> sb.append('O')
                'ù', 'ú', 'û', 'ü', 'ū' -> sb.append('u')
                'Ù', 'Ú', 'Û', 'Ü', 'Ū' -> sb.append('U')
                'ý', 'ÿ' -> sb.append('y')
                'Ý', 'Ÿ' -> sb.append('Y')
                'ç' -> sb.append('c')
                'Ç' -> sb.append('C')
                'ñ' -> sb.append('n')
                'Ñ' -> sb.append('N')
                'œ' -> sb.append("oe")
                'Œ' -> sb.append("OE")
                'æ' -> sb.append("ae")
                'Æ' -> sb.append("AE")
                '€' -> sb.append("EUR")
                '’', '‘', '‚', '`' -> sb.append('\'')
                '“', '”', '«', '»' -> sb.append('"')
                '–', '—', '−' -> sb.append('-')
                '…' -> sb.append("...")
                else -> {
                    if (c.code in 0x20..0x7E) sb.append(c)
                    else if (c == '\t') sb.append(' ')
                    else sb.append('?')
                }
            }
        }
        return sb.toString()
    }

    private suspend fun buildSessionClosureTicket(session: CaisseSession): List<String> {
        val width = settings.get(SettingsKeys.PRINT_WIDTH, "58").toIntOrNull() ?: 58
        val shop = settings.get(SettingsKeys.SHOP_NAME, "NexaGes")
        val address = settings.get(SettingsKeys.SHOP_ADDRESS, "")
        val phone = settings.get(SettingsKeys.SHOP_PHONE, "")
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val sep = "-".repeat(if (width >= 80) 48 else 32)

        val lines = mutableListOf<String>()
        lines += center(shop, width)
        if (address.isNotBlank()) lines += center(address, width)
        if (phone.isNotBlank()) lines += center(phone, width)
        lines += sep
        lines += center("CLOTURE DE CAISSE", width)
        lines += sep
        lines += "Caissier : ${session.userName}"
        lines += "Ouverture : ${df.format(Date(session.openedAt))}"
        session.closedAt?.let { lines += "Cloture  : ${df.format(Date(it))}" }
        lines += sep
        lines += "Fond de caisse  : ${MoneyFormat.forPrinter(session.openingBalance)}"
        lines += sep
        lines += "Ventes especes  : ${MoneyFormat.forPrinter(session.cashSales)}"
        lines += "Ventes mobile   : ${MoneyFormat.forPrinter(session.mobileSales)}"
        lines += "Ventes dettes   : ${MoneyFormat.forPrinter(session.debtSales)}"
        lines += "Total ventes    : ${MoneyFormat.forPrinter(session.totalAmount)}"
        lines += sep
        lines += "Esp. attendues  : ${MoneyFormat.forPrinter(session.cashTheoretical)}"
        session.cashCounted?.let { counted ->
            lines += "Esp. comptees   : ${MoneyFormat.forPrinter(counted)}"
            val variance = session.cashVariance ?: 0L
            val ecartLabel = if (variance >= 0) "Excedent        " else "Manquant        "
            val ecartSign = if (variance >= 0) "+" else ""
            lines += "$ecartLabel: $ecartSign${MoneyFormat.forPrinter(variance)}"
        }
        lines += sep
        lines += center("Nb. ventes : ${session.salesCount}", width)
        lines += sep
        return lines
    }

    private suspend fun buildTestTicket(): List<String> {
        val width = settings.get(SettingsKeys.PRINT_WIDTH, "58").toIntOrNull() ?: 58
        val name = settings.get(SettingsKeys.SHOP_NAME, "NexaGes")
        return listOf(
            center(name, width),
            center("TEST IMPRESSION", width),
            "----------------",
            "OK ${Date()}",
            "----------------",
        )
    }

    private suspend fun buildOrderTicket(order: Order): List<String> {
        val width = settings.get(SettingsKeys.PRINT_WIDTH, "58").toIntOrNull() ?: 58
        val shop = settings.get(SettingsKeys.SHOP_NAME, "NexaGes")
        val address = settings.get(SettingsKeys.SHOP_ADDRESS, "")
        val phone = settings.get(SettingsKeys.SHOP_PHONE, "")
        val footer = settings.get(SettingsKeys.TICKET_FOOTER, "Merci pour votre visite.")
        val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
        val lines = mutableListOf<String>()
        lines += center(shop, width)
        if (address.isNotBlank()) lines += center(address, width)
        if (phone.isNotBlank()) lines += center(phone, width)
        lines += "----------------"
        lines += "Cmd: ${order.publicId}"
        lines += df.format(Date(order.createdAtEpochMs))
        lines += "----------------"
        order.items.forEach { item ->
            lines += item.productName
            // Utiliser la forme normalisée pour l'imprimante
            lines += "  ${item.quantity} x ${MoneyFormat.forPrinter(item.unitPrice)}"
            lines += "  = ${MoneyFormat.forPrinter(item.lineTotal)}"
        }
        lines += "----------------"
        lines += "TOTAL: ${MoneyFormat.forPrinter(order.totalAmount)}"
        lines += "Statut: ${order.status.label}"
        if (order.payments.isNotEmpty()) {
            order.payments.forEach { p ->
                lines += "${p.paymentMode.label}: ${MoneyFormat.forPrinter(p.amount)}"
                if (p.amountTendered > 0) {
                    lines += "Recu: ${MoneyFormat.forPrinter(p.amountTendered)}"
                    lines += "Monnaie: ${MoneyFormat.forPrinter(p.changeAmount)}"
                }
            }
        } else if (order.status != OrderStatus.PAYEE) {
            lines += "NON PAYE"
        }
        lines += "----------------"
        lines += center(footer, width)
        return lines
    }

    private fun center(text: String, widthMm: Int): String {
        val cols = if (widthMm >= 80) 48 else 32
        if (text.length >= cols) return text.take(cols)
        val pad = (cols - text.length) / 2
        return " ".repeat(pad) + text
    }
}
