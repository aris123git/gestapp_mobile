package com.maquis.caisse.ui.suivi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.ExpenseCategories
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.ui.charts.CustomPeriodPickers
import com.maquis.caisse.ui.charts.PeriodSelector
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.theme.GestionSuccess
import kotlinx.coroutines.delay

@Composable
fun SuiviAdminScreen(viewModel: SuiviAdminViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()

    val message = ui.error ?: ui.success
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            viewModel.consumeMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageHeader(
                title = "Mouvements de stock",
                subtitle = if (ui.isAdmin) {
                    "Par caissier et période"
                } else {
                    "Vos mouvements et dépenses"
                },
            )
            TextButton(onClick = viewModel::refresh) { Text("Actualiser") }
        }

        if (message != null) {
            TextPill(
                message,
                if (ui.error != null) PillTone.DANGER else PillTone.SUCCESS,
            )
        }

        if (ui.isAdmin) {
            DropdownField(
                label = "Caissier",
                selected = users.firstOrNull { it.id == ui.selectedUserId },
                options = users,
                optionLabel = { it.name },
                onSelect = { viewModel.setSelectedUser(it?.id) },
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PeriodSelector(selected = ui.period, onSelect = viewModel::onPeriod)
        CustomPeriodPickers(
            period = ui.period,
            customDayMs = ui.customDayMs,
            customFromMs = ui.customFromMs,
            customToMs = ui.customToMs,
            onCustomDay = viewModel::onCustomDay,
            onCustomRange = viewModel::onCustomRange,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MouvementsTab.entries.forEach { tab ->
                FilterChip(
                    selected = ui.tab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    label = { Text(tab.label) },
                )
            }
        }

        if (ui.tab == MouvementsTab.SORTIE) {
            FinancialSummary(
                ca = ui.ca,
                benefice = ui.benefice,
                expensesTotal = ui.expensesTotal,
                onAddExpense = viewModel::openAddExpense,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (ui.flatMode) {
                items(ui.flatMovements, key = { it.id }) { movement ->
                    MinimalMovementRow(movement)
                }
                if (ui.flatMovements.isEmpty() && !ui.loading) {
                    item {
                        Text(
                            "Aucun mouvement sur cette période.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            } else {
                ui.groups.forEachIndexed { groupIndex, group ->
                    item(key = "h-$groupIndex-${group.title}") {
                        Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                            Text(
                                group.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            group.subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(
                        items = group.movements,
                        key = { movement -> "g$groupIndex-${movement.id}" },
                    ) { movement ->
                        MinimalMovementRow(movement)
                    }
                    item(key = "d-$groupIndex-${group.title}") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (ui.groups.isEmpty() && !ui.loading) {
                    item {
                        Text(
                            "Aucun mouvement sur cette période.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            if (ui.tab == MouvementsTab.SORTIE && ui.expenses.isNotEmpty()) {
                item {
                    Text(
                        "Dépenses de la période",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(ui.expenses, key = { "e-${it.id}" }) { expense ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            listOfNotNull(expense.description, expense.category, expense.userName.ifBlank { null })
                                .joinToString(" · "),
                            modifier = Modifier.weight(1f),
                        )
                        Text(MoneyFormat.format(expense.amount), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (ui.showAddExpense) {
        AddExpenseDialog(
            description = ui.expenseDescription,
            amountText = ui.expenseAmountText,
            category = ui.expenseCategory,
            onDescription = viewModel::setExpenseDescription,
            onAmount = viewModel::setExpenseAmountText,
            onCategory = viewModel::setExpenseCategory,
            onDismiss = viewModel::closeAddExpense,
            onConfirm = viewModel::saveExpense,
        )
    }
}

@Composable
private fun FinancialSummary(
    ca: Long,
    benefice: Long,
    expensesTotal: Long,
    onAddExpense: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("CA (après dépenses) : ${MoneyFormat.format(ca)}", fontWeight = FontWeight.Bold)
        Text(
            "Bénéfice (après dépenses) : ${MoneyFormat.format(benefice)}",
            fontWeight = FontWeight.Bold,
            color = GestionSuccess,
        )
        Text("Dépenses : ${MoneyFormat.format(expensesTotal)}", fontWeight = FontWeight.Bold)
        Button(
            onClick = onAddExpense,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text("Ajouter une dépense")
        }
    }
}

@Composable
private fun MinimalMovementRow(movement: StockMovement) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            movement.productName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${movement.previousStock} → ${movement.newStock}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AddExpenseDialog(
    description: String,
    amountText: String,
    category: String,
    onDescription: (String) -> Unit,
    onAmount: (String) -> Unit,
    onCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle dépense") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescription,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = onAmount,
                    label = { Text("Montant (FCFA)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                DropdownField(
                    label = "Catégorie",
                    selected = category,
                    options = ExpenseCategories,
                    optionLabel = { it },
                    onSelect = { if (it != null) onCategory(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
