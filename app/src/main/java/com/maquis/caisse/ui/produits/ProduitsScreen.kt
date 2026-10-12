package com.maquis.caisse.ui.produits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.core.Constants
import com.maquis.caisse.ui.theme.GestionBlue

/**
 * Catalogue produits (Sprint 1) : grille Coil + création/édition avec image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProduitsScreen(
    viewModel: ProduitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    val form = state.form
    if (form != null) {
        // Dialog plein écran pour masquer la barre basse pendant la saisie.
        Dialog(
            onDismissRequest = viewModel::closeForm,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                ProductFormScreen(
                    form = form,
                    existingImageFile = viewModel.imageFile(form.existingImagePath),
                    categoryOptions = state.categories.ifEmpty {
                        listOf("Boissons", "Plats", "Grillades", "Poissons", "Viandes", "Accompagnements", "Desserts", "Divers")
                    },
                    stockTrackingEnabled = state.stockTrackingEnabled,
                    freeEntryEnabled = state.freeEntryEnabled,
                    onBack = viewModel::closeForm,
                    onUpdate = viewModel::updateForm,
                    onImagePicked = viewModel::onImagePicked,
                    onClearImage = viewModel::onClearImage,
                    onSave = viewModel::saveForm,
                    onDelete = viewModel::deleteCurrent,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Produits", color = GestionBlue) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (state.canManageProducts) {
                FloatingActionButton(
                    onClick = viewModel::openCreateForm,
                    containerColor = GestionBlue,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter un produit")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.canManageProducts) {
                        "Aucun produit.\nAppuie sur + pour en ajouter."
                    } else {
                        "Aucun produit.\nConnecte-toi en Admin pour en ajouter."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = Constants.PRODUCT_TILE_MIN_DP.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(
                    items = state.products,
                    key = { it.id },
                ) { product ->
                    ProductTile(
                        product = product,
                        imageFile = viewModel.imageFile(product.imagePath),
                        onClick = { viewModel.openEditForm(product) },
                    )
                }
            }
        }
    }
}
