package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.core.Constants
import com.maquis.caisse.domain.cart.DetailSale
import com.maquis.caisse.domain.model.Product

/** Quel champ le vendeur a rempli en dernier (l'autre est calculé). */
private enum class Edited { AMOUNT, QUANTITY }

/**
 * Vente libre au détail : deux champs liés.
 * - le vendeur tape le MONTANT → la quantité (litres…) se calcule ;
 * - ou il tape la QUANTITÉ → le montant se calcule.
 */
@Composable
fun DetailSaleDialog(
    product: Product,
    initialAmount: Long?,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (amount: Long, fraction: Double, label: String) -> Unit,
) {
    val unit = product.detailUnit?.takeIf { it.isNotBlank() } ?: "L"
    val stockUnit = product.stockUnit?.takeIf { it.isNotBlank() } ?: "unité"
    val price = product.detailPrice
    val content = product.contentPerUnit

    var amountText by remember { mutableStateOf(initialAmount?.toString().orEmpty()) }
    var quantityText by remember { mutableStateOf(DetailSale.quantityText(initialAmount, price)) }
    var edited by remember { mutableStateOf(Edited.AMOUNT) }

    val amount: Long? = amountText.toLongOrNull()?.takeIf { it > 0L }
    val quantity: Double? = if (edited == Edited.QUANTITY) {
        DetailSale.parseQuantity(quantityText)
    } else {
        amount?.let { DetailSale.quantityFromAmount(it, price) }
    }
    val valid = amount != null && quantity != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${MoneyFormat.format(price)} le ${unit.lowercase()} · 1 ${stockUnit.lowercase()} = " +
                        "${DetailSale.formatQuantity(content)} ${unit.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v ->
                        val digits = v.filter { c -> c.isDigit() }.take(Constants.MAX_MONEY_DIGITS)
                        amountText = digits
                        edited = Edited.AMOUNT
                        quantityText = DetailSale.quantityText(digits.toLongOrNull(), price)
                    },
                    label = { Text("Prix (F)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { v ->
                        val clean = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(10)
                        quantityText = clean
                        edited = Edited.QUANTITY
                        val q = DetailSale.parseQuantity(clean)
                        amountText = q?.let { DetailSale.amountFromQuantity(it, price).toString() }.orEmpty()
                    },
                    label = { Text("Nombre de ${unit.lowercase()}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (valid && quantity != null) {
                    Text(
                        "= ${DetailSale.formatQuantity(DetailSale.fractionFromQuantity(quantity, content))} " +
                            "unité de stock",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val q = quantity
                    val a = amount
                    if (q != null && a != null) {
                        onConfirm(
                            a,
                            DetailSale.fractionFromQuantity(q, content),
                            "${DetailSale.formatQuantity(q)} $unit",
                        )
                    }
                },
            ) { Text(if (isEditing) "Modifier" else "Ajouter au panier", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Column {
                if (isEditing) {
                    TextButton(onClick = onDelete) { Text("Supprimer la ligne") }
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
