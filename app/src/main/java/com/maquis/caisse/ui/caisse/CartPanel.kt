package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.maquis.caisse.domain.model.formatUnits
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.cart.ProfitSummary
import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionSuccess

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CartPanel(
    lines: List<CartLine>,
    total: Long,
    profit: ProfitSummary? = null,
    onLineLongPress: (CartLine) -> Unit,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    onSaveOrder: (() -> Unit)? = null,
    onReprint: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
        color = Color.White.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Panier",
                        style = MaterialTheme.typography.titleLarge,
                        color = GestionBlue,
                        fontWeight = FontWeight.Bold,
                    )
                    if (lines.isNotEmpty()) {
                        TextPill("${lines.sumOf { it.quantity }} articles", PillTone.INFO)
                    }
                }
                if (onClear != null && lines.isNotEmpty()) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Text("Vider", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (lines.isEmpty()) {
                Text(
                    text = "Ajouter un produit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(lines, key = { it.productId }) { line ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = GestionBlue.copy(alpha = 0.06f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onLineLongPress(line) },
                                    onLongClick = { onLineLongPress(line) },
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        line.productName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        if (line.isPartial) {
                                            "${line.detailLabel ?: (formatUnits(line.units) + " unité(s)")} · détail"
                                        } else {
                                            "${line.quantity} × ${MoneyFormat.format(line.unitPrice)}"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = MoneyFormat.format(line.lineTotal),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GestionBlue,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            TextPill(
                text = "Total ${MoneyFormat.format(total)}",
                tone = PillTone.SUCCESS,
                modifier = Modifier.padding(top = 8.dp, bottom = if (profit != null) 2.dp else 8.dp),
            )

            if (profit != null && lines.isNotEmpty()) {
                Text(
                    text = "Bénéfice estimé ${MoneyFormat.format(profit.profit)} (${profit.marginPercent} %)" +
                        if (profit.incomplete) " · prix d'achat manquant" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (profit.profit >= 0L) GestionSuccess else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (onSaveOrder != null) {
                OutlinedButton(
                    onClick = onSaveOrder,
                    enabled = lines.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(bottom = 4.dp),
                ) {
                    Text("Enregistrer commande", style = MaterialTheme.typography.titleMedium)
                }
            }

            Button(
                onClick = onValidate,
                enabled = lines.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = GestionSuccess),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("Encaisser", style = MaterialTheme.typography.titleMedium)
            }

            if (onReprint != null && lines.isEmpty()) {
                OutlinedButton(
                    onClick = onReprint,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .padding(top = 6.dp),
                ) {
                    Text(
                        "🖨  Réimprimer le dernier ticket",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
