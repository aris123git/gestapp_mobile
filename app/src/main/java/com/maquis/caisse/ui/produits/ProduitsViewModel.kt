package com.maquis.caisse.ui.produits

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maquis.caisse.core.SessionManager
import com.maquis.caisse.domain.model.Category
import com.maquis.caisse.domain.model.Product
import com.maquis.caisse.domain.repository.CategoryRepository
import com.maquis.caisse.domain.usecase.AddProductUseCase
import com.maquis.caisse.domain.usecase.DeleteProductUseCase
import com.maquis.caisse.domain.usecase.ObserveProductsUseCase
import com.maquis.caisse.domain.usecase.ResolveProductImageUseCase
import com.maquis.caisse.domain.usecase.UpdateProductUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProductFormState(
    val editingId: Long? = null,
    val name: String = "",
    val category: String = "Boissons",
    val salePrice: String = "",
    val purchasePrice: String = "",
    val stock: String = "0",
    val alertThreshold: String = "5",
    val isActive: Boolean = true,
    /** false = ce produit n'a pas de stock géré. */
    val trackStock: Boolean = true,
    /** Vente au détail (vente libre) : ex. bidon de 20 L vendu au litre. */
    val detailEnabled: Boolean = false,
    val contentPerUnit: String = "",
    val stockUnit: String = "Carton",
    val detailUnit: String = "Kg",
    val detailPrice: String = "",
    /** Image déjà persistée (édition). */
    val existingImagePath: String? = null,
    /** Nouvelle image choisie (galerie/caméra), pas encore sauvegardée. */
    val pendingImageUri: Uri? = null,
    val clearExistingImage: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

data class ProduitsUiState(
    val products: List<Product> = emptyList(),
    val categories: List<String> = emptyList(),
    val form: ProductFormState? = null,
    val snackbarMessage: String? = null,
    val canManageProducts: Boolean = false,
    /** Option globale Paramètres : suivi de stock actif. */
    val stockTrackingEnabled: Boolean = true,
    /** Option Paramètres : vente libre (détail) disponible. */
    val freeEntryEnabled: Boolean = true,
)

@HiltViewModel
class ProduitsViewModel @Inject constructor(
    observeProducts: ObserveProductsUseCase,
    categoryRepository: CategoryRepository,
    private val addProduct: AddProductUseCase,
    private val updateProduct: UpdateProductUseCase,
    private val deleteProduct: DeleteProductUseCase,
    private val resolveImage: ResolveProductImageUseCase,
    private val session: SessionManager,
    private val settingsRepository: com.maquis.caisse.domain.repository.SettingsRepository,
) : ViewModel() {

    private fun isAdmin(): Boolean = session.userOrNull()?.role == "ADMIN"

    private val productsFlow: StateFlow<List<Product>> = observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val categoriesFlow: StateFlow<List<String>> = categoryRepository.observeActive()
        .map { list -> list.map(Category::name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(ProduitsUiState(canManageProducts = isAdmin()))
    val uiState: StateFlow<ProduitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            session.currentUser.collect { user ->
                _uiState.update { it.copy(canManageProducts = user?.role == "ADMIN") }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(com.maquis.caisse.core.SettingsKeys.STOCK_TRACKING_ENABLED).collect { v ->
                _uiState.update { it.copy(stockTrackingEnabled = v != "false") }
            }
        }
        viewModelScope.launch {
            settingsRepository.observe(com.maquis.caisse.core.SettingsKeys.FREE_ENTRY_ENABLED).collect { v ->
                _uiState.update { it.copy(freeEntryEnabled = v != "false") }
            }
        }
        viewModelScope.launch {
            productsFlow.collect { products ->
                _uiState.update { it.copy(products = products) }
            }
        }
        viewModelScope.launch {
            categoriesFlow.collect { categories ->
                _uiState.update { state ->
                    val form = state.form
                    state.copy(
                        categories = categories,
                        form = if (form != null && form.category.isBlank() && categories.isNotEmpty()) {
                            form.copy(category = categories.first())
                        } else {
                            form
                        },
                    )
                }
            }
        }
    }

    fun imageFile(relativePath: String?): File? = resolveImage(relativePath)

    fun openCreateForm() {
        if (!isAdmin()) {
            _uiState.update { it.copy(snackbarMessage = "Seul l'administrateur peut ajouter un produit") }
            return
        }
        val defaultCategory = _uiState.value.categories.firstOrNull() ?: "Boissons"
        _uiState.update { it.copy(form = ProductFormState(category = defaultCategory)) }
    }

    fun openEditForm(product: Product) {
        if (!isAdmin()) {
            _uiState.update { it.copy(snackbarMessage = "Seul l'administrateur peut modifier un produit") }
            return
        }
        _uiState.update {
            it.copy(
                form = ProductFormState(
                    editingId = product.id,
                    name = product.name,
                    category = product.category,
                    salePrice = product.salePrice.toString(),
                    purchasePrice = product.purchasePrice.toString(),
                    stock = product.stock.toString(),
                    alertThreshold = product.alertThreshold.toString(),
                    isActive = product.isActive,
                    trackStock = product.trackStock,
                    detailEnabled = product.hasDetail,
                    contentPerUnit = if (product.hasDetail) {
                        com.maquis.caisse.domain.model.formatUnits(product.contentPerUnit)
                    } else {
                        ""
                    },
                    stockUnit = product.stockUnit?.takeIf { it.isNotBlank() } ?: "Carton",
                    detailUnit = product.detailUnit?.takeIf { it.isNotBlank() } ?: "Kg",
                    detailPrice = if (product.hasDetail) product.detailPrice.toString() else "",
                    existingImagePath = product.imagePath,
                ),
            )
        }
    }

    fun closeForm() {
        _uiState.update { it.copy(form = null) }
    }

    fun updateForm(transform: (ProductFormState) -> ProductFormState) {
        _uiState.update { state ->
            val form = state.form ?: return@update state
            state.copy(form = transform(form).copy(errorMessage = null))
        }
    }

    fun onImagePicked(uri: Uri) {
        updateForm {
            it.copy(
                pendingImageUri = uri,
                clearExistingImage = false,
            )
        }
    }

    fun onClearImage() {
        updateForm {
            it.copy(
                pendingImageUri = null,
                existingImagePath = null,
                clearExistingImage = true,
            )
        }
    }

    fun saveForm() {
        if (!isAdmin()) {
            _uiState.update { it.copy(snackbarMessage = "Seul l'administrateur peut gérer les produits") }
            return
        }
        val form = _uiState.value.form ?: return
        val name = form.name.trim()
        if (name.isEmpty()) {
            updateForm { it.copy(errorMessage = "Le nom est obligatoire") }
            return
        }
        val purchasePrice = form.purchasePrice.toLongOrNull() ?: 0L
        val stock = form.stock.toIntOrNull()
        val alert = form.alertThreshold.toIntOrNull()

        var content = 0.0
        var detailPrice = 0L
        if (form.detailEnabled) {
            content = com.maquis.caisse.domain.cart.DetailSale.parseQuantity(form.contentPerUnit) ?: 0.0
            detailPrice = form.detailPrice.toLongOrNull() ?: 0L
            if (content <= 0.0) {
                updateForm { it.copy(errorMessage = "Nombre d'unités invalide") }
                return
            }
            if (detailPrice <= 0L) {
                updateForm { it.copy(errorMessage = "Prix de l'unité invalide") }
                return
            }
        }
        // En vente libre, le prix du produit entier se déduit : prix de l'unité × nombre d'unités.
        val salePrice = if (form.detailEnabled) {
            Math.round(detailPrice * content)
        } else {
            form.salePrice.toLongOrNull()
        }
        if (salePrice == null || salePrice <= 0) {
            updateForm { it.copy(errorMessage = "Prix de vente invalide (doit être > 0)") }
            return
        }
        if (purchasePrice < 0) {
            updateForm { it.copy(errorMessage = "Prix d'achat invalide") }
            return
        }
        val tracked = form.trackStock
        if (tracked && (stock == null || stock < 0)) {
            updateForm { it.copy(errorMessage = "Stock invalide") }
            return
        }
        if (tracked && (alert == null || alert < 0)) {
            updateForm { it.copy(errorMessage = "Seuil d'alerte invalide") }
            return
        }

        val product = Product(
            id = form.editingId ?: 0L,
            name = name,
            category = form.category.trim().ifEmpty { "Divers" },
            salePrice = salePrice,
            purchasePrice = purchasePrice,
            stock = stock ?: 0,
            alertThreshold = alert ?: 0,
            imagePath = form.existingImagePath,
            isActive = form.isActive,
            trackStock = form.trackStock,
            contentPerUnit = content,
            detailPrice = detailPrice,
            detailUnit = if (form.detailEnabled) form.detailUnit.trim().ifEmpty { null } else null,
            stockUnit = if (form.detailEnabled) form.stockUnit.trim().ifEmpty { null } else null,
        )

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(form = state.form?.copy(isSaving = true, errorMessage = null))
            }
            try {
                if (form.editingId == null) {
                    addProduct(product, form.pendingImageUri)
                    _uiState.update {
                        it.copy(form = null, snackbarMessage = "Produit ajouté")
                    }
                } else {
                    updateProduct(
                        product = product,
                        newImageUri = form.pendingImageUri,
                        clearImage = form.clearExistingImage && form.pendingImageUri == null,
                    )
                    _uiState.update {
                        it.copy(form = null, snackbarMessage = "Produit mis à jour")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        form = state.form?.copy(
                            isSaving = false,
                            errorMessage = e.message ?: "Erreur d'enregistrement",
                        ),
                    )
                }
            }
        }
    }

    fun deleteCurrent() {
        if (!isAdmin()) {
            _uiState.update { it.copy(snackbarMessage = "Seul l'administrateur peut supprimer un produit") }
            return
        }
        val id = _uiState.value.form?.editingId ?: return
        viewModelScope.launch {
            try {
                deleteProduct(id)
                _uiState.update {
                    it.copy(form = null, snackbarMessage = "Produit supprimé")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        form = state.form?.copy(
                            errorMessage = e.message ?: "Suppression impossible",
                        ),
                    )
                }
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
