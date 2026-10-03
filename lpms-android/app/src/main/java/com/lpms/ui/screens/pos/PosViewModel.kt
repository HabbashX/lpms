package com.lpms.ui.screens.pos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.local.CustomerEntity
import com.lpms.data.local.DrugEntity
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.repository.CartLine
import com.lpms.data.repository.CustomerRepository
import com.lpms.data.repository.DrugRepository
import com.lpms.data.repository.SaleDraft
import com.lpms.data.repository.SaleRepository
import com.lpms.data.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Point-of-sale state.
 *
 * The cart is plain in-memory state: it is only ever one transaction, so it does
 * not need to survive process death the way the catalog does. Everything the
 * cashier has chosen is held here and handed to [SaleRepository.recordSale] in
 * one shot at checkout.
 */
@HiltViewModel
class PosViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
    private val customerRepository: CustomerRepository,
    private val saleRepository: SaleRepository,
    sync: SyncEngine,
) : ViewModel() {

    /** The active, sellable catalog — the POS's source of truth for drugs. */
    val drugs: StateFlow<List<DrugEntity>> =
        drugRepository.observeSellable("")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every customer, for the "charge to customer" picker. */
    val customers: StateFlow<List<CustomerEntity>> =
        customerRepository.observeCatalog("")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the device currently has a connection, for the offline banner. */
    val online: StateFlow<Boolean> =
        sync.state.map { it.online }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sync.state.value.online)

    var search by mutableStateOf("")
        private set

    var customerSearch by mutableStateOf("")
        private set

    val cart = mutableStateListOf<CartLine>()

    var customer by mutableStateOf<CustomerEntity?>(null)
        private set

    var paymentMethod by mutableStateOf("CASH")
        private set

    var discount by mutableStateOf("")
        private set

    var amountPaid by mutableStateOf("")
        private set

    var submitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<ApiError?>(null)
        private set

    var fieldErrors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Set once a sale has been recorded, so the screen can show a receipt. */
    var completedSaleId by mutableStateOf<String?>(null)
        private set

    fun onSearchChange(value: String) {
        search = value
    }

    fun onCustomerSearchChange(value: String) {
        customerSearch = value
    }

    fun filteredDrugs(): List<DrugEntity> {
        val term = search.trim()
        if (term.isEmpty()) return drugs.value
        return drugs.value.filter {
            it.name.contains(term, ignoreCase = true) ||
                it.genericName?.contains(term, ignoreCase = true) == true ||
                it.barcode?.contains(term, ignoreCase = true) == true
        }
    }

    fun filteredCustomers(): List<CustomerEntity> {
        val term = customerSearch.trim()
        if (term.isEmpty()) return customers.value
        return customers.value.filter {
            it.name.contains(term, ignoreCase = true) ||
                it.phone?.contains(term, ignoreCase = true) == true
        }
    }

    /** Adds a drug to the cart, or bumps the quantity if it is already there. */
    fun addToCart(drug: DrugEntity) {
        error = null
        val existing = cart.indexOfFirst { it.drugLocalId == drug.localId }
        if (existing >= 0) {
            val line = cart[existing]
            if (line.quantity + 1 > drug.currentQuantity) {
                error = ApiError.Http(409, "INSUFFICIENT_STOCK", "insufficient_stock")
                return
            }
            cart[existing] = line.copy(quantity = line.quantity + 1)
        } else {
            if (drug.currentQuantity <= 0) {
                error = ApiError.Http(409, "OUT_OF_STOCK", "out_of_stock")
                return
            }
            cart += CartLine(
                drugLocalId = drug.localId,
                drugName = drug.name,
                quantity = 1,
                unitSellingPrice = drug.lastSellPrice ?: 0.0,
            )
        }
    }

    /** Adds a drug to the cart with a specific quantity. */
    fun addToCartWithQuantity(drug: DrugEntity, quantity: Int) {
        if (quantity <= 0) return
        error = null
        val existing = cart.indexOfFirst { it.drugLocalId == drug.localId }
        if (existing >= 0) {
            val line = cart[existing]
            if (line.quantity + quantity > drug.currentQuantity) {
                error = ApiError.Http(409, "INSUFFICIENT_STOCK", "insufficient_stock")
                return
            }
            cart[existing] = line.copy(quantity = line.quantity + quantity)
        } else {
            if (quantity > drug.currentQuantity) {
                error = ApiError.Http(409, "INSUFFICIENT_STOCK", "insufficient_stock")
                return
            }
            cart += CartLine(
                drugLocalId = drug.localId,
                drugName = drug.name,
                quantity = quantity,
                unitSellingPrice = drug.lastSellPrice ?: 0.0,
            )
        }
    }

    fun updateQuantity(drugLocalId: String, quantity: Int) {
        val index = cart.indexOfFirst { it.drugLocalId == drugLocalId }
        if (index < 0) return
        if (quantity <= 0) {
            cart.removeAt(index)
            return
        }
        val drug = drugs.value.firstOrNull { it.localId == drugLocalId }
        if (drug != null && quantity > drug.currentQuantity) {
            error = ApiError.Http(409, "INSUFFICIENT_STOCK", "insufficient_stock")
            return
        }
        cart[index] = cart[index].copy(quantity = quantity)
    }

    fun updateUnitPrice(drugLocalId: String, price: Double) {
        val index = cart.indexOfFirst { it.drugLocalId == drugLocalId }
        if (index < 0) return
        cart[index] = cart[index].copy(unitSellingPrice = price)
    }

    fun removeFromCart(drugLocalId: String) {
        cart.removeAll { it.drugLocalId == drugLocalId }
    }

    fun selectCustomer(value: CustomerEntity?) {
        customer = value
    }

    fun onPaymentMethodChange(value: String) {
        paymentMethod = value
        error = null
    }

    fun onDiscountChange(value: String) {
        discount = value
        error = null
    }

    fun onAmountPaidChange(value: String) {
        amountPaid = value
        error = null
    }

    val subtotal: Double get() = cart.sumOf { it.quantity * it.unitSellingPrice }
    val discountValue: Double get() = discount.toDoubleOrNull() ?: 0.0
    val total: Double get() = (subtotal - discountValue).coerceAtLeast(0.0)
    val paidValue: Double get() = amountPaid.toDoubleOrNull() ?: total
    val change: Double get() = (paidValue - total).coerceAtLeast(0.0)
    val due: Double get() = (total - paidValue).coerceAtLeast(0.0)

    fun checkout(createdBy: String? = null) {
        if (submitting) return
        if (cart.isEmpty()) {
            fieldErrors = mapOf("cart" to "__required__")
            return
        }

        submitting = true
        error = null
        fieldErrors = emptyMap()

        val draft = SaleDraft(
            customerLocalId = customer?.localId,
            paymentMethod = paymentMethod,
            discount = discountValue,
            amountPaid = amountPaid.ifBlank { null }?.toDoubleOrNull(),
            lines = cart.toList(),
            createdBy = createdBy,
        )

        viewModelScope.launch {
            when (val result = saleRepository.recordSale(draft)) {
                is ApiResult.Success -> {
                    submitting = false
                    completedSaleId = result.value
                }

                is ApiResult.Failure -> {
                    submitting = false
                    onError(result.error)
                }
            }
        }
    }

    /** Clears the cart so the next sale starts empty. */
    fun reset() {
        cart.clear()
        customer = null
        paymentMethod = "CASH"
        discount = ""
        amountPaid = ""
        error = null
        fieldErrors = emptyMap()
        completedSaleId = null
    }

    private fun onError(error: ApiError) {
        fieldErrors = when (error) {
            is ApiError.Http -> error.fieldErrors
            else -> emptyMap()
        }
        this.error = error
    }
}
