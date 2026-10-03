package com.lpms.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.DrugValuationResponse
import com.lpms.data.remote.dto.ExpiringBatchResponse
import com.lpms.data.remote.dto.LowStockDrugResponse
import com.lpms.data.remote.dto.StockBatchResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [InventoryScreen].
 *
 * Each tab is a server-side view, so all four are fetched over the network.
 * The last successful response is kept so the screen can show previous data
 * while a refresh is in flight.
 */
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val api: LpmsApi,
    private val executor: ApiExecutor,
) : ViewModel() {

    private val _batches = MutableStateFlow<List<StockBatchResponse>>(emptyList())
    val batches: StateFlow<List<StockBatchResponse>> = _batches.asStateFlow()

    private val _lowStock = MutableStateFlow<List<LowStockDrugResponse>>(emptyList())
    val lowStock: StateFlow<List<LowStockDrugResponse>> = _lowStock.asStateFlow()

    private val _expiring = MutableStateFlow<List<ExpiringBatchResponse>>(emptyList())
    val expiring: StateFlow<List<ExpiringBatchResponse>> = _expiring.asStateFlow()

    private val _valuation = MutableStateFlow<List<DrugValuationResponse>>(emptyList())
    val valuation: StateFlow<List<DrugValuationResponse>> = _valuation.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = executor.run { api.listBatches() }) {
                is ApiResult.Success -> _batches.value = result.value.content
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
        viewModelScope.launch {
            when (val result = executor.run { api.lowStock() }) {
                is ApiResult.Success -> _lowStock.value = result.value.content
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
        viewModelScope.launch {
            when (val result = executor.run { api.expiring() }) {
                is ApiResult.Success -> _expiring.value = result.value.content
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
        viewModelScope.launch {
            when (val result = executor.run { api.valuation() }) {
                is ApiResult.Success -> _valuation.value = result.value.content
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
    }
}
