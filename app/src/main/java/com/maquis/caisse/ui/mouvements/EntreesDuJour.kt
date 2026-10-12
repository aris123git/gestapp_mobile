package com.maquis.caisse.ui.mouvements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.model.StockMovementType
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EntreeProduitGroup(
    val productId: Long,
    val productName: String,
    val totalQty: Int,
    val entries: List<StockMovement>,
)

fun groupEntreesByProduct(movements: List<StockMovement>): List<EntreeProduitGroup> {
    return movements
        .filter { it.type == StockMovementType.ENTREE }
        .groupBy { it.productId }
        .map { (productId, list) ->
            EntreeProduitGroup(
                productId = productId,
                productName = list.first().productName,
                totalQty = list.sumOf { it.quantity },
                entries = list.sortedBy { it.createdAtEpochMs },
            )
        }
        .sortedByDescending { it.totalQty }
}

@Composable
fun EntreesDuJourList(
    groups: List<EntreeProduitGroup>,
    onProductClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Entrées du jour",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Produits regroupés — appuie pour le détail",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (groups.isEmpty()) {
            item {
                Text(
                    "Aucune entrée sur la période.",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(groups, key = { it.productId }) { group ->
            GlassCard(onClick = { onProductClick(group.productId) }) {
                Text(group.productName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                TextPill("+${group.totalQty} unités", PillTone.SUCCESS)
                Text(
                    "${group.entries.size} entrée${if (group.entries.size > 1) "s" else ""}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                group.entries.forEach { e ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(timeFmt.format(Date(e.createdAtEpochMs)))
                        Text("+${e.quantity}", fontWeight = FontWeight.SemiBold)
                    }
                    if (e.motif.isNotBlank()) {
                        Text(e.motif, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
