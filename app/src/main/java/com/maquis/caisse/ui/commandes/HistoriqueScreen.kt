package com.maquis.caisse.ui.commandes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HistoriqueScreen(
    onOpenOrder: (Long) -> Unit,
    viewModel: HistoriqueViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val displayed = viewModel.filterDisplayed(orders)
    val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val wide = maxWidth >= 900.dp
        val compactList = maxWidth < 700.dp

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HistoryListPane(
                    modifier = Modifier.weight(1.4f).fillMaxSize(),
                    ui = ui,
                    displayed = displayed,
                    timeFmt = timeFmt,
                    compactList = false,
                    viewModel = viewModel,
                    onOpenOrder = onOpenOrder,
                )
                HistoryStatsPane(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    ui = ui,
                    viewModel = viewModel,
                    fillProductList = true,
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HistoryListPane(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.15f),
                    ui = ui,
                    displayed = displayed,
                    timeFmt = timeFmt,
                    compactList = compactList,
                    viewModel = viewModel,
                    onOpenOrder = onOpenOrder,
                )
                HistoryStatsPane(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    ui = ui,
                    viewModel = viewModel,
                    fillProductList = true,
                )
            }
        }
    }
}

@Composable
private fun HistoryListPane(
    modifier: Modifier,
    ui: HistoriqueUiState,
    displayed: List<com.maquis.caisse.domain.model.Order>,
    timeFmt: SimpleDateFormat,
    compactList: Boolean,
    viewModel: HistoriqueViewModel,
    onOpenOrder: (Long) -> Unit,
) {
    Column(modifier = modifier) {
        PageHeader(
            title = "Historique",
            subtitle = "Toutes les commandes filtrables",
        )
        TextPill("${displayed.size} résultats", PillTone.INFO)
        OutlinedTextField(
            value = ui.query,
            onValueChange = viewModel::onQuery,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            singleLine = true,
            placeholder = { Text("ID, date, heure, produit…") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DropdownField(
                label = "Période",
                selected = ui.period,
                options = HistoryPeriod.entries,
                optionLabel = {
                    when (it) {
                        HistoryPeriod.TODAY -> "Aujourd'hui"
                        HistoryPeriod.WEEK -> "7 jours"
                        HistoryPeriod.MONTH -> "30 jours"
                        HistoryPeriod.ALL -> "Tout"
                    }
                },
                onSelect = { it?.let(viewModel::onPeriod) },
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "Statut",
                selected = ui.status,
                options = OrderStatus.entries,
                optionLabel = { it.label },
                onSelect = viewModel::onStatus,
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            DropdownField(
                label = "Paiement",
                selected = ui.paymentMode,
                options = PaymentMode.PAYMENT_CHOICES,
                optionLabel = { it.label },
                onSelect = viewModel::onPaymentMode,
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "Catégorie",
                selected = ui.categoryFilter,
                options = ui.categories,
                optionLabel = { it },
                onSelect = viewModel::onCategory,
                allowNull = true,
                nullLabel = "Toutes",
                modifier = Modifier.weight(1f),
            )
        }
        if (!compactList) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("ID", Modifier.weight(1.3f), style = MaterialTheme.typography.labelLarge)
                Text("Heure", Modifier.weight(0.7f), style = MaterialTheme.typography.labelLarge)
                Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("Statut", Modifier.weight(0.9f), style = MaterialTheme.typography.labelLarge)
            }
            HorizontalDivider()
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(displayed, key = { it.id }) { order ->
                if (compactList) {
                    CompactOrderRow(order, timeFmt) { onOpenOrder(order.id) }
                } else {
                    OrderRow(order, timeFmt) { onOpenOrder(order.id) }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryStatsPane(
    modifier: Modifier,
    ui: HistoriqueUiState,
    viewModel: HistoriqueViewModel,
    fillProductList: Boolean,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Statistiques", style = MaterialTheme.typography.titleLarge)
        val totalRevenue = ui.categoryRows.sumOf { it.revenue }
        val totalCost = ui.categoryRows.sumOf { it.cost }
        val totalProfit = totalRevenue - totalCost
        if (ui.categoryRows.isNotEmpty()) {
            val margin = if (totalRevenue > 0L) ((totalProfit * 100L) / totalRevenue).toInt() else 0
            Text("CA : ${MoneyFormat.format(totalRevenue)}", fontWeight = FontWeight.SemiBold)
            Text("Coût d'achat : ${MoneyFormat.format(totalCost)}", fontWeight = FontWeight.SemiBold)
            Text(
                "Bénéfice : ${MoneyFormat.format(totalProfit)} ($margin %)",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text("Aucune donnée pour cette période", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("Par catégorie (quantité · CA · bénéfice)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        ui.categoryRows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(row.categoryName, Modifier.weight(1.2f))
                Text("${row.quantity}", Modifier.weight(0.5f))
                Text(MoneyFormat.format(row.revenue), Modifier.weight(1f))
                Text(MoneyFormat.format(row.benefice), Modifier.weight(1f))
            }
        }
        if (ui.categoryRows.isNotEmpty()) {
            val totalQty = ui.categoryRows.sumOf { it.quantity }
            val totalRev = ui.categoryRows.sumOf { it.revenue }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                Text("$totalQty", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                Text(MoneyFormat.format(totalRev), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(MoneyFormat.format(totalRev - totalCost), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        }

        Text("Par produit (quantité · CA · bénéfice)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(
            modifier = if (fillProductList) {
                Modifier.weight(1f)
            } else {
                Modifier.heightIn(max = 240.dp)
            },
        ) {
            items(ui.productRows, key = { it.productName }) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.productName, Modifier.weight(1.2f), maxLines = 1)
                    Text("${row.quantity}", Modifier.weight(0.4f))
                    Text(MoneyFormat.format(row.revenue), Modifier.weight(1f))
                    Text(MoneyFormat.format(row.benefice), Modifier.weight(1f))
                }
            }
        }
    }
}
