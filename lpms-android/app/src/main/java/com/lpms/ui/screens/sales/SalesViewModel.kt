package com.lpms.ui.screens.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.local.SaleEntity
import com.lpms.data.repository.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Backs [SalesScreen]. The list is a Room flow, so offline sales show up
 * immediately, marked as pending until they sync.
 */
@HiltViewModel
class SalesViewModel @Inject constructor(
    saleRepository: SaleRepository,
) : ViewModel() {

    val sales: StateFlow<List<SaleEntity>> =
        saleRepository.observeSales()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
