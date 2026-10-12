package com.maquis.caisse.ui.parametres

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.data.backup.BackupManager
import com.maquis.caisse.data.print.EscPosPrinter
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.kiosk.KioskManager
import com.maquis.caisse.kiosk.KioskSecureStore
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ParametresUiState(
    val shopName: String = "",
    val shopAddress: String = "",
    val shopPhone: String = "",
    val ticketFooter: String = "Merci pour votre visite.",
    val printEnabled: Boolean = false,
    val printWidth: String = "58",
    val printAsImage: Boolean = true,
    val printerAddress: String = "",
    val printerName: String = "",
    val stockTrackingEnabled: Boolean = true,
    val freeEntryEnabled: Boolean = true,
    val devices: List<BtDeviceUi> = emptyList(),
    val message: String? = null,
    val backupBusy: Boolean = false,
    val isAdmin: Boolean = false,
    val kioskEnabled: Boolean = false,
    val kioskAutoStart: Boolean = false,
    val kioskHasPin: Boolean = false,
    val kioskDeviceOwner: Boolean = false,
    val kioskLockedNow: Boolean = false,
)

data class BtDeviceUi(val name: String, val address: String)

@HiltViewModel
class ParametresViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val printer: EscPosPrinter,
    private val backupManager: BackupManager,
    private val userRepository: UserRepository,
    private val session: SessionManager,
    private val kioskManager: KioskManager,
) : ViewModel() {
    private val _ui = MutableStateFlow(ParametresUiState())
    val ui: StateFlow<ParametresUiState> = _ui.asStateFlow()

    fun suggestedBackupName(): String = backupManager.suggestedFileName()

    /** Sortie / config kiosque : uniquement le compte rôle ADMIN. */
    fun isAdmin(): Boolean = session.userOrNull()?.role == "ADMIN"

    fun changeOwnPin(newPin: String, confirm: String) = viewModelScope.launch {
        if (newPin != confirm) {
            _ui.update { it.copy(message = "Les deux codes ne correspondent pas") }
            return@launch
        }
        val me = session.userOrNull()
        if (me == null) {
            _ui.update { it.copy(message = "Non connecté") }
            return@launch
        }
        try {
            userRepository.changePin(me.id, newPin)
            // Même PIN pour le kiosque.
            if (me.role == "ADMIN") {
                runCatching { kioskManager.setAdminPin(newPin) }
            }
            _ui.update { it.copy(message = "PIN Admin mis à jour (aussi utilisé pour le kiosque)") }
        } catch (e: Exception) {
            _ui.update { it.copy(message = e.message ?: "Échec modification du code") }
        }
    }

    fun setKioskEnabled(enabled: Boolean, adminPin: String, activity: Activity): Boolean {
        if (!requireAdminAccount()) return false
        if (!verifyPinForAdminAction(adminPin)) return false
        // Un seul PIN : celui du compte Admin (synchronisé aussi en store kiosque).
        runCatching { kioskManager.setAdminPin(adminPin) }
        if (enabled) {
            kioskManager.setEnabled(true) // persiste + active aussi le démarrage auto
            // Home / batterie avant Lock Task (sinon Settings peut être bloqué).
            runCatching {
                if (!kioskManager.isDeviceOwner()) {
                    activity.startActivity(kioskManager.homeSettingsIntent())
                }
                kioskManager.requestIgnoreBatteryOptimizationsIntent()?.let { activity.startActivity(it) }
            }
            val lockOk = kioskManager.enterKiosk(activity)
            val err = kioskManager.lastError()
            _ui.update {
                it.copy(
                    message = when {
                        lockOk && err == null ->
                            "Mode kiosque activé — tablette dédiée NexaGes"
                        else ->
                            "Kiosque activé mais Lock Task incomplet. " +
                                (err ?: "Définis NexaGes comme Home + Device Owner.")
                    },
                )
            }
        } else {
            kioskManager.disableKiosk(activity)
            _ui.update { it.copy(message = "Mode kiosque désactivé") }
        }
        refreshKioskUi()
        return true
    }

    fun setKioskAutoStart(enabled: Boolean, adminPin: String): Boolean {
        if (!requireAdminAccount()) return false
        if (!verifyPinForAdminAction(adminPin)) return false
        kioskManager.setAutoStart(enabled)
        refreshKioskUi()
        _ui.update {
            it.copy(
                message = if (enabled) {
                    "NexaGes se lancera automatiquement au démarrage"
                } else {
                    "Démarrage automatique désactivé"
                },
            )
        }
        return true
    }

    fun exitKioskTemporary(adminPin: String, activity: Activity): Boolean {
        if (!requireAdminAccount()) return false
        if (!verifyPinForAdminAction(adminPin)) return false
        kioskManager.exitKiosk(activity)
        refreshKioskUi()
        val minutes = KioskSecureStore.TEMP_UNLOCK_MS / 60_000L
        _ui.update {
            it.copy(
                message = "Kiosque quitté ${minutes} min — re-verrouillage automatique ensuite",
            )
        }
        return true
    }

    fun openHomeSettings(activity: Activity) {
        runCatching { activity.startActivity(kioskManager.homeSettingsIntent()) }
    }

    fun openBatteryExemption(activity: Activity) {
        val intent = kioskManager.requestIgnoreBatteryOptimizationsIntent()
        if (intent != null) {
            runCatching { activity.startActivity(intent) }
        } else {
            _ui.update { it.copy(message = "Optimisation batterie déjà ignorée pour NexaGes") }
        }
    }

    /** Vérifie le PIN admin sans quitter le kiosque (étape avant confirmation). */
    fun checkAdminPin(adminPin: String): Boolean {
        if (!requireAdminAccount()) return false
        return verifyPinForAdminAction(adminPin)
    }

    private fun requireAdminAccount(): Boolean {
        if (isAdmin()) return true
        _ui.update {
            it.copy(message = "Connecte-toi avec le compte administrateur pour gérer le kiosque")
        }
        return false
    }

    private fun matchesLoggedInAdminPin(pin: String): Boolean {
        val user = session.userOrNull() ?: return false
        return user.role == "ADMIN" && user.pin == pin
    }

    /** Un seul code : le PIN du compte Admin connecté (aussi utilisé pour le kiosque). */
    private fun verifyPinForAdminAction(adminPin: String): Boolean {
        if (adminPin.isBlank()) {
            _ui.update { it.copy(message = "Saisis le PIN du compte Admin") }
            return false
        }
        if (matchesLoggedInAdminPin(adminPin)) {
            runCatching { kioskManager.setAdminPin(adminPin) }
            return true
        }
        _ui.update { it.copy(message = "PIN Admin incorrect") }
        return false
    }

    private fun refreshKioskUi() {
        val ks = kioskManager.state.value
        _ui.update {
            it.copy(
                isAdmin = isAdmin(),
                kioskEnabled = ks.enabled,
                kioskAutoStart = ks.autoStart,
                kioskHasPin = ks.hasAdminPin,
                kioskDeviceOwner = kioskManager.isDeviceOwner(),
                kioskLockedNow = ks.shouldLockNow,
            )
        }
    }

    init {
        viewModelScope.launch { reload() }
        viewModelScope.launch {
            kioskManager.state.collect { refreshKioskUi() }
        }
    }

    suspend fun reload() {
        _ui.update {
            it.copy(
                shopName = settings.get(SettingsKeys.SHOP_NAME, "NexaGes"),
                shopAddress = settings.get(SettingsKeys.SHOP_ADDRESS, ""),
                shopPhone = settings.get(SettingsKeys.SHOP_PHONE, ""),
                ticketFooter = settings.get(SettingsKeys.TICKET_FOOTER, "Merci pour votre visite."),
                printEnabled = settings.isPrintEnabled(),
                printWidth = settings.get(SettingsKeys.PRINT_WIDTH, "58"),
                printAsImage = settings.get(SettingsKeys.PRINT_AS_IMAGE, "1") != "0",
                printerAddress = settings.get(SettingsKeys.PRINTER_ADDRESS, ""),
                printerName = settings.get(SettingsKeys.PRINTER_NAME, ""),
                stockTrackingEnabled = settings.get(SettingsKeys.STOCK_TRACKING_ENABLED, "true") != "false",
                freeEntryEnabled = settings.get(SettingsKeys.FREE_ENTRY_ENABLED, "true") != "false",
            )
        }
        refreshKioskUi()
    }

    fun deviceOwnerHint(): String = kioskManager.deviceOwnerSetupHint()

    fun update(transform: (ParametresUiState) -> ParametresUiState) {
        _ui.update(transform)
    }

    fun save() = viewModelScope.launch {
        val s = _ui.value
        settings.set(SettingsKeys.SHOP_NAME, s.shopName)
        settings.set(SettingsKeys.SHOP_ADDRESS, s.shopAddress)
        settings.set(SettingsKeys.SHOP_PHONE, s.shopPhone)
        settings.set(SettingsKeys.TICKET_FOOTER, s.ticketFooter.ifBlank { "Merci pour votre visite." })
        settings.setPrintEnabled(s.printEnabled)
        settings.set(SettingsKeys.PRINT_WIDTH, s.printWidth)
        settings.set(SettingsKeys.PRINT_AS_IMAGE, if (s.printAsImage) "1" else "0")
        settings.set(SettingsKeys.PRINTER_ADDRESS, s.printerAddress)
        settings.set(SettingsKeys.PRINTER_NAME, s.printerName)
        settings.set(SettingsKeys.STOCK_TRACKING_ENABLED, s.stockTrackingEnabled.toString())
        settings.set(SettingsKeys.FREE_ENTRY_ENABLED, s.freeEntryEnabled.toString())
        _ui.update { it.copy(message = "Paramètres enregistrés") }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(backupBusy = true, message = "Export en cours…") }
        val result = backupManager.exportToUri(uri)
        _ui.update {
            it.copy(
                backupBusy = false,
                message = if (result.isSuccess) {
                    "Sauvegarde exportée. Garde ce fichier avant toute désinstallation."
                } else {
                    result.exceptionOrNull()?.message ?: "Échec de l'export"
                },
            )
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(backupBusy = true, message = "Restauration en cours…") }
        val result = backupManager.importFromUri(uri)
        if (result.isSuccess) {
            _ui.update { it.copy(message = "Restauration OK — redémarrage…") }
            backupManager.restartApp()
        } else {
            val detail = result.exceptionOrNull()?.message ?: "Erreur inconnue"
            _ui.update {
                it.copy(
                    backupBusy = false,
                    message = "Échec restauration : $detail",
                )
            }
        }
    }

    fun importBackupFromFolder(treeUri: Uri) = viewModelScope.launch {
        _ui.update { it.copy(backupBusy = true, message = "Restauration du dossier app…") }
        val result = backupManager.importFromTreeUri(treeUri)
        if (result.isSuccess) {
            _ui.update { it.copy(message = "Restauration OK — redémarrage…") }
            backupManager.restartApp()
        } else {
            val detail = result.exceptionOrNull()?.message ?: "Erreur inconnue"
            _ui.update {
                it.copy(
                    backupBusy = false,
                    message = "Échec restauration dossier : $detail",
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetoothDevices() {
        val devices = printer.bondedDevices().map { d ->
            BtDeviceUi(name = d.name ?: "Imprimante", address = d.address)
        }
        _ui.update { it.copy(devices = devices, message = "${devices.size} appareil(s) appairé(s)") }
    }

    fun selectPrinter(device: BtDeviceUi) {
        _ui.update {
            it.copy(printerAddress = device.address, printerName = device.name)
        }
    }

    fun testPrint() = viewModelScope.launch {
        if (!_ui.value.printEnabled) {
            _ui.update { it.copy(message = "Impression désactivée — aucun envoi") }
            return@launch
        }
        val result = printer.testPrint()
        _ui.update {
            it.copy(
                message = if (result.isSuccess) {
                    "Test imprimé"
                } else {
                    result.exceptionOrNull()?.message ?: "Échec impression"
                },
            )
        }
    }
}

private enum class KioskPinAction {
    SET_PIN,
    TOGGLE_ENABLED,
    TOGGLE_AUTO_START,
    EXIT_KIOSK,
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return ctx as? Activity
}

@Composable
fun ParametresScreen(viewModel: ParametresViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var permissionsReady by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreTree by remember { mutableStateOf<Uri?>(null) }
    var changeOwnPin by remember { mutableStateOf(false) }
    var kioskPinAction by remember { mutableStateOf<KioskPinAction?>(null) }
    var pendingKioskEnable by remember { mutableStateOf<Boolean?>(null) }
    var pendingAutoStart by remember { mutableStateOf<Boolean?>(null) }
    var confirmExitKiosk by remember { mutableStateOf(false) }
    var verifiedExitPin by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_ZIP),
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreTree = null
            pendingRestoreUri = uri
            confirmRestore = true
        }
    }

    val importFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            // Persiste l'accès au dossier (USB / Downloads).
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            pendingRestoreUri = null
            pendingRestoreTree = treeUri
            confirmRestore = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionsReady = true }

    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageHeader(title = "Paramètres", subtitle = "Commerce, impression, kiosque")
        TextPill("Style Assistant · pastilles & cartes verre", PillTone.CYAN)

        GlassCard {
        OutlinedButton(
            onClick = { changeOwnPin = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Changer mon code PIN") }
        OutlinedTextField(value = ui.shopName, onValueChange = { v -> viewModel.update { it.copy(shopName = v) } }, label = { Text("Nom du commerce") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.shopAddress, onValueChange = { v -> viewModel.update { it.copy(shopAddress = v) } }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.shopPhone, onValueChange = { v -> viewModel.update { it.copy(shopPhone = v) } }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = ui.ticketFooter, onValueChange = { v -> viewModel.update { it.copy(ticketFooter = v) } }, label = { Text("Message ticket") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.stockTrackingEnabled,
                onCheckedChange = { c -> viewModel.update { it.copy(stockTrackingEnabled = c) } },
            )
            Text("Activer le suivi de stock")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.freeEntryEnabled,
                onCheckedChange = { c -> viewModel.update { it.copy(freeEntryEnabled = c) } },
            )
            Text("Activer la vente libre")
        }
        }

        TextPill("Sauvegarde", PillTone.INFO)
        GlassCard {
        Text("Sauvegarde des données", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pour récupérer un ancien dossier app (Replit / copie USB) : " +
                "sélectionne le dossier qui contient « databases » " +
                "(avec maquis_caisse.db). Tu peux aussi restaurer un .zip ou .db.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { exportLauncher.launch(viewModel.suggestedBackupName()) },
            enabled = !ui.backupBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Exporter la sauvegarde") }
        OutlinedButton(
            onClick = { importFolderLauncher.launch(null) },
            enabled = !ui.backupBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Restaurer depuis un dossier app") }
        OutlinedButton(
            onClick = { importLauncher.launch("*/*") },
            enabled = !ui.backupBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Restaurer un fichier (.zip ou .db)") }
        }

        TextPill("Impression", PillTone.CYAN)
        GlassCard {
        Text("Impression", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = ui.printEnabled,
                onCheckedChange = { c -> viewModel.update { it.copy(printEnabled = c) } },
            )
            Text("Activer l'impression des tickets")
        }

        if (ui.printEnabled) {
            DropdownField(
                label = "Format",
                selected = ui.printWidth,
                options = listOf("58", "80"),
                optionLabel = { "$it mm" },
                onSelect = { v -> if (v != null) viewModel.update { it.copy(printWidth = v) } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.printAsImage,
                    onCheckedChange = { c -> viewModel.update { it.copy(printAsImage = c) } },
                )
                Text("Envoyer le ticket en image (accents corrects)")
            }
            Text(
                "Imprimante : ${ui.printerName.ifBlank { "aucune" }} ${ui.printerAddress}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (permissionsReady || true) viewModel.refreshBluetoothDevices()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Rechercher Bluetooth") }
                OutlinedButton(
                    onClick = { viewModel.testPrint() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Tester l'impression") }
            }
            ui.devices.forEach { device ->
                OutlinedButton(
                    onClick = { viewModel.selectPrinter(device) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Text("${device.name} (${device.address})")
                }
            }
        } else {
            Text(
                "Impression désactivée — aucun envoi ni message d'erreur imprimante.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }

        if (ui.isAdmin) {
            TextPill("Mode kiosque", PillTone.WARNING)
            GlassCard {
            Text("MODE KIOSQUE", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tablette dédiée NexaGes. Même PIN que le compte Admin. " +
                    "Les options restent valables après redémarrage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.kioskEnabled,
                    onCheckedChange = { checked ->
                        pendingKioskEnable = checked
                        kioskPinAction = KioskPinAction.TOGGLE_ENABLED
                    },
                )
                Text("Activer le mode kiosque (persistant)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = ui.kioskAutoStart,
                    onCheckedChange = { checked ->
                        pendingAutoStart = checked
                        kioskPinAction = KioskPinAction.TOGGLE_AUTO_START
                    },
                )
                Text("Démarrer automatiquement après allumage")
            }
            Text(
                "Astuce : NexaGes en application d'accueil (Home) + Device Owner ADB " +
                    "pour un verrouillage total.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "PIN kiosque = PIN du compte Admin (modifiable via « Changer mon code PIN »).",
                style = MaterialTheme.typography.bodyLarge,
            )
            ui.message?.takeIf { it.contains("Lock Task", ignoreCase = true) || it.contains("kiosque", ignoreCase = true) }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Administration", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = { kioskPinAction = KioskPinAction.EXIT_KIOSK },
                enabled = ui.kioskEnabled && ui.kioskLockedNow,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Quitter le mode kiosque (3 min)") }
            OutlinedButton(
                onClick = { activity?.let(viewModel::openHomeSettings) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Choisir NexaGes comme accueil (Home)") }
            OutlinedButton(
                onClick = { activity?.let(viewModel::openBatteryExemption) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Ignorer l'optimisation batterie") }
            Text(
                if (ui.kioskDeviceOwner) {
                    "Device Owner actif — verrouillage système renforcé."
                } else {
                    viewModel.deviceOwnerHint()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }
        }

        Button(
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
        ) { Text("Enregistrer") }
        ui.message?.let { TextPill(it, PillTone.SUCCESS) }
    }

    if (changeOwnPin) {
        var pin by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changeOwnPin = false },
            title = { Text("Changer mon code PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Nouveau code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Confirmer") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changeOwnPin(pin, confirm)
                        changeOwnPin = false
                    },
                    enabled = pin.length >= 4 && confirm.length >= 4,
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { changeOwnPin = false }) { Text("Annuler") }
            },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = {
                confirmRestore = false
                pendingRestoreUri = null
                pendingRestoreTree = null
            },
            title = { Text("Restaurer la sauvegarde ?") },
            text = {
                Text(
                    if (pendingRestoreTree != null) {
                        "Les données actuelles seront remplacées par ce dossier app " +
                            "(databases + images + préférences). L'application redémarrera."
                    } else {
                        "Les données actuelles seront remplacées. " +
                            "Choisis un .zip, un .db, ou un dossier contenant databases/maquis_caisse.db. " +
                            "L'application redémarrera ensuite."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fileUri = pendingRestoreUri
                        val treeUri = pendingRestoreTree
                        confirmRestore = false
                        pendingRestoreUri = null
                        pendingRestoreTree = null
                        when {
                            treeUri != null -> viewModel.importBackupFromFolder(treeUri)
                            fileUri != null -> viewModel.importBackup(fileUri)
                        }
                    },
                ) { Text("Restaurer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        pendingRestoreUri = null
                        pendingRestoreTree = null
                    },
                ) { Text("Annuler") }
            },
        )
    }

    val pinAction = kioskPinAction
    if (pinAction != null) {
        KioskAdminPinDialog(
            action = pinAction,
            hasExistingPin = ui.kioskHasPin,
            onDismiss = {
                kioskPinAction = null
                pendingKioskEnable = null
                pendingAutoStart = null
            },
            onConfirm = { currentPin, _, _ ->
                when (pinAction) {
                    KioskPinAction.SET_PIN -> {
                        // PIN unique = compte Admin → ouvrir le changement de PIN compte.
                        kioskPinAction = null
                        changeOwnPin = true
                    }
                    KioskPinAction.TOGGLE_ENABLED -> {
                        val act = activity ?: return@KioskAdminPinDialog
                        val target = pendingKioskEnable ?: return@KioskAdminPinDialog
                        if (viewModel.setKioskEnabled(target, currentPin.orEmpty(), act)) {
                            kioskPinAction = null
                            pendingKioskEnable = null
                        }
                    }
                    KioskPinAction.TOGGLE_AUTO_START -> {
                        val target = pendingAutoStart ?: return@KioskAdminPinDialog
                        if (viewModel.setKioskAutoStart(target, currentPin.orEmpty())) {
                            kioskPinAction = null
                            pendingAutoStart = null
                        }
                    }
                    KioskPinAction.EXIT_KIOSK -> {
                        if (viewModel.checkAdminPin(currentPin.orEmpty())) {
                            verifiedExitPin = currentPin
                            kioskPinAction = null
                            confirmExitKiosk = true
                        }
                    }
                }
            },
        )
    }

    if (confirmExitKiosk) {
        AlertDialog(
            onDismissRequest = {
                confirmExitKiosk = false
                verifiedExitPin = null
            },
            title = { Text("Quitter le mode kiosque ?") },
            text = {
                Text(
                    "Navigation Android débloquée pendant 3 minutes, " +
                        "puis re-verrouillage automatique. Déconnexion Admin relock aussi.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val act = activity
                        val pin = verifiedExitPin.orEmpty()
                        confirmExitKiosk = false
                        verifiedExitPin = null
                        if (act != null) {
                            viewModel.exitKioskTemporary(pin, act)
                        }
                    },
                ) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmExitKiosk = false
                        verifiedExitPin = null
                    },
                ) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun KioskAdminPinDialog(
    action: KioskPinAction,
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String?, newPin: String?, confirmPin: String?) -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    val title = "PIN du compte Admin"
    val canSubmit = currentPin.length >= KioskSecureStore.MIN_PIN_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (action == KioskPinAction.EXIT_KIOSK || action != KioskPinAction.SET_PIN) {
                    Text(
                        text = "•".repeat(currentPin.length.coerceAtLeast(0)).ifEmpty { "••••" },
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (action != KioskPinAction.SET_PIN || hasExistingPin) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = {
                            currentPin = it.filter { c -> c.isDigit() }
                                .take(KioskSecureStore.MAX_PIN_LENGTH)
                        },
                        label = {
                            Text(
                                if (action == KioskPinAction.SET_PIN) {
                                    "PIN actuel"
                                } else {
                                    "Code administrateur"
                                },
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (action == KioskPinAction.SET_PIN) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            newPin = it.filter { c -> c.isDigit() }
                                .take(KioskSecureStore.MAX_PIN_LENGTH)
                        },
                        label = { Text("Nouveau PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            confirmPin = it.filter { c -> c.isDigit() }
                                .take(KioskSecureStore.MAX_PIN_LENGTH)
                        },
                        label = { Text("Confirmer") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (action) {
                        KioskPinAction.SET_PIN -> onConfirm(
                            currentPin.takeIf { hasExistingPin },
                            newPin,
                            confirmPin,
                        )
                        else -> onConfirm(currentPin, null, null)
                    }
                },
                enabled = canSubmit,
            ) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
