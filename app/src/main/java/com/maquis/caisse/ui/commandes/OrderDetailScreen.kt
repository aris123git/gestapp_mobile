package com.maquis.caisse.ui.commandes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderLine
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    orderId: Long,
    onBack: () -> Unit,
    onPaidGoToOpenOrders: () -> Unit = onBack,
    viewModel: OrderDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showPay by remember { mutableStateOf(false) }
    var showDebt by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val df = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE) }

    LaunchedEffect(orderId) { viewModel.load(orderId) }

    LaunchedEffect(state.navigateToOpenOrders) {
        if (state.navigateToOpenOrders) {
            viewModel.consumeNavigateToOpenOrders()
            onPaidGoToOpenOrders()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.order?.publicId ?: "Commande") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    state.order?.let { order ->
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(order.publicId))
                            },
                        ) { Text("Copier ID") }
                    }
                },
            )
        },
    ) { padding ->
        val order = state.order
        if (order == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                if (state.isBusy) CircularProgressIndicator()
                else Text(state.error ?: "Commande introuvable")
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
        ) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OrderHeader(order, df, state.message, state.error, state.profitOrNull)
                        OrderLines(
                            lines = if (state.editing) state.editLines else order.items,
                            editing = state.editing,
                            onRemove = viewModel::removeLine,
                        )
                        OrderPayments(order)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .widthIn(min = 240.dp, max = 420.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OrderActions(
                            order = order,
                            editing = state.editing,
                            canModifyOrCancel = state.canModifyOrCancel,
                            canMarkPaid = state.canMarkPaid,
                            editLinesEmpty = state.editLines.isEmpty(),
                            onStartEdit = viewModel::startEdit,
                            onSaveEdits = viewModel::saveEdits,
                            onCancelEdit = viewModel::cancelEdit,
                            onCancelOrder = viewModel::cancelOrder,
                            onMarkPaid = { showPay = true },
                            onPutDebt = { showDebt = true },
                            onPrint = viewModel::printTicket,
                        )
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OrderHeader(order, df, state.message, state.error, state.profitOrNull)
                    OrderLines(
                        lines = if (state.editing) state.editLines else order.items,
                        editing = state.editing,
                        onRemove = viewModel::removeLine,
                    )
                    OrderPayments(order)
                    OrderActions(
                        order = order,
                        editing = state.editing,
                        canModifyOrCancel = state.canModifyOrCancel,
                        canMarkPaid = state.canMarkPaid,
                        editLinesEmpty = state.editLines.isEmpty(),
                        onStartEdit = viewModel::startEdit,
                        onSaveEdits = viewModel::saveEdits,
                        onCancelEdit = viewModel::cancelEdit,
                        onCancelOrder = viewModel::cancelOrder,
                        onMarkPaid = { showPay = true },
                        onPutDebt = { showDebt = true },
                        onPrint = viewModel::printTicket,
                    )
                }
            }
        }
    }

    if (showPay) {
        val remaining = state.order?.remainingAmount ?: 0L
        val defaultCustomer = ""
        PayOrderDialog(
            remaining = remaining,
            defaultCustomerName = defaultCustomer,
            onDismiss = { showPay = false },
            onConfirm = { mode, received, full, remainderDebt, customer ->
                showPay = false
                viewModel.markPaid(
                    mode = mode,
                    amountReceived = received,
                    payFull = full,
                    putRemainderAsDebt = remainderDebt,
                    debtCustomerName = customer,
                )
            },
        )
    }

    if (showDebt) {
        val remaining = state.order?.remainingAmount ?: 0L
        val defaultCustomer = ""
        PutOnDebtDialog(
            remaining = remaining,
            defaultCustomerName = defaultCustomer,
            onDismiss = { showDebt = false },
            onConfirm = { customer ->
                showDebt = false
                viewModel.putRemainingOnDebt(customer)
            },
        )
    }
}

@Composable
private fun OrderHeader(
    order: Order,
    df: SimpleDateFormat,
    message: String?,
    error: String?,
    profit: Long? = null,
) {
    GlassCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                df.format(Date(order.createdAtEpochMs)),
                style = MaterialTheme.typography.titleMedium,
                color = GestionBlue,
                fontWeight = FontWeight.Bold,
            )
            StatusPill(order.status)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPill("Total ${MoneyFormat.format(order.totalAmount)}", PillTone.CYAN)
            TextPill("Payé ${MoneyFormat.format(order.paidAmount)}", PillTone.SUCCESS)
            if (order.remainingAmount > 0L) {
                TextPill("Reste ${MoneyFormat.format(order.remainingAmount)}", PillTone.WARNING)
            }
        }
        if (profit != null) {
            TextPill(
                "Bénéfice ${MoneyFormat.format(profit)}",
                if (profit >= 0L) PillTone.SUCCESS else PillTone.DANGER,
            )
        }
        message?.let { TextPill(it, PillTone.SUCCESS) }
        error?.let { TextPill(it, PillTone.DANGER) }
    }
}

@Composable
private fun OrderLines(
    lines: List<OrderLine>,
    editing: Boolean,
    onRemove: (Long) -> Unit,
) {
    lines.forEach { item ->
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${item.productName} ×${item.quantity}")
                Text(
                    MoneyFormat.format(item.unitPrice),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(MoneyFormat.format(item.lineTotal))
            if (editing) {
                TextButton(onClick = { onRemove(item.productId) }) {
                    Text("Retirer")
                }
            }
        }
    }
}

@Composable
private fun OrderPayments(order: Order) {
    if (order.payments.isEmpty()) return
    Text("Paiements", fontWeight = FontWeight.SemiBold)
    order.payments.forEach { p ->
        Text(
            "${p.paymentMode.label} ${MoneyFormat.format(p.amount)}" +
                if (p.changeAmount > 0) {
                    " · monnaie ${MoneyFormat.format(p.changeAmount)}"
                } else {
                    ""
                },
        )
    }
}

@Composable
private fun OrderActions(
    order: Order,
    editing: Boolean,
    canModifyOrCancel: Boolean,
    canMarkPaid: Boolean,
    editLinesEmpty: Boolean,
    onStartEdit: () -> Unit,
    onSaveEdits: () -> Unit,
    onCancelEdit: () -> Unit,
    onCancelOrder: () -> Unit,
    onMarkPaid: () -> Unit,
    onPutDebt: () -> Unit,
    onPrint: () -> Unit,
) {
    if (order.isOpen) {
        if (!editing) {
            if (canMarkPaid) {
                Button(
                    onClick = onMarkPaid,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("MARQUER COMME PAYÉ", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onPutDebt,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("METTRE EN DETTE", fontWeight = FontWeight.Bold, color = GestionWarning)
                }
            }
            if (canModifyOrCancel) {
                OutlinedButton(
                    onClick = onStartEdit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Modifier la commande") }
                OutlinedButton(
                    onClick = onCancelOrder,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Annuler la commande") }
            }
        } else if (canModifyOrCancel) {
            Button(
                onClick = onSaveEdits,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !editLinesEmpty,
            ) { Text("Enregistrer les modifications") }
            OutlinedButton(
                onClick = onCancelEdit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Annuler l'édition") }
        }
    }

    Spacer(Modifier.height(4.dp))
    OutlinedButton(
        onClick = onPrint,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("Imprimer le ticket") }
}

@Composable
private fun PutOnDebtDialog(
    remaining: Long,
    defaultCustomerName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var customer by remember { mutableStateOf(defaultCustomerName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mettre en dette") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Montant : ${MoneyFormat.format(remaining)}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = customer,
                    onValueChange = { customer = it },
                    label = { Text("Nom du client") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(customer.trim()) },
                enabled = remaining > 0L,
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun PayOrderDialog(
    remaining: Long,
    defaultCustomerName: String,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMode, Long, Boolean, Boolean, String) -> Unit,
) {
    var mode by remember { mutableStateOf(PaymentMode.CASH) }
    var amountText by remember(remaining) { mutableStateOf(remaining.toString()) }
    var replaceNext by remember(remaining, mode) { mutableStateOf(true) }
    var partial by remember { mutableStateOf(false) }
    var remainderAsDebt by remember { mutableStateOf(false) }
    var customer by remember { mutableStateOf(defaultCustomerName) }
    val received = amountText.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val debtPart = if (partial && remainderAsDebt && received in 1 until remaining) {
        remaining - received
    } else {
        0L
    }
    val change = if (!partial && mode == PaymentMode.CASH && received > remaining) {
        received - remaining
    } else {
        0L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marquer comme payé") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Reste à payer : ${MoneyFormat.format(remaining)}")
                Text("Mode de paiement", fontWeight = FontWeight.SemiBold)
                PaymentMode.PAYMENT_CHOICES.filter { it != PaymentMode.MIXED }.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(m.label) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        val digits = raw.filter { c -> c.isDigit() }
                        amountText = if (replaceNext) {
                            replaceNext = false
                            digits.takeLast(1).ifEmpty { "" }
                        } else {
                            digits
                        }
                    },
                    label = { Text(if (mode == PaymentMode.CASH) "Montant reçu" else "Montant") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (mode == PaymentMode.CASH) {
                    Text("Monnaie : ${MoneyFormat.format(change)}")
                }
                FilterChip(
                    selected = partial,
                    onClick = {
                        partial = !partial
                        if (!partial) remainderAsDebt = false
                    },
                    label = { Text("Paiement partiel") },
                )
                if (partial && received in 1 until remaining) {
                    FilterChip(
                        selected = remainderAsDebt,
                        onClick = { remainderAsDebt = !remainderAsDebt },
                        label = { Text("Mettre le reste en dette") },
                    )
                    if (remainderAsDebt) {
                        Text(
                            "Dette : ${MoneyFormat.format(debtPart)}",
                            color = GestionWarning,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedTextField(
                            value = customer,
                            onValueChange = { customer = it },
                            label = { Text("Nom du client (dette)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(mode, received, !partial, remainderAsDebt, customer.trim())
                },
                enabled = received > 0L &&
                    (partial || received >= remaining || mode != PaymentMode.CASH),
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
