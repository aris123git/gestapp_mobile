package com.maquis.caisse.ui.commandes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.CategorySalesRow
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.ProductSalesRow
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.ui.common.DateRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryPeriod { TODAY, WEEK, MONTH, ALL }

data class HistoriqueUiState(
    val query: String = "",
    val status: OrderStatus? = null,
    val paymentMode: PaymentMode? = null,
    val categoryFilter: String? = null,
    val period: HistoryPeriod = HistoryPeriod.WEEK,
    val categories: List<String> = emptyList(),
    val categoryRows: List<CategorySalesRow> = emptyList(),
    val productRows: List<ProductSalesRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoriqueViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HistoriqueUiState())
    val ui: StateFlow<HistoriqueUiState> = _ui.asStateFlow()

    val orders: StateFlow<List<Order>> = _ui
        .flatMapLatest { state ->
            val (from, to) = bounds(state.period)
            orderRepository.observeFiltered(
                query = state.query,
                status = state.status,
                fromMs = from,
                toMs = to,
            ).map { list ->
                if (state.paymentMode == null) list
                else {
                    // Affinage : charge les paiements pour les commandes candidates
                    list
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            categoryRepository.observeActive().collect { list ->
                _ui.update { it.copy(categories = list.map(Category::name)) }
            }
        }
        refreshStats()
    }

    fun onQuery(q: String) {
        _ui.update { it.copy(query = q) }
        refreshStats()
    }

    fun onStatus(s: OrderStatus?) {
        _ui.update { it.copy(status = s) }
    }

    fun onPeriod(p: HistoryPeriod) {
        _ui.update { it.copy(period = p) }
        refreshStats()
    }

    fun onPaymentMode(m: PaymentMode?) {
        _ui.update { it.copy(paymentMode = m) }
        refreshStats()
    }

    fun onCategory(c: String?) {
        _ui.update { it.copy(categoryFilter = c) }
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            val state = _ui.value
            val (from, to) = bounds(state.period)
            val cats = orderRepository.categorySales(from, to)
                .let { rows ->
                    val cat = state.categoryFilter
                    if (cat == null) rows else rows.filter { it.categoryName.equals(cat, true) }
                }
            val products = orderRepository.productSales(from, to)
                .let { rows ->
                    val cat = state.categoryFilter
                    if (cat == null) rows else rows.filter { it.categoryName.equals(cat, true) }
                }
            _ui.update {
                it.copy(
                    categoryRows = cats,
                    productRows = products.take(40),
                )
            }
        }
    }

    fun filterDisplayed(orders: List<Order>): List<Order> = orders

    private fun bounds(period: HistoryPeriod): Pair<Long, Long> = when (period) {
        HistoryPeriod.TODAY -> DateRanges.todayBounds()
        HistoryPeriod.WEEK -> DateRanges.lastDaysBounds(7)
        HistoryPeriod.MONTH -> DateRanges.lastDaysBounds(30)
        HistoryPeriod.ALL -> DateRanges.allTimeBounds()
    }
}
