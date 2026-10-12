package com.maquis.caisse.ui.avoirs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.Avoir
import com.maquis.caisse.domain.model.AvoirLine
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.repository.AvoirRepository
import com.maquis.caisse.domain.repository.ProductRepository
import com.maquis.caisse.ui.common.DropdownField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AvoirsViewModel @Inject constructor(
    private val repository: AvoirRepository,
    private val productRepository: ProductRepository,
    private val session: SessionManager,
) : ViewModel() {

    val avoirs: StateFlow<List<Avoir>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val products: StateFlow<List<Product>> = productRepository.observeActiveProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createAvoir(
        customerName: String,
        reason: String,
        amount: Long,
        note: String,
        items: List<AvoirLine>,
    ) = viewModelScope.launch {
        val user = session.user()
        repository.createAvoir(
            orderId = null,
            orderPublicId = null,
            customerName = customerName,
            reason = reason,
            amount = amount,
            userId = user.id,
            userName = user.name,
            note = note,
            items = items,
            restoreStock = items.isNotEmpty(),
        )
    }
}

@Composable
fun AvoirsScreen(viewModel: AvoirsViewModel = hiltViewModel()) {
    val avoirs by viewModel.avoirs.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    val totalAvoirs = avoirs.sumOf { it.amount }

    if (showAddDialog) {
        AddAvoirDialog(
            products = products,
            onConfirm = { customer, reason, amount, note, items ->
                viewModel.createAvoir(customer, reason, amount, note, items)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouvel avoir")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "Total avoirs / remboursements",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "${avoirs.size} enregistrement(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                            )
                        }
                        Text(
                            MoneyFormat.format(totalAvoirs),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

            if (avoirs.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Aucun avoir enregistré.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(avoirs, key = { it.id }) { avoir ->
                    AvoirCard(avoir = avoir)
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AvoirCard(avoir: Avoir) {
    val df = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.FRANCE) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    avoir.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (avoir.avoirType == "PRODUCT" && avoir.items.isNotEmpty()) {
                    Text(
                        avoir.items.joinToString(" · ") { "${it.productName} ×${it.quantity}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (avoir.customerName.isNotBlank()) {
                    Text(
                        avoir.customerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (avoir.orderPublicId != null) {
                    Text(
                        "Cmd. ${avoir.orderPublicId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${avoir.userName} — ${df.format(Date(avoir.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (avoir.note.isNotBlank()) {
                    Text(
                        avoir.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "- ${MoneyFormat.format(avoir.amount)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF57C00),
            )
        }
    }
}

private enum class AvoirMode { CASH, PRODUCTS }

@Composable
private fun AddAvoirDialog(
    products: List<Product>,
    onConfirm: (String, String, Long, String, List<AvoirLine>) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(AvoirMode.PRODUCTS) }
    var customer by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<AvoirLine>>(emptyList()) }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var qtyText by remember { mutableStateOf("1") }

    val linesTotal = lines.sumOf { it.lineTotal }
    val canConfirm = reason.isNotBlank() && when (mode) {
        AvoirMode.CASH -> (amount.toLongOrNull() ?: 0L) > 0L
        AvoirMode.PRODUCTS -> lines.isNotEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel avoir / remboursement") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == AvoirMode.PRODUCTS,
                        onClick = { mode = AvoirMode.PRODUCTS },
                        label = { Text("Produits") },
                    )
                    FilterChip(
                        selected = mode == AvoirMode.CASH,
                        onClick = { mode = AvoirMode.CASH },
                        label = { Text("Montant") },
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = { Text("Client (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (mode == AvoirMode.CASH) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() } },
                        label = { Text("Montant FCFA *") },
                        singleLine = true,
                        suffix = { Text("FCFA") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Produits / bouteilles", fontWeight = FontWeight.SemiBold)
                    DropdownField(
                        label = "Produit",
                        selected = selectedProduct,
                        options = products,
                        optionLabel = { "${it.name} (${MoneyFormat.format(it.salePrice)})" },
                        onSelect = { selectedProduct = it },
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = qtyText,
                            onValueChange = { qtyText = it.filter { c -> c.isDigit() }.ifEmpty { "" } },
                            label = { Text("Quantité") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                val p = selectedProduct ?: return@OutlinedButton
                                val qty = qtyText.toIntOrNull() ?: return@OutlinedButton
                                if (qty <= 0) return@OutlinedButton
                                val existing = lines.indexOfFirst { it.productId == p.id }
                                lines = if (existing >= 0) {
                                    lines.toMutableList().also { list ->
                                        val cur = list[existing]
                                        list[existing] = cur.copy(quantity = cur.quantity + qty)
                                    }
                                } else {
                                    lines + AvoirLine(
                                        productId = p.id,
                                        productName = p.name,
                                        unitPrice = p.salePrice,
                                        quantity = qty,
                                    )
                                }
                                qtyText = "1"
                            },
                            enabled = selectedProduct != null && (qtyText.toIntOrNull() ?: 0) > 0,
                        ) { Text("Ajouter") }
                    }
                    lines.forEach { line ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${line.productName} ×${line.quantity}")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(MoneyFormat.format(line.lineTotal))
                                TextButton(
                                    onClick = {
                                        lines = lines.filterNot { it.productId == line.productId }
                                    },
                                ) { Text("Retirer") }
                            }
                        }
                    }
                    if (lines.isNotEmpty()) {
                        Text(
                            "Total : ${MoneyFormat.format(linesTotal)}",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Le stock sera remis pour ces produits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        customer,
                        reason,
                        if (mode == AvoirMode.CASH) amount.toLong() else linesTotal,
                        note,
                        if (mode == AvoirMode.PRODUCTS) lines else emptyList(),
                    )
                },
                enabled = canConfirm,
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
