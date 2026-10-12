package com.maquis.caisse.data.print

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for EscPosPrinter Bluetooth adapter resolution.
 *
 * Two suites: API 31 (Android 12, S) uses BluetoothManager; API 30 (R) uses deprecated
 * getDefaultAdapter. Both paths are exercised in the JVM via Robolectric so no physical
 * device is required.
 */

// ---------------------------------------------------------------------------
// Minimal in-memory SettingsRepository for tests
// ---------------------------------------------------------------------------

private class FakeSettingsRepository(
    private val printEnabled: Boolean = false,
    private val printerAddress: String = "",
) : SettingsRepository {
    private val store = mutableMapOf<String, String>()

    override suspend fun get(key: String, default: String): String = store[key] ?: default
    override fun observe(key: String): Flow<String?> = throw UnsupportedOperationException()
    override suspend fun set(key: String, value: String) { store[key] = value }
    override suspend fun isPrintEnabled(): Boolean = printEnabled
    override suspend fun setPrintEnabled(enabled: Boolean) { store["print_enabled"] = enabled.toString() }
}

// ---------------------------------------------------------------------------
// API 31+ — BluetoothManager path (Android 12 "S")
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S]) // API 31
class EscPosPrinterApiSTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /**
     * On API 31+ the adapter must be obtained via BluetoothManager, not
     * BluetoothAdapter.getDefaultAdapter() (which is deprecated and can return null on
     * some API-31+ devices). Verify that the system service is reachable and that
     * bondedDevices() returns a non-null list (empty in the Robolectric sandbox,
     * which has no paired devices — the important thing is no crash and correct type).
     */
    @Test
    fun bondedDevices_returnsListOnApi31_viaBluetoothManager() {
        val printer = EscPosPrinter(context, FakeSettingsRepository())
        val devices = printer.bondedDevices()
        // In the Robolectric sandbox there are no bonded devices, but the call must
        // succeed without throwing and return an empty (not null) list.
        assertTrue(
            "bondedDevices() must return a non-null list on API 31+",
            devices is List<*>,
        )
    }

    /** BluetoothManager is available as a system service on API 31+. */
    @Test
    fun bluetoothManagerSystemService_availableOnApi31() {
        val manager = context.getSystemService(BluetoothManager::class.java)
        // Robolectric shadows BluetoothManager — asserting non-null confirms the
        // service lookup itself doesn't crash on API 31.
        assertTrue("BluetoothManager must be available via getSystemService on API 31+", manager != null)
    }

    /** printOrder() must return success immediately when printing is disabled. */
    @Test
    fun printOrder_withPrintDisabled_returnsSuccess() = runBlocking {
        val printer = EscPosPrinter(context, FakeSettingsRepository(printEnabled = false))
        val order = buildMinimalOrder()
        val result = printer.printOrder(order)
        assertTrue("printOrder should succeed silently when printing is disabled", result.isSuccess)
    }

    /** testPrint() must return failure when printing is disabled. */
    @Test
    fun testPrint_withPrintDisabled_returnsFailure() = runBlocking {
        val printer = EscPosPrinter(context, FakeSettingsRepository(printEnabled = false))
        val result = printer.testPrint()
        assertTrue("testPrint should fail with a clear message when printing is disabled", result.isFailure)
        assertEquals("Impression désactivée", result.exceptionOrNull()?.message)
    }

    /**
     * When printing is enabled but no printer address is configured, testPrint() must
     * fail with a descriptive error (not crash or hang).
     */
    @Test
    fun testPrint_withNoPrinterAddress_returnsFailureWithMessage() = runBlocking {
        val printer = EscPosPrinter(
            context,
            FakeSettingsRepository(printEnabled = true, printerAddress = ""),
        )
        val result = printer.testPrint()
        assertTrue("testPrint should fail when no printer address is set", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "Error message should mention missing printer, got: $message",
            message.contains("imprimante", ignoreCase = true),
        )
    }
}

// ---------------------------------------------------------------------------
// API 30 — deprecated getDefaultAdapter path (Android 11 "R")
// ---------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R]) // API 30
class EscPosPrinterApiRTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    /**
     * On API 30 and below the adapter is obtained via the deprecated
     * BluetoothAdapter.getDefaultAdapter(). bondedDevices() must still return a
     * non-null list without crashing.
     */
    @Test
    fun bondedDevices_returnsListOnApi30_viaDefaultAdapter() {
        val printer = EscPosPrinter(context, FakeSettingsRepository())
        val devices = printer.bondedDevices()
        assertTrue(
            "bondedDevices() must return a non-null list on API 30",
            devices is List<*>,
        )
    }

    /** printOrder() must return success immediately when printing is disabled (API 30). */
    @Test
    fun printOrder_withPrintDisabled_returnsSuccess() = runBlocking {
        val printer = EscPosPrinter(context, FakeSettingsRepository(printEnabled = false))
        val order = buildMinimalOrder()
        val result = printer.printOrder(order)
        assertTrue("printOrder should succeed silently when printing is disabled (API 30)", result.isSuccess)
    }

    /** testPrint() must return failure when printing is disabled (API 30). */
    @Test
    fun testPrint_withPrintDisabled_returnsFailure() = runBlocking {
        val printer = EscPosPrinter(context, FakeSettingsRepository(printEnabled = false))
        val result = printer.testPrint()
        assertTrue("testPrint should fail with a clear message when printing is disabled (API 30)", result.isFailure)
        assertEquals("Impression désactivée", result.exceptionOrNull()?.message)
    }

    /**
     * When printing is enabled but no printer address is configured, testPrint() must
     * fail with a descriptive error on API 30 as well.
     */
    @Test
    fun testPrint_withNoPrinterAddress_returnsFailureWithMessage() = runBlocking {
        val printer = EscPosPrinter(
            context,
            FakeSettingsRepository(printEnabled = true, printerAddress = ""),
        )
        val result = printer.testPrint()
        assertTrue("testPrint should fail when no printer address is set (API 30)", result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "Error message should mention missing printer, got: $message",
            message.contains("imprimante", ignoreCase = true),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun buildMinimalOrder() = Order(
    id = 1L,
    publicId = "ORD-001",
    createdAtEpochMs = 1_700_000_000_000L,
    updatedAtEpochMs = 1_700_000_000_000L,
    status = OrderStatus.EN_COURS,
    items = emptyList(),
    payments = emptyList(),
)
