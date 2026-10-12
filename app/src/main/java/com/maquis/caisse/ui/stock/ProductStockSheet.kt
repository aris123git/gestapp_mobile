package com.maquis.caisse.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.model.StockMovementType
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProductDayStats(
    val entrees: Int = 0,
    val ventes: Int = 0,
    val ventesHorsCaisse: Int = 0,
    val pertes: Int = 0,
    val sorties: Int = 0,
    val inventaires: Int = 0,
)

fun computeProductDayStats(movements: List<StockMovement>): ProductDayStats {
    fun qty(type: String) = movements.filter { it.type == type }.sumOf { it.quantity }
    val ventes = movements.filter { it.type == StockMovementType.VENTE }
    val horsCaisse = ventes.filter {
        it.motif.contains("hors caisse", ignoreCase = true) ||
            it.comment.orEmpty().contains("hors caisse", ignoreCase = true)
    }.sumOf { it.quantity }
    return ProductDayStats(
        entrees = qty(StockMovementType.ENTREE),
        ventes = ventes.sumOf { it.quantity },
        ventesHorsCaisse = horsCaisse,
        pertes = qty(StockMovementType.PERTE),
        sorties = qty(StockMovementType.SORTIE),
        inventaires = movements.count { it.type == StockMovementType.INVENTAIRE },
    )
}

@Composable
fun ProductStockSheet(
    product: Product,
    movements: List<StockMovement>,
    onDismiss: () -> Unit,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val stats = remember(movements) { computeProductDayStats(movements) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Stock actuel", style = MaterialTheme.typography.labelLarge)
                Text(
                    product.stock.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                Text("Aujourd'hui", fontWeight = FontWeight.SemiBold)
                StatLine("Entrées", "+${stats.entrees}", PillTone.SUCCESS)
                StatLine("Ventes", "−${stats.ventes}", PillTone.INFO)
                StatLine("Ventes hors caisse", "−${stats.ventesHorsCaisse}", PillTone.WARNING)
                StatLine("Pertes", "−${stats.pertes}", PillTone.DANGER)
                StatLine("Sorties", "−${stats.sorties}", PillTone.WARNING)
                HorizontalDivider()
                Text("Mouvements", fontWeight = FontWeight.SemiBold)
                if (movements.isEmpty()) {
                    Text("Aucun mouvement aujourd'hui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    movements.sortedByDescending { it.createdAtEpochMs }.forEach { m ->
                        GlassCard {
                            Text(
                                "${timeFmt.format(Date(m.createdAtEpochMs))} — ${StockMovementType.label(m.type)} ${StockMovementType.signedQuantity(m.type, m.quantity, m.previousStock, m.newStock)}",
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (m.motif.isNotBlank()) {
                                Text(m.motif, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun StatLine(label: String, value: String, tone: PillTone) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        TextPill(value, tone)
    }
}
