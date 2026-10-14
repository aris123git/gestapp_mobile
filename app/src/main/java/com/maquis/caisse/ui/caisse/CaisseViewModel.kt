package com.maquis.caisse.ui.caisse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.common.CartJson
import com.maquis.caisse.core.SettingsKeys
import com.maquis.caisse.data.print.EscPosPrinter
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.cart.CartOperations
import com.maquis.caisse.domain.cart.ProfitCalculator
import com.maquis.caisse.domain.cart.ProfitSummary
import com.maquis.caisse.domain.model.CartLine
import com.maquis.caisse.domain.model.CreateOrderRequest
import com.maquis.caisse.domain.model.Order
import com.maquis.caisse.domain.model.PaymentInput
import com.maquis.caisse.domain.model.PaymentMode
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.model.Sale
import com.maquis.caisse.domain.payment.PaymentCalculator
import com.maquis.caisse.domain.repository.OrderRepository
import com.maquis.caisse.domain.repository.SettingsRepository
import com.maquis.caisse.domain.usecase.ObserveActiveProductsUseCase
import com.maquis.caisse.domain.usecase.ResolveProductImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Vente au détail en cours de saisie (produit avec contenance + prix de détail). */
data class DetailSaleState(
    val product: Product,
    val initialAmount: Long?,
    val isEditing: Boolean,
)

/** Overlay pavé numérique : ajout ou édition d'une ligne panier. */
data class QuantityOverlay(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val imagePath: String?,
    val quantityInput: String,
    val isEditing: Boolean,
    /** true = on saisit un MONTANT (F) ; la quantité est calculée (montant / prix). */
    val amountMode: Boolean = false,
)

data class PaymentFormState(
    val mode: PaymentMode = PaymentMode.CASH,
    val amountTendered: String = "",
    val cashAmount: String = "",
    val mobileMoneyAmount: String = "",
    val voucherAmount: String = "",
    val debtAmount: String = "",
    /** true si le commerçant a touché le champ « espèces tendues » en mixte. */
    val tenderedExplicit: Boolean = false,
    val activeField: PaymentField = PaymentField.TENDERED,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val activeInput: String
        get() = when (activeField) {
            PaymentField.TENDERED -> amountTendered
            PaymentField.CASH -> cashAmount
            PaymentField.MOBILE_MONEY -> mobileMoneyAmount
            PaymentField.VOUCHER -> voucherAmount
            PaymentField.DEBT -> debtAmount
        }

    fun toPaymentInput(): PaymentInput = PaymentInput(
        mode = mode,
        amountTendered = when {
            mode == PaymentMode.CASH -> amountTendered.toLongOrNull()
            mode == PaymentMode.MIXED && tenderedExplicit -> amountTendered.toLongOrNull()
            else -> null
        },
        cashAmount = cashAmount.toLongOrNull() ?: 0L,
        mobileMoneyAmount = mobileMoneyAmount.toLongOrNull() ?: 0L,
        voucherAmount = voucherAmount.toLongOrNull() ?: 0L,
        debtAmount = debtAmount.toLongOrNull() ?: 0L,
    )
}

enum class PaymentField {
    TENDERED,
    CASH,
    MOBILE_MONEY,
    VOUCHER,
    DEBT,
}

data class SavedOrderPrompt(
    val order: Order,
    val printEnabled: Boolean,
    val printMessage: String? = null,
)

data class CaisseUiState(
    val products: List<Product> = emptyList(),
    val searchQuery: String = "",
    val cart: List<CartLine> = emptyList(),
    val quantityOverlay: QuantityOverlay? = null,
    val showPayment: Boolean = false,
    val payment: PaymentFormState = PaymentFormState(),
    val completedSale: Sale? = null,
    val completedOrder: Order? = null,
    /** Après « Enregistrer commande » : proposer / confirmer l'impression. */
    val savedOrderPrompt: SavedOrderPrompt? = null,
    val snackbarMessage: String? = null,
    /** Bénéfice du panier visible uniquement pour l'administrateur. */
    val showProfit: Boolean = false,
    /** Imprimante activée dans Paramètres (sinon le choix d'impression est grisé). */
    val printAvailable: Boolean = false,
    /** Choix du vendeur dans la fenêtre de paiement : imprimer le ticket à l'encaissement. */
    val printOnPayment: Boolean = true,
    /** Option Paramètres : vente libre (par montant) disponible. */
    val freeEntryEnabled: Boolean = true,
    /** Mode vente libre actif : toucher un produit = saisir un montant. */
    val amountMode: Boolean = false,
    /** Fenêtre « montant ↔ quantité » (vente au détail), null = fermée. */
    val detailSale: DetailSaleState? = null,
) {
    val cartTotal: Long get() = CartOperations.total(cart)

    /** Bénéfice estimé du panier (prix de vente − prix d'achat des produits). */
    val profit: ProfitSummary
        get() = ProfitCalculator.summarize(cart, products.associateBy { it.id })

    val filteredProducts: List<Product>
        get() {
            val q = searchQuery.trim()
            if (q.isEmpty()) return products
            return products.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.category.contains(q, ignoreCase = true)
            }
        }
}

@HiltViewModel
class CaisseViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeActiveProducts: ObserveActiveProductsUseCase,
    private val resolveImage: ResolveProductImageUseCase,
    private val orderRepository: OrderRepository,
    private val session: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val printer: EscPosPrinter,
) : ViewModel() {

    private val saleInFlight = AtomicBoolean(false)

    private val productsFlow = observeActiveProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(
        CaisseUiState(cart = CartJson.decode(savedStateHandle[KEY_CART])),
    )
    val uiState: StateFlow<CaisseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            productsFlow.collect { products ->
                _uiState.update { it.copy(products = products) }
            }
        }
        viewModelScope.launch {
            session.currentUser.collect { user ->
                _uiState.update { it.copy(showProfit = user?.role == "ADMIN") }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsKeys.PRINT_ENABLED).collect { value ->
                _uiState.update { it.copy(printAvailable = value == "true") }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsKeys.PRINT_ON_PAYMENT).collect { value ->
                _uiState.update { it.copy(printOnPayment = value != "false") }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(SettingsKeys.FREE_ENTRY_ENABLED).collect { value ->
                _uiState.update {
                    val on = value != "false"
                    it.copy(freeEntryEnabled = on, amountMode = it.amountMode && on)
                }
            }
        }
    }

    fun imageFile(relativePath: String?): File? = resolveImage(relativePath)

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onProductTap(product: Product) {
        val current = _uiState.value
        if (current.freeEntryEnabled && product.hasDetail) {
            val existing = current.cart.firstOrNull { it.productId == product.id && it.isPartial }
            _uiState.update {
                it.copy(detailSale = DetailSaleState(product, existing?.lineTotal, existing != null))
            }
            return
        }
        if (product.salePrice <= 0L) {
            _uiState.update {
                it.copy(snackbarMessage = "Prix de vente invalide pour ${product.name}")
            }
            return
        }
        _uiState.update {
            it.copy(
                quantityOverlay = QuantityOverlay(
                    productId = product.id,
                    productName = product.name,
                    unitPrice = product.salePrice,
                    imagePath = product.imagePath,
                    quantityInput = if (it.amountMode && it.freeEntryEnabled) "" else "1",
                    isEditing = false,
                    amountMode = it.amountMode && it.freeEntryEnabled,
                ),
            )
        }
    }

    fun dismissDetailSale() {
        _uiState.update { it.copy(detailSale = null) }
    }

    fun confirmDetailSale(amount: Long, fraction: Double, label: String) {
        val detail = _uiState.value.detailSale ?: return
        if (amount <= 0L || fraction <= 0.0) return
        val line = CartLine(
            productId = detail.product.id,
            productName = detail.product.name,
            unitPrice = amount,
            quantity = 1,
            imagePath = detail.product.imagePath,
            fraction = fraction,
            detailLabel = label,
        )
        setCart(CartOperations.upsert(_uiState.value.cart, line, replace = detail.isEditing))
        dismissDetailSale()
    }

    fun deleteDetailLine() {
        val detail = _uiState.value.detailSale ?: return
        if (detail.isEditing) {
            setCart(CartOperations.remove(_uiState.value.cart, detail.product.id))
        }
        dismissDetailSale()
    }

    /** Active / coupe l'impression du ticket à l'encaissement (choix mémorisé). */
    fun setPrintOnPayment(enabled: Boolean) {
        _uiState.update { it.copy(printOnPayment = enabled) }
        viewModelScope.launch {
            settingsRepository.set(SettingsKeys.PRINT_ON_PAYMENT, enabled.toString())
        }
    }

    /** Active / coupe le mode vente libre (vente par montant). */
    fun toggleAmountMode() {
        _uiState.update { it.copy(amountMode = !it.amountMode && it.freeEntryEnabled) }
    }

    fun onCartLineLongPress(line: CartLine) {
        val detailProduct = if (line.isPartial) {
            _uiState.value.products.firstOrNull { it.id == line.productId && it.hasDetail }
        } else {
            null
        }
        if (detailProduct != null) {
            _uiState.update {
                it.copy(detailSale = DetailSaleState(detailProduct, line.lineTotal, true))
            }
            return
        }
        _uiState.update {
            it.copy(
                quantityOverlay = if (line.isPartial) {
                    // Ligne « par montant » : on rouvre le montant ; prix du produit = montant / unités.
                    QuantityOverlay(
                        productId = line.productId,
                        productName = line.productName,
                        unitPrice = Math.round(line.unitPrice / line.fraction),
                        imagePath = line.imagePath,
                        quantityInput = line.lineTotal.toString(),
                        isEditing = true,
                        amountMode = true,
                    )
                } else {
                    QuantityOverlay(
                        productId = line.productId,
                        productName = line.productName,
                        unitPrice = line.unitPrice,
                        imagePath = line.imagePath,
                        quantityInput = line.quantity.toString(),
                        isEditing = true,
                    )
                },
            )
        }
    }

    fun onQuantityInputChange(value: String) {
        _uiState.update { state ->
            val overlay = state.quantityOverlay ?: return@update state
            state.copy(quantityOverlay = overlay.copy(quantityInput = value))
        }
    }

    fun dismissQuantityOverlay() {
        _uiState.update { it.copy(quantityOverlay = null) }
    }

    fun confirmQuantity() {
        val overlay = _uiState.value.quantityOverlay ?: return
        if (overlay.amountMode) {
            confirmAmount(overlay)
            return
        }
        val qty = overlay.quantityInput.toIntOrNull() ?: return
        if (qty <= 0) {
            if (overlay.isEditing) setCart(CartOperations.remove(_uiState.value.cart, overlay.productId))
            dismissQuantityOverlay()
            return
        }
        val line = CartLine(
            productId = overlay.productId,
            productName = overlay.productName,
            unitPrice = overlay.unitPrice,
            quantity = qty,
            imagePath = overlay.imagePath,
        )
        setCart(CartOperations.upsert(_uiState.value.cart, line, replace = overlay.isEditing))
        dismissQuantityOverlay()
    }

    /** Vente par montant : quantité = montant / prix (ex. 100 F d'un produit à 1100 F = 0,091). */
    private fun confirmAmount(overlay: QuantityOverlay) {
        val amount = overlay.quantityInput.toLongOrNull() ?: return
        if (amount <= 0L) {
            if (overlay.isEditing) setCart(CartOperations.remove(_uiState.value.cart, overlay.productId))
            dismissQuantityOverlay()
            return
        }
        if (overlay.unitPrice <= 0L) return
        val line = CartLine(
            productId = overlay.productId,
            productName = overlay.productName,
            unitPrice = amount,
            quantity = 1,
            imagePath = overlay.imagePath,
            fraction = amount.toDouble() / overlay.unitPrice.toDouble(),
        )
        setCart(CartOperations.upsert(_uiState.value.cart, line, replace = overlay.isEditing))
        dismissQuantityOverlay()
    }

    fun deleteOverlayLine() {
        val overlay = _uiState.value.quantityOverlay ?: return
        if (overlay.isEditing) {
            setCart(CartOperations.remove(_uiState.value.cart, overlay.productId))
        }
        dismissQuantityOverlay()
    }

    private fun setCart(cart: List<CartLine>) {
        savedStateHandle[KEY_CART] = CartJson.encode(cart)
        _uiState.update { it.copy(cart = cart) }
    }

    /**
     * Enregistre une commande sans paiement.
     * Reste en Caisse ; si impression ON : tire le ticket + dialogue OK / réimprimer.
     */
    fun saveUnpaidOrder() {
        val state = _uiState.value
        if (state.cart.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Panier vide") }
            return
        }
        if (!saleInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val order = orderRepository.createOrder(
                    CreateOrderRequest(
                        lines = state.cart,
                        markAsPaid = false,
                    ),
                )
                savedStateHandle[KEY_CART] = CartJson.encode(emptyList())
                val printEnabled = printer.isEnabled()
                var printMessage: String? = null
                if (printEnabled && state.printOnPayment) {
                    val result = printer.printOrder(order)
                    printMessage = if (result.isSuccess) {
                        "Ticket imprimé"
                    } else {
                        result.exceptionOrNull()?.message ?: "Échec impression"
                    }
                }
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        completedOrder = order,
                        savedOrderPrompt = SavedOrderPrompt(
                            order = order,
                            printEnabled = printEnabled,
                            printMessage = printMessage,
                        ),
                        snackbarMessage = "Commande ${order.publicId} enregistrée",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = e.message ?: "Échec commande") }
            } finally {
                saleInFlight.set(false)
            }
        }
    }

    fun reprintSavedOrder() = viewModelScope.launch {
        val order = _uiState.value.savedOrderPrompt?.order ?: return@launch
        val result = printer.printOrder(order)
        _uiState.update {
            it.copy(
                savedOrderPrompt = it.savedOrderPrompt?.copy(
                    printMessage = if (result.isSuccess) {
                        "Ticket imprimé"
                    } else {
                        result.exceptionOrNull()?.message ?: "Échec impression"
                    },
                ),
            )
        }
    }

    /** Ferme le dialogue et reste en Caisse. */
    fun dismissSavedOrderPrompt() {
        _uiState.update { it.copy(savedOrderPrompt = null) }
    }

    fun openPayment() {
        val state = _uiState.value
        if (state.cart.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "Panier vide") }
            return
        }
        if (state.cartTotal <= 0L) {
            _uiState.update { it.copy(snackbarMessage = "Montant total invalide") }
            return
        }
        _uiState.update {
            it.copy(
                showPayment = true,
                payment = PaymentFormState(
                    mode = PaymentMode.CASH,
                    amountTendered = it.cartTotal.toString(),
                    tenderedExplicit = true,
                    activeField = PaymentField.TENDERED,
                ),
            )
        }
    }

    fun dismissPayment() {
        if (_uiState.value.payment.isSaving) return
        _uiState.update {
            it.copy(showPayment = false, payment = PaymentFormState())
        }
    }

    fun selectPaymentMode(mode: PaymentMode) {
        val total = _uiState.value.cartTotal.toString()
        _uiState.update { state ->
            state.copy(
                payment = when (mode) {
                    PaymentMode.CASH -> PaymentFormState(
                        mode = mode,
                        amountTendered = total,
                        tenderedExplicit = true,
                        activeField = PaymentField.TENDERED,
                    )
                    PaymentMode.MIXED -> PaymentFormState(
                        mode = mode,
                        cashAmount = total,
                        amountTendered = "",
                        tenderedExplicit = false,
                        activeField = PaymentField.CASH,
                    )
                    else -> PaymentFormState(mode = mode)
                },
            )
        }
    }

    fun selectPaymentField(field: PaymentField) {
        _uiState.update { state ->
            state.copy(
                payment = state.payment.copy(
                    activeField = field,
                    errorMessage = null,
                    tenderedExplicit = state.payment.tenderedExplicit ||
                        field == PaymentField.TENDERED,
                ),
            )
        }
    }

    fun onPaymentDigitInput(value: String) {
        _uiState.update { state ->
            val p = state.payment
            val updated = when (p.activeField) {
                PaymentField.TENDERED -> p.copy(
                    amountTendered = value,
                    tenderedExplicit = true,
                    errorMessage = null,
                )
                PaymentField.CASH -> p.copy(cashAmount = value, errorMessage = null)
                PaymentField.MOBILE_MONEY -> p.copy(mobileMoneyAmount = value, errorMessage = null)
                PaymentField.VOUCHER -> p.copy(voucherAmount = value, errorMessage = null)
                PaymentField.DEBT -> p.copy(debtAmount = value, errorMessage = null)
            }
            state.copy(payment = updated)
        }
    }

    fun changePreview(): Long {
        val state = _uiState.value
        return PaymentCalculator.previewChange(state.cartTotal, state.payment.toPaymentInput())
    }

    fun canConfirmPayment(): Boolean {
        val state = _uiState.value
        if (state.payment.isSaving) return false
        return PaymentCalculator.validate(state.cartTotal, state.payment.toPaymentInput()).isSuccess
    }

    fun confirmPayment() {
        if (!saleInFlight.compareAndSet(false, true)) return
        val state = _uiState.value
        if (!state.showPayment) {
            saleInFlight.set(false)
            return
        }

        _uiState.update {
            it.copy(payment = it.payment.copy(isSaving = true, errorMessage = null))
        }

        viewModelScope.launch {
            try {
                val paymentInput = state.payment.toPaymentInput()
                val breakdown = PaymentCalculator.validate(state.cartTotal, paymentInput).getOrThrow()
                val order = orderRepository.createOrder(
                    CreateOrderRequest(
                        lines = state.cart,
                        markAsPaid = true,
                        paymentMode = breakdown.mode,
                        amountTendered = breakdown.amountTendered,
                        paymentAmount = breakdown.totalAmount,
                    ),
                )
                savedStateHandle[KEY_CART] = CartJson.encode(emptyList())
                _uiState.update {
                    it.copy(
                        cart = emptyList(),
                        showPayment = false,
                        payment = PaymentFormState(),
                        completedOrder = order,
                        completedSale = null,
                        quantityOverlay = null,
                        snackbarMessage = "Commande ${order.publicId} payée",
                    )
                }
                // Impression après coup : un échec d'imprimante n'annule jamais la vente.
                if (state.printAvailable && state.printOnPayment) {
                    val result = printer.printOrder(order)
                    val note = if (result.isSuccess) {
                        "ticket imprimé"
                    } else {
                        "impression impossible (${result.exceptionOrNull()?.message ?: "erreur"})"
                    }
                    _uiState.update {
                        it.copy(snackbarMessage = "Commande ${order.publicId} payée · $note")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        payment = it.payment.copy(
                            isSaving = false,
                            errorMessage = e.message ?: "Échec de la vente",
                        ),
                    )
                }
            } finally {
                saleInFlight.set(false)
            }
        }
    }

    fun dismissTicket() {
        _uiState.update { it.copy(completedSale = null, completedOrder = null) }
    }

    fun clearCart() {
        setCart(emptyList())
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        private const val KEY_CART = "caisse_cart_json"
    }
}
