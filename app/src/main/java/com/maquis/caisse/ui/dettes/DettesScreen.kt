package com.maquis.caisse.ui.dettes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.maquis.caisse.domain.model.Dette
import com.maquis.caisse.domain.model.DetteStatus
import com.maquis.caisse.domain.repository.DetteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class DettesViewModel @Inject constructor(
    private val repository: DetteRepository,
    private val session: SessionManager,
) : ViewModel() {

    val dettes: StateFlow<List<Dette>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createDette(
        customerName: String,
        customerPhone: String,
        amount: Long,
        note: String,
    ) = viewModelScope.launch {
        val user = session.user()
        repository.createDette(
            customerName = customerName,
            customerPhone = customerPhone,
            amount = amount,
            orderId = null,
            orderPublicId = null,
            userId = user.id,
            userName = user.name,
            note = note,
        )
    }

    fun recordPaiement(detteId: Long, amount: Long, note: String) = viewModelScope.launch {
        val user = session.user()
        repository.recordPaiement(
            detteId = detteId,
            amount = amount,
            userId = user.id,
            userName = user.name,
            note = note,
        )
    }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun DettesScreen(viewModel: DettesViewModel = hiltViewModel()) {
    val allDettes by viewModel.dettes.collectAsStateWithLifecycle()
    var filterStatus by remember { mutableStateOf<DetteStatus?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDette by remember { mutableStateOf<Dette?>(null) }

    val filtered = if (filterStatus == null) allDettes
    else allDettes.filter { it.status == filterStatus }

    val totalRestant = allDettes
        .filter { !it.isSettled }
        .sumOf { it.remainingAmount }

    if (showAddDialog) {
        AddDetteDialog(
            onConfirm = { name, phone, amount, note ->
                viewModel.createDette(name, phone, amount, note)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    selectedDette?.let { dette ->
        RecordPaiementDialog(
            dette = dette,
            onConfirm = { amount, note ->
                viewModel.recordPaiement(dette.id, amount, note)
                selectedDette = null
            },
            onDismiss = { selectedDette = null },
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouvelle dette")
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

            // Résumé
            item {
                SummaryCard(totalRestant = totalRestant, totalCount = allDettes.count { !it.isSettled })
            }

            // Filtres
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterStatus == null,
                        onClick = { filterStatus = null },
                        label = { Text("Toutes") },
                    )
                    FilterChip(
                        selected = filterStatus == DetteStatus.OPEN,
                        onClick = { filterStatus = DetteStatus.OPEN },
                        label = { Text("Impayées") },
                    )
                    FilterChip(
                        selected = filterStatus == DetteStatus.PARTIAL,
                        onClick = { filterStatus = DetteStatus.PARTIAL },
                        label = { Text("Partielles") },
                    )
                    FilterChip(
                        selected = filterStatus == DetteStatus.SETTLED,
                        onClick = { filterStatus = DetteStatus.SETTLED },
                        label = { Text("Réglées") },
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Aucune dette enregistrée.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { dette ->
                    DetteCard(
                        dette = dette,
                        onPayer = { selectedDette = dette },
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Composables ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(totalRestant: Long, totalCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
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
                    "Total dettes en cours",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "$totalCount client(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                )
            }
            Text(
                MoneyFormat.format(totalRestant),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun DetteCard(dette: Dette, onPayer: () -> Unit) {
    val df = remember { SimpleDateFormat("dd/MM/yy", Locale.FRANCE) }
    var expanded by remember { mutableStateOf(false) }

    val statusColor = when (dette.status) {
        DetteStatus.OPEN -> MaterialTheme.colorScheme.error
        DetteStatus.PARTIAL -> Color(0xFFF57C00)
        DetteStatus.SETTLED -> Color(0xFF388E3C)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dette.customerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (dette.customerPhone.isNotBlank()) {
                        Text(
                            dette.customerPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (dette.orderPublicId != null) {
                        Text(
                            "Cmd. ${dette.orderPublicId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        df.format(Date(dette.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        dette.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                    Text(
                        MoneyFormat.format(dette.remainingAmount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                    Text(
                        "/ ${MoneyFormat.format(dette.originalAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (dette.paiements.isEmpty()) {
                    Text(
                        "Aucun paiement enregistré.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        dette.paiements.forEach { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${p.userName} — ${df.format(Date(p.paidAt))}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "+ ${MoneyFormat.format(p.amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF388E3C),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (p.note.isNotBlank()) {
                                Text(
                                    p.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (!dette.isSettled) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onPayer,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Enregistrer un paiement")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddDetteDialog(
    onConfirm: (String, String, Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val canConfirm = name.isNotBlank() && (amount.toLongOrNull() ?: 0L) > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle dette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du client *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() } },
                    label = { Text("Montant FCFA *") },
                    singleLine = true,
                    suffix = { Text("FCFA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
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
                onClick = { onConfirm(name, phone, amount.toLong(), note) },
                enabled = canConfirm,
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun RecordPaiementDialog(
    dette: Dette,
    onConfirm: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val parsed = amount.toLongOrNull() ?: 0L
    val canConfirm = parsed > 0L && parsed <= dette.remainingAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paiement — ${dette.customerName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Reste dû", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        MoneyFormat.format(dette.remainingAmount),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() } },
                    label = { Text("Montant encaissé *") },
                    singleLine = true,
                    suffix = { Text("FCFA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = parsed > dette.remainingAmount,
                    supportingText = if (parsed > dette.remainingAmount) {
                        { Text("Supérieur au montant restant") }
                    } else null,
                )
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
                onClick = { onConfirm(parsed, note) },
                enabled = canConfirm,
            ) { Text("Confirmer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
