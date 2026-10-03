package com.lpms.ui.screens.customers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.local.CustomerEntity
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.repository.CustomerAccountSnapshot
import com.lpms.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [CustomerDetailScreen].
 *
 * The customer row comes from Room (so it works offline); the account totals
 * and ledger are a server aggregate, so they are fetched over the network and
 * only cached as a snapshot. Payments and adjustments are queued locally and
 * pushed by the sync engine, so recording a debt payment works offline.
 */
@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    private val _customer = MutableStateFlow<CustomerEntity?>(null)
    val customer: StateFlow<CustomerEntity?> = _customer.asStateFlow()

    private val _account = MutableStateFlow<CustomerAccountSnapshot?>(null)
    val account: StateFlow<CustomerAccountSnapshot?> = _account.asStateFlow()

    var loading by mutableStateOf(false)
        private set

    var error by mutableStateOf<ApiError?>(null)
        private set

    private var localId: String? = null
    private var loaded = false

    fun load(customerId: String) {
        if (loaded) return
        loaded = true
        localId = customerId

        viewModelScope.launch {
            val customer = customerRepository.byLocalId(customerId)
            if (customer == null) {
                error = ApiError.Http(404, "NOT_FOUND", "customer_not_found")
                return@launch
            }
            _customer.value = customer
            refreshAccount()
        }
    }

    fun refreshAccount() {
        val id = localId ?: return
        loading = true
        viewModelScope.launch {
            when (val result = customerRepository.refreshAccount(id)) {
                is ApiResult.Success -> {
                    loading = false
                    _account.value = result.value
                }
                is ApiResult.Failure -> {
                    loading = false
                    error = result.error
                }
            }
        }
    }

    fun recordPayment(amount: Double, method: String, notes: String?) {
        val id = localId ?: return
        viewModelScope.launch {
            when (val result = customerRepository.recordPayment(id, amount, method, notes)) {
                is ApiResult.Success -> refreshAccount()
                is ApiResult.Failure -> error = result.error
            }
        }
    }

    fun recordAdjustment(amount: Double, direction: String, description: String) {
        val id = localId ?: return
        viewModelScope.launch {
            when (val result = customerRepository.recordAdjustment(id, amount, direction, description)) {
                is ApiResult.Success -> refreshAccount()
                is ApiResult.Failure -> error = result.error
            }
        }
    }
}
