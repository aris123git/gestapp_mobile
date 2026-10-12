package com.maquis.caisse.ui.inventaire

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.domain.model.CreateOrderRequest
import com.maquis.caisse.domain.model.InventaireEcartReason
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.StockMovementType
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.usecase.ObserveProductsUseCase
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InventaireViewModel @Inject constructor(
    observeProducts: ObserveProductsUseCase,
    private val stockRepository: StockRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {
    val products: StateFlow<List<Product>> = observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun applyCount(
        product: Product,
        counted: Int,
        reason: InventaireEcartReason?,
        comment: String?,
    ) = viewModelScope.launch {
        val theoretical = product.stock
        val gap = counted - theoretical
        if (gap == 0) {
            _message.value = "${product.name} : stock conforme ($counted)"
            return@launch
        }
        try {
            when {
                gap > 0 -> {
                    // Surplus → entrée d'inventaire (erreur / oubli d'entrée)
                    stockRepository.adjust(
                        productId = product.id,
                        type = StockMovementType.ENTREE,
                        quantity = gap,
                        motif = "Inventaire — surplus",
                        comment = comment ?: reason?.label,
                    )
                }
                reason == InventaireEcartReason.VENTE_HORS_CAISSE -> {
                    val qty = -gap
                    orderRepository.createOrder(
                        CreateOrderRequest(
                            lines = listOf(
                                CartLine(
                                    productId = product.id,
                                    productName = product.name,
                                    unitPrice = product.salePrice,
                                    quantity = qty,
                                    imagePath = product.imagePath,
                                ),
                            ),
                            note = "Vente hors caisse (inventaire)",
                            markAsPaid = true,
                            paymentMode = PaymentMode.OTHER,
                            amountTendered = product.salePrice * qty,
                            paymentAmount = product.salePrice * qty,
                        ),
                    )
                }
                reason == InventaireEcartReason.PERTE ||
                    reason == InventaireEcartReason.CASSE ||
                    reason == InventaireEcartReason.AUTRE -> {
                    stockRepository.adjust(
                        productId = product.id,
                        type = StockMovementType.PERTE,
                        quantity = -gap,
                        motif = reason.label,
                        comment = comment,
                    )
                }
                reason == InventaireEcartReason.ERREUR_SAISIE || reason == null -> {
                    stockRepository.adjust(
                        productId = product.id,
                        type = StockMovementType.INVENTAIRE,
                        quantity = 0,
                        motif = reason?.label ?: "Inventaire",
                        comment = comment,
                        absoluteNewStock = counted,
                    )
                }
            }
            _message.value = "${product.name} : écart $gap enregistré"
        } catch (e: Exception) {
            _message.value = e.message ?: "Échec inventaire"
        }
    }
}

@Composable
fun InventaireScreen(viewModel: InventaireViewModel = hiltViewModel()) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val counts = remember { mutableStateMapOf<Long, String>() }
    var pendingProduct by remember { mutableStateOf<Product?>(null) }
    var pendingCounted by remember { mutableStateOf(0) }
    var reason by remember { mutableStateOf(InventaireEcartReason.PERTE) }
    var comment by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageHeader(title = "Inventaire", subtitle = "Comptage tactile — écarts tracés")
        message?.let { TextPill(it, PillTone.SUCCESS) }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(products, key = { it.id }) { product ->
                val text = counts[product.id] ?: product.stock.toString()
                val counted = text.toIntOrNull() ?: product.stock
                val gap = counted - product.stock
                GlassCard {
                    Text(product.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Stock théorique : ${product.stock}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val next = (text.toIntOrNull() ?: product.stock).coerceAtLeast(1) - 1
                                counts[product.id] = next.coerceAtLeast(0).toString()
                            },
                            modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 56.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("−", style = MaterialTheme.typography.headlineSmall) }

                        OutlinedTextField(
                            value = text,
                            onValueChange = { counts[product.id] = it.filter { c -> c.isDigit() } },
                            label = { Text("Compté") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )

                        OutlinedButton(
                            onClick = {
                                val next = (text.toIntOrNull() ?: product.stock) + 1
                                counts[product.id] = next.toString()
                            },
                            modifier = Modifier.heightIn(min = 52.dp).widthIn(min = 56.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
                    }
                    if (gap != 0) {
                        TextPill(
                            "Écart : ${if (gap > 0) "+" else ""}$gap",
                            if (gap < 0) PillTone.DANGER else PillTone.SUCCESS,
                        )
                    }
                    Button(
                        onClick = {
                            if (gap == 0) {
                                viewModel.applyCount(product, counted, null, null)
                            } else if (gap > 0) {
                                viewModel.applyCount(
                                    product,
                                    counted,
                                    InventaireEcartReason.ERREUR_SAISIE,
                                    "Surplus inventaire",
                                )
                            } else {
                                pendingProduct = product
                                pendingCounted = counted
                                reason = InventaireEcartReason.PERTE
                                comment = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (gap == 0) "Confirmer OK" else "Valider le comptage")
                    }
                }
            }
        }
    }

    val product = pendingProduct
    if (product != null) {
        val gap = pendingCounted - product.stock
        AlertDialog(
            onDismissRequest = { pendingProduct = null },
            title = { Text("Pourquoi −${-gap} ?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${product.name}")
                    Text("Théorique ${product.stock} · Compté $pendingCounted")
                    TextPill("Écart : $gap", PillTone.DANGER)
                    InventaireEcartReason.entries.forEach { option ->
                        FilterChip(
                            selected = reason == option,
                            onClick = { reason = option },
                            label = { Text(option.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        label = { Text("Commentaire") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyCount(product, pendingCounted, reason, comment.ifBlank { null })
                        pendingProduct = null
                    },
                ) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingProduct = null }) { Text("Annuler") }
            },
        )
    }
}
