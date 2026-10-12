package com.maquis.caisse.ui.commandes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.OrderStatus
import com.maquis.caisse.domain.repository.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CommandesUiState(
    val query: String = "",
)

@HiltViewModel
class CommandesViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(CommandesUiState())
    val ui: StateFlow<CommandesUiState> = _ui.asStateFlow()

    val openOrders: StateFlow<List<Order>> = orderRepository.observeOpenOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQuery(q: String) = _ui.update { it.copy(query = q) }

    fun filtered(orders: List<Order>): List<Order> {
        val q = _ui.value.query.trim()
        if (q.isEmpty()) return orders
        return orders.filter {
            it.publicId.contains(q, true)
        }
    }
}
