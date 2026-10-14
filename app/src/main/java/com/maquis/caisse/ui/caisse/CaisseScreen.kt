package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import com.maquis.caisse.domain.model.formatUnits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.core.Constants
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.components.NumericKeypad
import com.maquis.caisse.ui.produits.ProductTile

/**
 * Caisse paysage type Gestion_app : catalogue à gauche, panier compact à droite.
 */
@Composable
fun CaisseScreen(
    viewModel: CaisseViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbar()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Catalogue (gauche) — élargi pour compenser panier réduit
            Column(
                modifier = Modifier
                    .weight(1.85f)
                    .fillMaxHeight()
                    .padding(10.dp),
            ) {
                PageHeader(
                    title = "Caisse",
                    subtitle = "Touche un produit pour l'ajouter au panier",
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (state.cart.isNotEmpty()) {
                    TextPill(
                        "${state.cart.sumOf { it.quantity }} dans le panier · ${MoneyFormat.format(state.cartTotal)}",
                        PillTone.INFO,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                if (state.freeEntryEnabled) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vente libre (détail)", fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = state.amountMode,
                            onCheckedChange = { viewModel.toggleAmountMode() },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    singleLine = true,
                    placeholder = { Text("Rechercher un produit…") },
                )

                if (state.filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.products.isEmpty()) {
                                "Aucun produit actif.\nAjoute-en dans Produits."
                            } else {
                                "Aucun résultat"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = Constants.PRODUCT_TILE_MIN_DP.dp),
                        contentPadding = PaddingValues(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        items(state.filteredProducts, key = { it.id }) { product ->
                            ProductTile(
                                product = product,
                                imageFile = viewModel.imageFile(product.imagePath),
                                onClick = { viewModel.onProductTap(product) },
                            )
                        }
                    }
                }
            }

            // Panier (droite) — style verre
            CartPanel(
                lines = state.cart,
                total = state.cartTotal,
                profit = if (state.showProfit) state.profit else null,
                onLineLongPress = viewModel::onCartLineLongPress,
                onValidate = viewModel::openPayment,
                onClear = viewModel::clearCart,
                onSaveOrder = { viewModel.saveUnpaidOrder() },
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    state.detailSale?.let { detail ->
        DetailSaleDialog(
            product = detail.product,
            initialAmount = detail.initialAmount,
            isEditing = detail.isEditing,
            onDismiss = viewModel::dismissDetailSale,
            onDelete = viewModel::deleteDetailLine,
            onConfirm = viewModel::confirmDetailSale,
        )
    }

    state.quantityOverlay?.let { overlay ->
        Dialog(
            onDismissRequest = viewModel::dismissQuantityOverlay,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true,
            ),
        ) {
            Surface(modifier = Modifier.widthIn(min = 280.dp).fillMaxWidth(0.45f)) {
                NumericKeypad(
                    value = overlay.quantityInput,
                    onValueChange = viewModel::onQuantityInputChange,
                    onConfirm = viewModel::confirmQuantity,
                    title = overlay.productName,
                    subtitle = if (overlay.amountMode) {
                        val amount = overlay.quantityInput.toLongOrNull()
                        if (amount != null && amount > 0L && overlay.unitPrice > 0L) {
                            "Montant (F) → " + formatUnits(amount.toDouble() / overlay.unitPrice) +
                                " · prix " + MoneyFormat.format(overlay.unitPrice)
                        } else {
                            "Montant (F) · prix " + MoneyFormat.format(overlay.unitPrice)
                        }
                    } else {
                        MoneyFormat.format(overlay.unitPrice)
                    },
                    maxDigits = if (overlay.amountMode) Constants.MAX_MONEY_DIGITS else Constants.MAX_QUANTITY_DIGITS,
                    confirmLabel = "OK",
                    confirmEnabled = overlay.quantityInput.isNotEmpty(),
                    inputSessionKey = "${overlay.productId}-${overlay.isEditing}-${overlay.amountMode}",
                    compact = true,
                    onDeleteLine = if (overlay.isEditing) {
                        { viewModel.deleteOverlayLine() }
                    } else {
                        null
                    },
                )
            }
        }
    }

    if (state.showPayment) {
        PaymentDialog(
            total = state.cartTotal,
            payment = state.payment,
            onDismiss = viewModel::dismissPayment,
            onSelectMode = viewModel::selectPaymentMode,
            onSelectField = viewModel::selectPaymentField,
            onInputChange = viewModel::onPaymentDigitInput,
            onConfirm = viewModel::confirmPayment,
            printAvailable = state.printAvailable,
            printOnPayment = state.printOnPayment,
            onPrintOnPaymentChange = viewModel::setPrintOnPayment,
        )
    }

    state.completedSale?.let { sale ->
        TicketDialog(
            sale = sale,
            onDismiss = viewModel::dismissTicket,
        )
    }

    state.savedOrderPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSavedOrderPrompt,
            title = { Text("Commande ${prompt.order.publicId}") },
            text = {
                Text(
                    if (prompt.printEnabled) {
                        buildString {
                            append("Commande enregistrée.")
                            prompt.printMessage?.let {
                                append("\n\n")
                                append(it)
                            }
                            append("\n\nTu restes en Caisse. Réimprime si besoin, ou OK pour continuer.")
                        }
                    } else {
                        "Commande enregistrée. Tu restes en Caisse."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissSavedOrderPrompt,
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                if (prompt.printEnabled) {
                    TextButton(onClick = viewModel::reprintSavedOrder) {
                        Text("Réimprimer")
                    }
                }
            },
        )
    }
}
