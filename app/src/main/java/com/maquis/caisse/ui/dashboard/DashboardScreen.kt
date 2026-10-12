package com.maquis.caisse.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.CaisseDuJour
import com.maquis.caisse.ui.charts.CustomPeriodPickers
import com.maquis.caisse.ui.charts.PeriodSelector
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.theme.GestionBlue
import com.maquis.caisse.ui.theme.GestionCyan
import com.maquis.caisse.ui.theme.GestionDanger
import com.maquis.caisse.ui.theme.GestionSuccess
import com.maquis.caisse.ui.theme.GestionWarning

private val ChartPalette = listOf(
    GestionBlue,
    GestionCyan,
    GestionSuccess,
    GestionWarning,
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
)

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val stats = ui.stats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(
            title = "Tableau de bord",
            subtitle = "${ui.period.label} — CA, coûts et bénéfices",
            actionLabel = "Actualiser",
            onAction = viewModel::refresh,
        )
        PeriodSelector(selected = ui.period, onSelect = viewModel::onPeriod)
        CustomPeriodPickers(
            period = ui.period,
            customDayMs = ui.customDayMs,
            customFromMs = ui.customFromMs,
            customToMs = ui.customToMs,
            onCustomDay = viewModel::onCustomDay,
            onCustomRange = viewModel::onCustomRange,
        )

        val s = stats
        if (s == null) {
            Text("Chargement…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        CaisseDuJourCard(s.caisseDuJour)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KpiChip("Coût d'achat", MoneyFormat.format(s.costOfGoods), Modifier.weight(1f), GestionWarning)
            KpiChip(
                "Bénéfice",
                "${MoneyFormat.format(s.benefice)} (${s.marginPercent} %)",
                Modifier.weight(1f),
                GestionSuccess,
            )
        }

        GlassCard {
            Text("Top produits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            HorizontalBarChart(
                labels = s.topProducts.map { it.productName },
                values = s.topProducts.map { it.revenue.toFloat() },
                barColor = GestionBlue,
            )
        }

        GlassCard {
            Text("Top catégories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            HorizontalBarChart(
                labels = s.topCategories.map { it.categoryName },
                values = s.topCategories.map { it.revenue.toFloat() },
                barColor = GestionCyan,
            )
        }

        Text(
            "Chiffres du jour",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = GestionBlue,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiChip("Commandes", "${s.ordersToday}", Modifier.weight(1f), GestionBlue)
            KpiChip("En cours", "${s.openOrders}", Modifier.weight(1f), GestionWarning)
            KpiChip("CA", MoneyFormat.format(s.caGenerated), Modifier.weight(1.2f), GestionSuccess)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiChip("Encaissé", MoneyFormat.format(s.caCollected), Modifier.weight(1f), GestionCyan)
            KpiChip("À encaisser", MoneyFormat.format(s.toCollect), Modifier.weight(1f), GestionWarning)
            if (s.expensesTotal > 0L) {
                KpiChip("Dépenses", MoneyFormat.format(s.expensesTotal), Modifier.weight(1f), GestionWarning)
            }
        }
    }
}

@Composable
private fun CaisseDuJourCard(c: CaisseDuJour) {
    GlassCard {
        Text(
            "Caisse du jour",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GestionBlue,
        )
        Spacer(Modifier.height(4.dp))

        CaisseDetailLine("Espèces encaissées", MoneyFormat.format(c.cashToday), GestionSuccess)
        CaisseDetailLine("Mobile money", MoneyFormat.format(c.mobileToday), GestionCyan)
        CaisseDetailLine("Dettes créées", MoneyFormat.format(c.debtToday), GestionWarning)
        CaisseDetailLine("Avoirs émis", MoneyFormat.format(c.avoirToday), GestionWarning)

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        val netTotal = c.cashToday + c.mobileToday
        CaisseDetailLine(
            "Total net encaissé",
            MoneyFormat.format(netTotal),
            GestionBlue,
            bold = true,
        )

        if (c.fondDeCaisse != null && c.espècesThéoriques != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                "Session ouverte",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CaisseDetailLine("Fond de caisse", MoneyFormat.format(c.fondDeCaisse), GestionBlue)
            CaisseDetailLine(
                "Espèces théoriques",
                MoneyFormat.format(c.espècesThéoriques),
                GestionSuccess,
            )
            if (c.écart != null) {
                val écartColor = when {
                    c.écart > 0L -> GestionSuccess
                    c.écart < 0L -> GestionDanger
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val écartPrefix = if (c.écart > 0L) "+" else ""
                CaisseDetailLine(
                    "Écart caisse",
                    "$écartPrefix${MoneyFormat.format(c.écart)}",
                    écartColor,
                    bold = true,
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Espèces comptées",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "— non saisi",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaisseDetailLine(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
private fun KpiChip(label: String, value: String, modifier: Modifier, accent: Color) {
    Box(
        modifier = modifier
            .background(accent.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    color: Color = Color.Unspecified,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun HorizontalBarChart(
    labels: List<String>,
    values: List<Float>,
    barColor: Color,
) {
    if (labels.isEmpty()) {
        Text("Pas encore de données", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.zip(values).forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        MoneyFormat.format(value.toLong()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = barColor,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    drawRoundRect(
                        color = barColor.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(8f, 8f),
                        size = size,
                    )
                    drawRoundRect(
                        color = barColor,
                        cornerRadius = CornerRadius(8f, 8f),
                        size = Size(size.width * (value / max), size.height),
                    )
                }
            }
        }
    }
}
