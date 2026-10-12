package com.maquis.caisse.ui.suivi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.CaisseSession
import com.maquis.caisse.domain.model.CashierPeriodStats
import com.maquis.caisse.domain.model.Expense
import com.maquis.caisse.domain.model.ExpenseCategories
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.repository.CaisseSessionRepository
import com.maquis.caisse.domain.repository.ExpenseRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.common.DateRanges
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MouvementsTab(val label: String, val movementType: String) {
    SORTIE("Sortie", "VENTE"),
    ENTREE("Entrée", "ENTREE"),
}

data class MovementGroup(
    val title: String,
    val subtitle: String? = null,
    val movements: List<StockMovement>,
)

data class MouvementsUiState(
    val isAdmin: Boolean = false,
    /** null = tous les caissiers (admin seulement). */
    val selectedUserId: Long? = null,
    val period: StatsPeriod = StatsPeriod.TODAY,
    val customDayMs: Long = System.currentTimeMillis(),
    val customFromMs: Long = DateRanges.todayBounds().first,
    val customToMs: Long = DateRanges.todayBounds().second,
    val tab: MouvementsTab = MouvementsTab.SORTIE,
    /** Affichage plat (un jour / une session) vs regroupé. */
    val flatMode: Boolean = true,
    val flatMovements: List<StockMovement> = emptyList(),
    val groups: List<MovementGroup> = emptyList(),
    val ca: Long = 0L,
    val benefice: Long = 0L,
    val expensesTotal: Long = 0L,
    val expenses: List<Expense> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val showAddExpense: Boolean = false,
    val expenseDescription: String = "",
    val expenseAmountText: String = "",
    val expenseCategory: String = ExpenseCategories.last(),
) {
    /** CA et bénéfice déjà nets de dépenses (soustraction faite côté repo). */
    val beneficeNet: Long get() = benefice
    val caNet: Long get() = ca
}

@HiltViewModel
class SuiviAdminViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val expenseRepository: ExpenseRepository,
    private val orderRepository: OrderRepository,
    private val sessionRepository: CaisseSessionRepository,
    userRepository: UserRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val currentUser = sessionManager.userOrNull()
    private val isAdmin = currentUser?.role == "ADMIN"

    private val _ui = MutableStateFlow(
        MouvementsUiState(
            isAdmin = isAdmin,
            selectedUserId = if (isAdmin) null else currentUser?.id,
        ),
    )
    val ui: StateFlow<MouvementsUiState> = _ui.asStateFlow()

    val users: StateFlow<List<AppUser>> = userRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun selectTab(tab: MouvementsTab) {
        _ui.update { it.copy(tab = tab, error = null, success = null) }
        refresh()
    }

    fun setSelectedUser(userId: Long?) {
        if (!isAdmin) return
        _ui.update { it.copy(selectedUserId = userId) }
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
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                val state = _ui.value
                val (from, to) = DateRanges.boundsFor(
                    state.period,
                    customDayMs = state.customDayMs,
                    customFromMs = state.customFromMs,
                    customToMs = state.customToMs,
                )
                val userId = state.selectedUserId
                val type = state.tab.movementType
                val movements = stockRepository.listMovementsByType(
                    type = type,
                    fromMs = from,
                    toMs = to,
                    userId = userId,
                )
                val sessions = sessionRepository.listOpenedBetween(from, to, userId)
                val singleDay = isSingleCalendarDay(from, to)
                // Entrée : affichage plat (évite les collisions de sessions ouvertes).
                val flatMode = singleDay || sessions.size <= 1 || type == "ENTREE"

                val flat: List<StockMovement>
                val groups: List<MovementGroup>
                if (flatMode) {
                    flat = movements.sortedBy { it.createdAtEpochMs }
                    groups = emptyList()
                } else {
                    flat = emptyList()
                    groups = buildGroups(movements, sessions, type)
                }

                val stats: CashierPeriodStats = orderRepository.cashierPeriodStats(from, to, userId)
                val expensesTotal = if (userId != null) {
                    expenseRepository.totalByUserAndDateRange(userId, from, to)
                } else {
                    expenseRepository.totalBetween(from, to)
                }
                val expenses = if (userId != null) {
                    expenseRepository.listByUserAndDateRange(userId, from, to)
                } else {
                    expenseRepository.listBetween(from, to)
                }

                _ui.update {
                    it.copy(
                        flatMode = flatMode,
                        flatMovements = flat,
                        groups = groups,
                        ca = stats.ca,
                        benefice = stats.benefice,
                        expensesTotal = expensesTotal,
                        expenses = expenses,
                        loading = false,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Impossible de charger les mouvements",
                    )
                }
            }
        }
    }

    fun openAddExpense() {
        _ui.update {
            it.copy(
                showAddExpense = true,
                expenseDescription = "",
                expenseAmountText = "",
                expenseCategory = ExpenseCategories.last(),
                error = null,
                success = null,
            )
        }
    }

    fun closeAddExpense() {
        _ui.update { it.copy(showAddExpense = false) }
    }

    fun setExpenseDescription(value: String) {
        _ui.update { it.copy(expenseDescription = value) }
    }

    fun setExpenseAmountText(value: String) {
        _ui.update { it.copy(expenseAmountText = value.filter { ch -> ch.isDigit() }) }
    }

    fun setExpenseCategory(value: String) {
        _ui.update { it.copy(expenseCategory = value) }
    }

    fun saveExpense() {
        val state = _ui.value
        val description = state.expenseDescription.trim()
        if (description.isBlank()) {
            _ui.update { it.copy(error = "Saisissez une description") }
            return
        }
        val amount = state.expenseAmountText.toLongOrNull()
        if (amount == null || amount <= 0L) {
            _ui.update { it.copy(error = "Montant invalide") }
            return
        }
        viewModelScope.launch {
            runCatching {
                // Toujours l'utilisateur connecté (jamais le caissier consulté).
                expenseRepository.add(
                    description = description,
                    amount = amount,
                    category = state.expenseCategory.ifBlank { null },
                )
            }.onSuccess {
                _ui.update {
                    it.copy(
                        showAddExpense = false,
                        success = "Dépense enregistrée",
                        error = null,
                    )
                }
                refresh()
            }.onFailure { e ->
                _ui.update { it.copy(error = e.message ?: "Échec enregistrement dépense") }
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(error = null, success = null) }
    }

    private fun isSingleCalendarDay(fromMs: Long, toMs: Long): Boolean {
        // ALL / bornes extrêmes : ne jamais traiter comme un seul jour.
        if (toMs <= fromMs) return true
        if (toMs >= Long.MAX_VALUE / 2 || fromMs <= 0L) {
            val spanDays = (toMs - fromMs) / (24L * 60L * 60L * 1000L)
            return spanDays < 1L
        }
        val a = Calendar.getInstance().apply { timeInMillis = fromMs }
        val b = Calendar.getInstance().apply { timeInMillis = toMs }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun buildGroups(
        movements: List<StockMovement>,
        sessions: List<CaisseSession>,
        type: String,
    ): List<MovementGroup> {
        val now = System.currentTimeMillis()
        val assigned = mutableSetOf<Long>()
        val groups = mutableListOf<MovementGroup>()
        val dayFmt = java.text.SimpleDateFormat("EEEE d MMMM yyyy", java.util.Locale.FRANCE)
        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)

        // Sessions fermées d'abord (fenêtre plus précise), puis ouvertes.
        val orderedSessions = sessions.sortedWith(
            compareBy<CaisseSession> { if (it.closedAt == null) 1 else 0 }
                .thenBy { it.openedAt },
        )

        for (session in orderedSessions) {
            val end = session.closedAt ?: now
            val matched = movements.filter { m ->
                m.id !in assigned &&
                    m.createdAtEpochMs in session.openedAt..end &&
                    (
                        type != "VENTE" ||
                            m.userId == null ||
                            m.userId == session.userId
                        )
            }
            if (matched.isEmpty()) continue
            matched.forEach { assigned.add(it.id) }
            val dateLabel = dayFmt.format(java.util.Date(session.openedAt))
            val hours = buildString {
                append(timeFmt.format(java.util.Date(session.openedAt)))
                append(" – ")
                if (session.closedAt != null) {
                    append(timeFmt.format(java.util.Date(session.closedAt)))
                } else {
                    append("en cours")
                }
            }
            groups += MovementGroup(
                title = "Session ${session.userName} · $dateLabel",
                subtitle = hours,
                movements = matched.sortedBy { it.createdAtEpochMs },
            )
        }

        val orphans = movements.filter { it.id !in assigned }
        if (orphans.isNotEmpty()) {
            val byDay = orphans.groupBy { dayKey(it.createdAtEpochMs) }
                .toSortedMap()
            byDay.forEach { (_, dayMovements) ->
                val first = dayMovements.minByOrNull { it.createdAtEpochMs } ?: return@forEach
                val label = dayFmt.format(java.util.Date(first.createdAtEpochMs))
                val title = if (type == "ENTREE") {
                    "Réappro · $label"
                } else {
                    "Hors session · $label"
                }
                groups += MovementGroup(
                    title = title,
                    movements = dayMovements.sortedBy { it.createdAtEpochMs },
                )
            }
        }
        return groups
    }

    private fun dayKey(epochMs: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = epochMs }
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }
}
