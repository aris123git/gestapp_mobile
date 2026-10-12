package com.maquis.caisse.ui.mouvements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.MoneyFormat
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.AppUser
import com.maquis.caisse.domain.model.Expense
import com.maquis.caisse.domain.model.ExpenseCategories
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.StatsPeriod
import com.maquis.caisse.domain.model.StockMovement
import com.maquis.caisse.domain.model.StockMovementType
import com.maquis.caisse.domain.repository.ExpenseRepository
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.ProductRepository
import com.maquis.caisse.domain.repository.StockRepository
import com.maquis.caisse.domain.repository.UserRepository
import com.maquis.caisse.ui.charts.CustomPeriodPickers
import com.maquis.caisse.ui.charts.PeriodSelector
import com.maquis.caisse.ui.common.DateRanges
import com.maquis.caisse.ui.common.DropdownField
import com.maquis.caisse.ui.common.GlassCard
import com.maquis.caisse.ui.common.PageHeader
import com.maquis.caisse.ui.common.PillTone
import com.maquis.caisse.ui.common.TextPill
import com.maquis.caisse.ui.stock.MovementTimelineCard
import com.maquis.caisse.ui.stock.ProductStockSheet
import com.maquis.caisse.ui.theme.GestionSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MouvementsFilter(val label: String, val types: List<String>?) {
    TOUS("Tous", null),
    ENTREES("Entrées", listOf(StockMovementType.ENTREE)),
    VENTES("Ventes", listOf(StockMovementType.VENTE)),
    PERTES("Pertes", listOf(StockMovementType.PERTE)),
    SORTIES("Sorties", listOf(StockMovementType.SORTIE)),
    INVENTAIRES("Inventaires", listOf(StockMovementType.INVENTAIRE)),
    AUTRES(
        "Autres",
        listOf(
            StockMovementType.CORRECTION,
            StockMovementType.AJUSTEMENT_AUTORISE,
            StockMovementType.AVOIR,
        ),
    ),
}

enum class MouvementsViewMode(val label: String) {
    JOURNAL("Journal"),
    ENTREES_JOUR("Entrées du jour"),
}

data class DayKpis(
    val entrees: Int = 0,
    val ventes: Int = 0,
    val pertes: Int = 0,
    val sorties: Int = 0,
    val ecarts: Int = 0,
    val pertesValeur: Long = 0L,
)

data class MouvementsUiState(
    val isAdmin: Boolean = false,
    val selectedUserId: Long? = null,
    val period: StatsPeriod = StatsPeriod.TODAY,
    val customDayMs: Long = System.currentTimeMillis(),
    val customFromMs: Long = DateRanges.todayBounds().first,
    val customToMs: Long = DateRanges.todayBounds().second,
    val filter: MouvementsFilter = MouvementsFilter.TOUS,
    val viewMode: MouvementsViewMode = MouvementsViewMode.JOURNAL,
    val allMovements: List<StockMovement> = emptyList(),
    val movements: List<StockMovement> = emptyList(),
    val entreesGroups: List<EntreeProduitGroup> = emptyList(),
    val selected: StockMovement? = null,
    val productSheet: Product? = null,
    val productSheetMovements: List<StockMovement> = emptyList(),
    val kpis: DayKpis = DayKpis(),
    val ca: Long = 0L,
    val benefice: Long = 0L,
    val expensesTotal: Long = 0L,
    val expenses: List<Expense> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val showFilters: Boolean = false,
    val showAddExpense: Boolean = false,
    val expenseDescription: String = "",
    val expenseAmountText: String = "",
    val expenseCategory: String = ExpenseCategories.last(),
)

@HiltViewModel
class MouvementsViewModel @Inject constructor(
    private val stockRepository: StockRepository,
    private val expenseRepository: ExpenseRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    userRepository: UserRepository,
    sessionManager: SessionManager,
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

    private val products: StateFlow<List<Product>> = productRepository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun selectFilter(filter: MouvementsFilter) {
        _ui.update {
            it.copy(
                filter = filter,
                showFilters = false,
                viewMode = if (filter == MouvementsFilter.ENTREES) {
                    MouvementsViewMode.ENTREES_JOUR
                } else {
                    it.viewMode
                },
            )
        }
        refresh()
    }

    fun setViewMode(mode: MouvementsViewMode) {
        _ui.update {
            it.copy(
                viewMode = mode,
                filter = if (mode == MouvementsViewMode.ENTREES_JOUR) {
                    MouvementsFilter.ENTREES
                } else {
                    it.filter
                },
            )
        }
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
            it.copy(customFromMs = fromMs, customToMs = toMs, period = StatsPeriod.CUSTOM_RANGE)
        }
        refresh()
    }

    fun toggleFilters() {
        _ui.update { it.copy(showFilters = !it.showFilters) }
    }

    fun dismissFilters() {
        _ui.update { it.copy(showFilters = false) }
    }

    fun selectMovement(m: StockMovement?) {
        _ui.update { it.copy(selected = m) }
    }

    fun openProductSheet(productId: Long) = viewModelScope.launch {
        val product = productRepository.getProduct(productId) ?: return@launch
        val today = DateRanges.todayBounds()
        val movements = stockRepository.listMovements(
            fromMs = today.first,
            toMs = today.second,
            userId = null,
            types = null,
            limit = 300,
        ).filter { it.productId == productId }
        _ui.update { it.copy(productSheet = product, productSheetMovements = movements) }
    }

    fun closeProductSheet() {
        _ui.update { it.copy(productSheet = null, productSheetMovements = emptyList()) }
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
                val all = stockRepository.listMovements(from, to, userId, types = null, limit = 800)
                val filtered = state.filter.types?.let { types ->
                    all.filter { it.type in types }
                } ?: all

                val priceById = products.value.associate { it.id to it.purchasePrice }
                val pertesList = all.filter { it.type == StockMovementType.PERTE }
                val pertesValeur = pertesList.sumOf { m ->
                    (priceById[m.productId] ?: 0L) * m.quantity
                }
                val ecarts = all
                    .filter { it.type == StockMovementType.INVENTAIRE }
                    .sumOf { kotlin.math.abs(it.newStock - it.previousStock) }

                val kpis = DayKpis(
                    entrees = all.filter { it.type == StockMovementType.ENTREE }.sumOf { it.quantity },
                    ventes = all.filter { it.type == StockMovementType.VENTE }.sumOf { it.quantity },
                    pertes = pertesList.sumOf { it.quantity },
                    sorties = all.filter { it.type == StockMovementType.SORTIE }.sumOf { it.quantity },
                    ecarts = ecarts,
                    pertesValeur = pertesValeur,
                )
                val stats = orderRepository.cashierPeriodStats(from, to, userId)
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
                        allMovements = all,
                        movements = filtered.sortedByDescending { m -> m.createdAtEpochMs },
                        entreesGroups = groupEntreesByProduct(all),
                        kpis = kpis,
                        ca = stats.ca,
                        benefice = stats.benefice,
                        expensesTotal = expensesTotal,
                        expenses = expenses,
                        loading = false,
                        selected = it.selected?.let { sel ->
                            filtered.find { m -> m.id == sel.id }
                        },
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(loading = false, error = e.message ?: "Impossible de charger")
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
                expenseRepository.add(
                    description = description,
                    amount = amount,
                    category = state.expenseCategory.ifBlank { null },
                )
            }.onSuccess {
                _ui.update {
                    it.copy(showAddExpense = false, success = "Dépense enregistrée", error = null)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MouvementsScreen(viewModel: MouvementsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val dayFmt = remember { SimpleDateFormat("dd/MM", Locale.FRANCE) }
    val fullFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val message = ui.error ?: ui.success
    LaunchedEffect(message) {
        if (message != null) {
            delay(2_500)
            viewModel.consumeMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val masterDetail = maxWidth >= 900.dp
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageHeader(
                    title = "Mouvements",
                    subtitle = if (ui.isAdmin) "Journal tactile" else "Mes mouvements",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = viewModel::toggleFilters,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Filtrer") }
                    TextButton(onClick = viewModel::refresh) { Text("Actualiser") }
                }
            }

            message?.let {
                TextPill(it, if (ui.error != null) PillTone.DANGER else PillTone.SUCCESS)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MouvementsViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = ui.viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            if (ui.isAdmin) {
                KpiRow(ui.kpis, ui.filter, onSelect = viewModel::selectFilter)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("CA : ${MoneyFormat.format(ui.ca)}", fontWeight = FontWeight.Bold)
                    Text(
                        "Bénéfice : ${MoneyFormat.format(ui.benefice)}",
                        color = GestionSuccess,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(onClick = viewModel::openAddExpense) { Text("Dépense") }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (ui.viewMode) {
                    MouvementsViewMode.ENTREES_JOUR -> {
                        EntreesDuJourList(
                            groups = ui.entreesGroups,
                            onProductClick = viewModel::openProductSheet,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    MouvementsViewMode.JOURNAL -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(ui.movements, key = { it.id }) { m ->
                                MovementTimelineCard(
                                    m = m,
                                    timeFmt = timeFmt,
                                    dayFmt = dayFmt,
                                    onClick = { viewModel.selectMovement(m) },
                                )
                            }
                            if (ui.movements.isEmpty() && !ui.loading) {
                                item {
                                    Text(
                                        "Aucun mouvement.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (masterDetail && ui.viewMode == MouvementsViewMode.JOURNAL) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MovementDetailPane(
                            movement = ui.selected,
                            fullFmt = fullFmt,
                            onOpenProduct = { id -> viewModel.openProductSheet(id) },
                        )
                    }
                }
            }
        }

        if (!masterDetail && ui.selected != null && ui.viewMode == MouvementsViewMode.JOURNAL) {
            val selected = ui.selected!!
            AlertDialog(
                onDismissRequest = { viewModel.selectMovement(null) },
                title = { Text(StockMovementType.label(selected.type)) },
                text = {
                    MovementDetailPane(
                        movement = selected,
                        fullFmt = fullFmt,
                        onOpenProduct = { id ->
                            viewModel.selectMovement(null)
                            viewModel.openProductSheet(id)
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.selectMovement(null) }) { Text("Fermer") }
                },
            )
        }
    }

    if (ui.showFilters) {
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissFilters,
            sheetState = sheetState,
        ) {
            FilterPanel(
                ui = ui,
                users = users,
                onUser = viewModel::setSelectedUser,
                onPeriod = viewModel::onPeriod,
                onCustomDay = viewModel::onCustomDay,
                onCustomRange = viewModel::onCustomRange,
                onFilter = viewModel::selectFilter,
            )
        }
    }

    ui.productSheet?.let { product ->
        ProductStockSheet(
            product = product,
            movements = ui.productSheetMovements,
            onDismiss = viewModel::closeProductSheet,
        )
    }

    if (ui.showAddExpense) {
        AlertDialog(
            onDismissRequest = viewModel::closeAddExpense,
            title = { Text("Nouvelle dépense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.expenseDescription,
                        onValueChange = viewModel::setExpenseDescription,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = ui.expenseAmountText,
                        onValueChange = viewModel::setExpenseAmountText,
                        label = { Text("Montant (FCFA)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    DropdownField(
                        label = "Catégorie",
                        selected = ui.expenseCategory,
                        options = ExpenseCategories,
                        optionLabel = { it },
                        onSelect = { if (it != null) viewModel.setExpenseCategory(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::saveExpense) { Text("Enregistrer") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeAddExpense) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun KpiRow(
    kpis: DayKpis,
    selected: MouvementsFilter,
    onSelect: (MouvementsFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiChip("Entrées", "+${kpis.entrees}", selected == MouvementsFilter.ENTREES) {
                onSelect(MouvementsFilter.ENTREES)
            }
            KpiChip("Ventes", "−${kpis.ventes}", selected == MouvementsFilter.VENTES) {
                onSelect(MouvementsFilter.VENTES)
            }
            KpiChip("Pertes", "−${kpis.pertes}", selected == MouvementsFilter.PERTES) {
                onSelect(MouvementsFilter.PERTES)
            }
            KpiChip("Sorties", "−${kpis.sorties}", selected == MouvementsFilter.SORTIES) {
                onSelect(MouvementsFilter.SORTIES)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpiChip("Écarts", "−${kpis.ecarts}", selected == MouvementsFilter.INVENTAIRES) {
                onSelect(MouvementsFilter.INVENTAIRES)
            }
            KpiChip(
                "Valeur pertes",
                MoneyFormat.format(kpis.pertesValeur),
                selected == MouvementsFilter.PERTES,
            ) {
                onSelect(MouvementsFilter.PERTES)
            }
        }
    }
}

@Composable
private fun KpiChip(label: String, value: String, selected: Boolean, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.heightIn(min = 64.dp), onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (selected) TextPill("Filtré", PillTone.INFO)
    }
}

@Composable
private fun FilterPanel(
    ui: MouvementsUiState,
    users: List<AppUser>,
    onUser: (Long?) -> Unit,
    onPeriod: (StatsPeriod) -> Unit,
    onCustomDay: (Long) -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    onFilter: (MouvementsFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Filtres", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (ui.isAdmin) {
            DropdownField(
                label = "Caissier",
                selected = users.firstOrNull { it.id == ui.selectedUserId },
                options = users,
                optionLabel = { it.name },
                onSelect = { onUser(it?.id) },
                allowNull = true,
                nullLabel = "Tous",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PeriodSelector(selected = ui.period, onSelect = onPeriod)
        CustomPeriodPickers(
            period = ui.period,
            customDayMs = ui.customDayMs,
            customFromMs = ui.customFromMs,
            customToMs = ui.customToMs,
            onCustomDay = onCustomDay,
            onCustomRange = onCustomRange,
        )
        Text("Type de mouvement", fontWeight = FontWeight.SemiBold)
        MouvementsFilter.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { f ->
                    FilterChip(
                        selected = ui.filter == f,
                        onClick = { onFilter(f) },
                        label = { Text(f.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementDetailPane(
    movement: StockMovement?,
    fullFmt: SimpleDateFormat,
    onOpenProduct: (Long) -> Unit,
) {
    if (movement == null) {
        Text(
            "Sélectionne un mouvement",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    GlassCard {
        Text(
            "${StockMovementType.label(movement.type)} #${movement.id}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        DetailLine("Produit", movement.productName)
        TextButton(onClick = { onOpenProduct(movement.productId) }) {
            Text("Voir la fiche produit")
        }
        DetailLine("Quantité", StockMovementType.signedQuantity(movement.type, movement.quantity, movement.previousStock, movement.newStock))
        DetailLine("Stock avant", movement.previousStock.toString())
        DetailLine("Stock après", movement.newStock.toString())
        DetailLine("Type", StockMovementType.label(movement.type))
        DetailLine("Caissier", movement.userName ?: "—")
        DetailLine("Date / heure", fullFmt.format(Date(movement.createdAtEpochMs)))
        DetailLine("Motif", movement.motif.ifBlank { "—" })
        if (!movement.supplier.isNullOrBlank()) {
            DetailLine("Référence / fournisseur", movement.supplier.orEmpty())
        }
        if (!movement.comment.isNullOrBlank()) {
            DetailLine("Commentaire", movement.comment.orEmpty())
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
