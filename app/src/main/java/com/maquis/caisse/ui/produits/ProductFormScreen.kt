package com.maquis.caisse.ui.produits

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.maquis.caisse.core.Constants
import com.maquis.caisse.ui.common.DropdownField
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
private val StockUnitOptions = listOf("Carton", "Bidon", "Sac", "Caisse", "Bac")
private val DetailUnitOptions = listOf("Kg", "Litre", "Pièce", "Verre")

@Composable
fun ProductFormScreen(
    form: ProductFormState,
    existingImageFile: File?,
    categoryOptions: List<String>,
    stockTrackingEnabled: Boolean = true,
    freeEntryEnabled: Boolean = true,
    onBack: () -> Unit,
    onUpdate: ((ProductFormState) -> ProductFormState) -> Unit,
    onImagePicked: (Uri) -> Unit,
    onClearImage: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    // Fallback si PickVisualMedia indisponible sur certains appareils.
    val galleryLegacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) onImagePicked(uri)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) onImagePicked(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val uri = createCameraUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = createCameraUri(context)
            cameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val isEdit = form.editingId != null
    val previewModel: Any? = form.pendingImageUri
        ?: existingImageFile?.takeIf { !form.clearExistingImage }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEdit) "Modifier le produit" else "Nouveau produit")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Image compacte : laisse la place aux champs (tablette paysage).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 90.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewModel != null) {
                        AsyncImage(
                            model = previewModel,
                            contentDescription = "Photo produit",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                } else {
                                    galleryLegacyLauncher.launch("image/*")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                        ) {
                            Icon(
                                Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Galerie")
                        }
                        FilledTonalButton(
                            onClick = { launchCamera() },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Photo")
                        }
                    }
                    if (previewModel != null) {
                        TextButton(
                            onClick = onClearImage,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text("Retirer l'image")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onUpdate { it.copy(name = value) } },
                label = { Text("Nom *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "Catégorie",
                selected = form.category.ifBlank { null },
                options = categoryOptions,
                optionLabel = { it },
                onSelect = { value -> onUpdate { it.copy(category = value.orEmpty()) } },
                allowNull = false,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!form.detailEnabled) {
                OutlinedTextField(
                    value = form.salePrice,
                    onValueChange = { value ->
                        onUpdate {
                            it.copy(
                                salePrice = value.filter { c -> c.isDigit() }
                                    .take(Constants.MAX_MONEY_DIGITS),
                            )
                        }
                    },
                    label = { Text("Prix de vente (FCFA) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = form.purchasePrice,
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            purchasePrice = value.filter { c -> c.isDigit() }
                                .take(Constants.MAX_MONEY_DIGITS),
                        )
                    }
                },
                label = {
                    Text(
                        if (form.detailEnabled) "Prix d'achat du ${form.stockUnit.lowercase()} (FCFA)"
                        else "Prix d'achat (FCFA)",
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (stockTrackingEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Suivre le stock de ce produit", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = form.trackStock,
                        onCheckedChange = { checked -> onUpdate { it.copy(trackStock = checked) } },
                    )
                }
            }
            if (stockTrackingEnabled && form.trackStock) {
            OutlinedTextField(
                value = form.stock,
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            stock = value.filter { c -> c.isDigit() }
                                .take(Constants.MAX_STOCK_DIGITS),
                        )
                    }
                },
                label = { Text("Stock") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.alertThreshold,
                onValueChange = { value ->
                    onUpdate {
                        it.copy(
                            alertThreshold = value.filter { c -> c.isDigit() }
                                .take(Constants.MAX_STOCK_DIGITS),
                        )
                    }
                },
                label = { Text("Seuil d'alerte stock") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            }

            if (freeEntryEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Vente libre (au détail)", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = form.detailEnabled,
                        onCheckedChange = { checked -> onUpdate { it.copy(detailEnabled = checked) } },
                    )
                }
                if (form.detailEnabled) {
                    DropdownField(
                        label = "Emballage",
                        selected = form.stockUnit,
                        options = StockUnitOptions,
                        optionLabel = { it },
                        onSelect = { value -> value?.let { v -> onUpdate { it.copy(stockUnit = v) } } },
                    )
                    DropdownField(
                        label = "Unité de vente",
                        selected = form.detailUnit,
                        options = DetailUnitOptions,
                        optionLabel = { it },
                        onSelect = { value -> value?.let { v -> onUpdate { it.copy(detailUnit = v) } } },
                    )
                    OutlinedTextField(
                        value = form.contentPerUnit,
                        onValueChange = { value ->
                            onUpdate {
                                it.copy(contentPerUnit = value.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(8))
                            }
                        },
                        label = { Text("Nombre de ${form.detailUnit.lowercase()} par ${form.stockUnit.lowercase()}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.detailPrice,
                        onValueChange = { value ->
                            onUpdate {
                                it.copy(detailPrice = value.filter { c -> c.isDigit() }.take(Constants.MAX_MONEY_DIGITS))
                            }
                        },
                        label = { Text("Prix d'un ${form.detailUnit.lowercase()} (FCFA)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Produit actif", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = form.isActive,
                    onCheckedChange = { checked -> onUpdate { it.copy(isActive = checked) } },
                )
            }

            form.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Button(
                onClick = onSave,
                enabled = !form.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(if (form.isSaving) "Enregistrement…" else "Enregistrer")
            }
        }
    }
}

private fun createCameraUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "camera").also { it.mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
