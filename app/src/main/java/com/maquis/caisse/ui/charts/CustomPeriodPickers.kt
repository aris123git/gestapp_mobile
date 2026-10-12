package com.maquis.caisse.ui.charts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Boutons jour / intervalle quand la période est personnalisée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPeriodPickers(
    period: StatsPeriod,
    customDayMs: Long,
    customFromMs: Long,
    customToMs: Long,
    onCustomDay: (Long) -> Unit,
    onCustomRange: (fromMs: Long, toMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (period != StatsPeriod.CUSTOM_DAY && period != StatsPeriod.CUSTOM_RANGE) return

    val fmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE) }
    var picking by remember { mutableStateOf<PickTarget?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (period) {
            StatsPeriod.CUSTOM_DAY -> {
                TextPill(
                    text = "Jour : ${fmt.format(Date(customDayMs))}",
                    tone = PillTone.INFO,
                    modifier = Modifier.clickable { picking = PickTarget.DAY },
                )
            }
            StatsPeriod.CUSTOM_RANGE -> {
                TextPill(
                    text = "Du ${fmt.format(Date(customFromMs))}",
                    tone = PillTone.INFO,
                    modifier = Modifier.clickable { picking = PickTarget.FROM },
                )
                TextPill(
                    text = "Au ${fmt.format(Date(customToMs))}",
                    tone = PillTone.INFO,
                    modifier = Modifier.clickable { picking = PickTarget.TO },
                )
            }
            else -> Unit
        }
    }

    val target = picking
    if (target != null) {
        val initial = when (target) {
            PickTarget.DAY -> customDayMs
            PickTarget.FROM -> customFromMs
            PickTarget.TO -> customToMs
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) {
                            when (target) {
                                PickTarget.DAY -> onCustomDay(millis)
                                PickTarget.FROM -> onCustomRange(millis, customToMs)
                                PickTarget.TO -> onCustomRange(customFromMs, millis)
                            }
                        }
                        picking = null
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text("Annuler") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

private enum class PickTarget { DAY, FROM, TO }
