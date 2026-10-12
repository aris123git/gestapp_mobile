package com.maquis.caisse.ui.caisse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.core.Constants
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.payment.PaymentCalculator
import com.maquis.caisse.ui.components.NumericKeypad

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentDialog(
    total: Long,
    payment: PaymentFormState,
    onDismiss: () -> Unit,
    onSelectMode: (PaymentMode) -> Unit,
    onSelectField: (PaymentField) -> Unit,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val input = payment.toPaymentInput()
    val changePreview = PaymentCalculator.previewChange(total, input)
    val canConfirm = !payment.isSaving &&
        PaymentCalculator.validate(total, input).isSuccess

    Dialog(
        onDismissRequest = { if (!payment.isSaving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Paiement",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "Total : ${MoneyFormat.format(total)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (PaymentMode.PAYMENT_CHOICES + PaymentMode.DEBT).forEach { mode ->
                            FilterChip(
                                selected = payment.mode == mode,
                                onClick = { if (!payment.isSaving) onSelectMode(mode) },
                                label = { Text(mode.label) },
                                enabled = !payment.isSaving,
                            )
                        }
                    }

                    when (payment.mode) {
                        PaymentMode.CASH -> {
                            Text(
                                text = "Monnaie : ${MoneyFormat.format(changePreview)}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        PaymentMode.MIXED -> {
                            MixedFields(
                                payment = payment,
                                enabled = !payment.isSaving,
                                onSelectField = onSelectField,
                            )
                            if (changePreview > 0) {
                                Text(
                                    text = "Monnaie espèces : ${MoneyFormat.format(changePreview)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                        PaymentMode.ORANGE_MONEY,
                        PaymentMode.MOOV_MONEY,
                        PaymentMode.WAVE,
                        PaymentMode.CARD,
                        PaymentMode.OTHER,
                        PaymentMode.DEBT,
                        -> {
                            Text(
                                text = "Confirmer ${payment.mode.label} pour ${MoneyFormat.format(total)}",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }

                    payment.errorMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                val needsKeypad = payment.mode == PaymentMode.CASH ||
                    payment.mode == PaymentMode.MIXED

                if (needsKeypad) {
                    NumericKeypad(
                        value = payment.activeInput,
                        onValueChange = onInputChange,
                        onConfirm = onConfirm,
                        title = fieldLabel(payment),
                        maxDigits = Constants.MAX_MONEY_DIGITS,
                        allowLeadingZero = false,
                        confirmLabel = if (payment.isSaving) "…" else "Encaisser",
                        confirmEnabled = canConfirm,
                        inputSessionKey = "${payment.mode}-${payment.activeField}",
                    )
                } else {
                    Button(
                        onClick = onConfirm,
                        enabled = canConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                    ) {
                        Text(
                            if (payment.isSaving) "Enregistrement…" else "Encaisser",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    enabled = !payment.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text("Annuler")
                }
            }
        }
    }
}

@Composable
private fun MixedFields(
    payment: PaymentFormState,
    enabled: Boolean,
    onSelectField: (PaymentField) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        MixedFieldChip(
            label = "Espèces",
            value = payment.cashAmount,
            selected = payment.activeField == PaymentField.CASH,
            enabled = enabled,
            onClick = { onSelectField(PaymentField.CASH) },
        )
        MixedFieldChip(
            label = "Orange Money",
            value = payment.mobileMoneyAmount,
            selected = payment.activeField == PaymentField.MOBILE_MONEY,
            enabled = enabled,
            onClick = { onSelectField(PaymentField.MOBILE_MONEY) },
        )
        MixedFieldChip(
            label = "Moov Money",
            value = payment.voucherAmount,
            selected = payment.activeField == PaymentField.VOUCHER,
            enabled = enabled,
            onClick = { onSelectField(PaymentField.VOUCHER) },
        )
        MixedFieldChip(
            label = "Dette",
            value = payment.debtAmount,
            selected = payment.activeField == PaymentField.DEBT,
            enabled = enabled,
            onClick = { onSelectField(PaymentField.DEBT) },
        )
        MixedFieldChip(
            label = "Espèces tendues (optionnel)",
            value = if (payment.tenderedExplicit) payment.amountTendered else "—",
            selected = payment.activeField == PaymentField.TENDERED,
            enabled = enabled,
            onClick = { onSelectField(PaymentField.TENDERED) },
        )
    }
}

@Composable
private fun MixedFieldChip(
    label: String,
    value: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            label = {
                val amount = value.toLongOrNull()
                Text(
                    if (amount != null) {
                        "$label : ${MoneyFormat.format(amount)}"
                    } else {
                        "$label : $value"
                    },
                )
            },
        )
    }
}

private fun fieldLabel(payment: PaymentFormState): String = when (payment.activeField) {
    PaymentField.TENDERED -> "Montant reçu"
    PaymentField.CASH -> "Part espèces"
    PaymentField.MOBILE_MONEY -> "Part Orange Money"
    PaymentField.VOUCHER -> "Part Moov Money"
    PaymentField.DEBT -> "Part dette"
}
