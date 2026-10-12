package com.maquis.caisse.ui.charts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatsPeriod.entries.forEach { period ->
            TextPill(
                text = period.label,
                tone = if (period == selected) PillTone.INFO else PillTone.NEUTRAL,
                modifier = Modifier.clickable { onSelect(period) },
            )
        }
    }
}
