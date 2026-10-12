package com.maquis.caisse.ui.commandes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.theme.GestionBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandesScreen(
    onOpenOrder: (Long) -> Unit,
    viewModel: CommandesViewModel = hiltViewModel(),
) {
    val orders by viewModel.openOrders.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filtered = viewModel.filtered(orders)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageHeader(
                title = "Commandes en cours",
                subtitle = "Touche une commande pour marquer comme payée.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextPill("${filtered.size} ouvertes", PillTone.INFO)
                if (filtered.isNotEmpty()) {
                    TextPill(
                        "Reste ${MoneyFormat.format(filtered.sumOf { it.remainingAmount })}",
                        PillTone.WARNING,
                    )
                }
            }
            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Rechercher par ID…") },
            )
            if (filtered.isEmpty()) {
                GlassCard {
                    Text("Aucune commande en cours", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { order ->
                        OrderCard(order, timeFmt) { onOpenOrder(order.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: Order, timeFmt: SimpleDateFormat, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                order.publicId,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GestionBlue,
            )
            StatusPill(order.status)
        }
        Text(
            timeFmt.format(Date(order.createdAtEpochMs)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextPill("Total ${MoneyFormat.format(order.totalAmount)}", PillTone.CYAN)
            if (order.remainingAmount > 0L) {
                Text(
                    "Reste ${MoneyFormat.format(order.remainingAmount)}",
                    fontWeight = FontWeight.Bold,
                    color = GestionBlue,
                )
            }
        }
    }
}

/** Conservé pour Historique (liste dense). */
@Composable
fun OrderRow(order: Order, timeFmt: SimpleDateFormat, onClick: () -> Unit) {
    OrderCard(order, timeFmt, onClick)
}

@Composable
fun CompactOrderRow(order: Order, timeFmt: SimpleDateFormat, onClick: () -> Unit) {
    OrderCard(order, timeFmt, onClick)
}

@Composable
fun StatusPill(status: OrderStatus) {
    val tone = when (status) {
        OrderStatus.PAYEE -> PillTone.SUCCESS
        OrderStatus.EN_COURS -> PillTone.INFO
        OrderStatus.NON_PAYEE -> PillTone.WARNING
        OrderStatus.ANNULEE -> PillTone.DANGER
    }
    TextPill(status.label, tone)
}
