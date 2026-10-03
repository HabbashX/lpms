package com.lpms.ui.screens.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.local.CustomerEntity
import com.lpms.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs [CustomerListScreen]. The list is a Room flow, so it reflects new
 * customers and debt changes as they happen.
 */
@HiltViewModel
class CustomerListViewModel @Inject constructor(
    customerRepository: CustomerRepository,
) : ViewModel() {

    val customers: StateFlow<List<CustomerEntity>> =
        customerRepository.observeCatalog("")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
