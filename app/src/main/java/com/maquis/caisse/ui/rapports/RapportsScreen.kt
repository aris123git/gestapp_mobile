package com.maquis.caisse.ui.rapports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.model.CategorySalesRow
import com.maquis.caisse.domain.model.DashboardStats
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.ProductSalesRow
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.ui.charts.CustomPeriodPickers
import com.maquis.caisse.ui.charts.PeriodSelector
import com.maquis.caisse.ui.common.DateRanges
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.theme.GestionSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RapportsUiState(
    val period: StatsPeriod = StatsPeriod.TODAY,
    val customDayMs: Long = System.currentTimeMillis(),
    val customFromMs: Long = DateRanges.todayBounds().first,
    val customToMs: Long = DateRanges.todayBounds().second,
    val category: String? = null,
    val status: OrderStatus? = null,
    val paymentMode: PaymentMode? = null,
    val dashboard: DashboardStats? = null,
    val categories: List<CategorySalesRow> = emptyList(),
    val products: List<ProductSalesRow> = emptyList(),
)

@HiltViewModel
class RapportsViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(RapportsUiState())
    val ui: StateFlow<RapportsUiState> = _ui.asStateFlow()

    val categoryOptions: StateFlow<List<Category>> = categoryRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun onPeriod(p: StatsPeriod) {
        _ui.update { it.copy(period = p) }
        refresh()
    }

    fun onCustomDay(ms: Long) {
        _ui.update { it.copy(customDayMs = ms, period = StatsPeriod.CUSTOM_DAY) }
        refresh()
    }

    fun onCustomRange(fromMs: Long, toMs: Long) {
        _ui.update {
            it.copy(
                customFromMs = fromMs,
                customToMs = toMs,
                period = StatsPeriod.CUSTOM_RANGE,
            )
        }
        refresh()
    }

    fun onCategory(name: String?) {
        _ui.update { it.copy(category = name) }
        refresh()
    }

    fun onStatus(s: OrderStatus?) {
        _ui.update { it.copy(status = s) }
        refresh()
    }

    fun onPayment(m: PaymentMode?) {
        _ui.update { it.copy(paymentMode = m) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val state = _ui.value
            val (from, to) = DateRanges.boundsFor(
                state.period,
                customDayMs = state.customDayMs,
                customFromMs = state.customFromMs,
                customToMs = state.customToMs,
            )
            val dash = orderRepository.dashboard(from, to)
            var cats = orderRepository.categorySales(from, to)
            var products = orderRepository.productSales(from, to)
            state.category?.let { c ->
                cats = cats.filter { it.categoryName.equals(c, true) }
                products = products.filter { it.categoryName.equals(c, true) }
            }
            _ui.update {
                it.copy(
                    dashboard = dash,
                    categories = cats,
                    products = products.take(50),
                )
            }
        }
    }
}

@Composable
fun RapportsScreen(viewModel: RapportsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val categories by viewModel.categoryOptions.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PageHeader(title = "Rapports", subtitle = "CA, coûts, bénéfices et période")
            TextButton(onClick = viewModel::refresh) { Text("Actualiser") }
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DropdownField(
                label = "Catégorie",
                selected = ui.category,
                options = categories.map { it.name },
                optionLabel = { it },
                onSelect = viewModel::onCategory,
                allowNull = true,
                nullLabel = "Toutes",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DropdownField(
                label = "Statut",
                selected = ui.status,
                options = OrderStatus.entries,
                optionLabel = { it.label },
                onSelect = viewModel::onStatus,
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.weight(1f),
            )
            DropdownField(
                label = "Paiement",
                selected = ui.paymentMode,
                options = PaymentMode.PAYMENT_CHOICES,
                optionLabel = { it.label },
                onSelect = viewModel::onPayment,
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.weight(1f),
            )
        }

        val d = ui.dashboard
        if (d != null) {
            Text("CA (payé − dépenses) : ${MoneyFormat.format(d.caGenerated)}", fontWeight = FontWeight.Bold)
            Text("Encaissé (après dépenses) : ${MoneyFormat.format(d.caCollected)}", fontWeight = FontWeight.Bold)
            Text("Reste à encaisser : ${MoneyFormat.format(d.toCollect)}", fontWeight = FontWeight.Bold)
            Text(
                "Coût d'achat : ${MoneyFormat.format(d.costOfGoods)}",
                fontWeight = FontWeight.Bold,
            )
            if (d.expensesTotal > 0L) {
                Text("Dépenses : ${MoneyFormat.format(d.expensesTotal)}", fontWeight = FontWeight.Bold)
            }
            Text(
                "Bénéfice : ${MoneyFormat.format(d.benefice)} (${d.marginPercent} %)",
                fontWeight = FontWeight.Bold,
                color = GestionSuccess,
            )
            Text("Nombre de commandes : ${d.ordersToday}")
        }

        Text("Catégories vendues", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        ui.categories.forEach {
            Text(
                "${it.categoryName} · ${it.quantity} · CA ${MoneyFormat.format(it.revenue)} · " +
                    "bénéfice ${MoneyFormat.format(it.benefice)}",
            )
        }

        Text("Produits vendus", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        ui.products.forEach {
            Text(
                "${it.productName} (${it.categoryName}) · ${it.quantity} · " +
                    "CA ${MoneyFormat.format(it.revenue)} · bénéfice ${MoneyFormat.format(it.benefice)}",
            )
        }
    }
}
