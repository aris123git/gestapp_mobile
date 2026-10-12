package com.maquis.caisse.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.maquis.caisse.core.Constants
import com.maquis.caisse.ui.theme.GestionBlue

/**
 * Pavé numérique générique (quantité, montant reçu…).
 *
 * Dès l'ouverture, la première frappe **remplace** la valeur par défaut
 * (ex. "1" → "3", "5000" → "1") au lieu de concaténer.
 */
@Composable
fun NumericKeypad(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    maxDigits: Int = Constants.MAX_QUANTITY_DIGITS,
    confirmLabel: String = "OK",
    confirmEnabled: Boolean = true,
    allowLeadingZero: Boolean = false,
    onDeleteLine: (() -> Unit)? = null,
    deleteLabel: String = "Supprimer",
    /** Change cette clé pour réarmer le mode « remplacer » (nouveau produit / champ). */
    inputSessionKey: Any? = Unit,
    /** Version réduite (touches plus basses, défilement) : utilisée par la fenêtre de quantité. */
    compact: Boolean = false,
) {
    var replaceNext by remember(inputSessionKey) { mutableStateOf(true) }

    LaunchedEffect(inputSessionKey) {
        replaceNext = true
    }

    val keyHeight = if (compact) 44.dp else 56.dp
    val confirmHeight = if (compact) 48.dp else 56.dp
    val gap = if (compact) 4.dp else 8.dp
    val pad = if (compact) 8.dp else 16.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(pad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GestionBlue.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 6.dp else 12.dp),
        ) {
            Text(
                text = value.ifEmpty { "0" },
                style = MaterialTheme.typography.headlineMedium,
                color = GestionBlue,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (compact) 8.dp else 14.dp),
            )
        }

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫"),
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "C" -> {
                                    onValueChange("")
                                    replaceNext = true
                                }
                                "⌫" -> {
                                    if (replaceNext) {
                                        onValueChange("")
                                        replaceNext = false
                                    } else {
                                        onValueChange(value.dropLast(1))
                                    }
                                }
                                else -> {
                                    val next = if (replaceNext) {
                                        replaceNext = false
                                        startDigit(digit = key, allowLeadingZero = allowLeadingZero)
                                    } else {
                                        appendDigit(
                                            current = value,
                                            digit = key,
                                            maxDigits = maxDigits,
                                            allowLeadingZero = allowLeadingZero,
                                        )
                                    }
                                    onValueChange(next)
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight),
                    ) {
                        if (key == "⌫") {
                            Icon(
                                Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "Effacer",
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(gap))
        }

        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(confirmHeight),
        ) {
            Text(confirmLabel, style = MaterialTheme.typography.titleLarge)
        }

        if (onDeleteLine != null) {
            TextButton(
                onClick = onDeleteLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = deleteLabel,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

internal fun startDigit(digit: String, allowLeadingZero: Boolean): String {
    if (!allowLeadingZero && digit == "0") return "0"
    return digit
}

internal fun appendDigit(
    current: String,
    digit: String,
    maxDigits: Int,
    allowLeadingZero: Boolean,
): String {
    if (current.length >= maxDigits) return current
    if (!allowLeadingZero && current.isEmpty() && digit == "0") return "0"
    if (current == "0") return digit
    return current + digit
}
