package com.maquis.caisse.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.domain.model.DashboardStats
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.ui.common.DateRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val period: StatsPeriod = StatsPeriod.TODAY,
    val customDayMs: Long = System.currentTimeMillis(),
    val customFromMs: Long = DateRanges.todayBounds().first,
    val customToMs: Long = DateRanges.todayBounds().second,
    val stats: DashboardStats? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(DashboardUiState())
    val ui: StateFlow<DashboardUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun onPeriod(period: StatsPeriod) {
        _ui.update { it.copy(period = period) }
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

    fun refresh() {
        viewModelScope.launch {
            val state = _ui.value
            val (from, to) = DateRanges.boundsFor(
                state.period,
                customDayMs = state.customDayMs,
                customFromMs = state.customFromMs,
                customToMs = state.customToMs,
            )
            _ui.update { it.copy(stats = orderRepository.dashboard(from, to)) }
        }
    }
}
