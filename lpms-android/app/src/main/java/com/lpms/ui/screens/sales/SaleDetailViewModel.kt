package com.lpms.ui.screens.sales

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.local.SaleEntity
import com.lpms.data.local.SaleItemEntity
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.repository.RefundLine
import com.lpms.data.repository.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [SaleDetailScreen].
 *
 * The sale and its lines come from Room. Refunds go through the repository,
 * which requires the sale to have synced (a server id) — an offline sale must
 * be pushed before it can be refunded.
 */
@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
) : ViewModel() {

    private val _sale = MutableStateFlow<SaleEntity?>(null)
    val sale: StateFlow<SaleEntity?> = _sale.asStateFlow()

    private val _items = MutableStateFlow<List<SaleItemEntity>>(emptyList())
    val items: StateFlow<List<SaleItemEntity>> = _items.asStateFlow()

    var refunding by mutableStateOf(false)
        private set

    var error by mutableStateOf<ApiError?>(null)
        private set

    private var localId: String? = null
    private var loaded = false

    fun load(saleId: String) {
        if (loaded) return
        loaded = true
        localId = saleId

        viewModelScope.launch {
            val sale = saleRepository.byLocalId(saleId)
            if (sale == null) {
                error = ApiError.Http(404, "NOT_FOUND", "sale_not_found")
                return@launch
            }
            _sale.value = sale
            _items.value = saleRepository.items(saleId)
        }
    }

    fun refund(lines: List<RefundLine>, reason: String?) {
        val id = localId ?: return
        refunding = true
        error = null

        viewModelScope.launch {
            when (val result = saleRepository.refund(id, lines, reason)) {
                is ApiResult.Success -> {
                    refunding = false
                    _sale.value = saleRepository.byLocalId(id)
                    _items.value = saleRepository.items(id)
                }
                is ApiResult.Failure -> {
                    refunding = false
                    error = result.error
                }
            }
        }
    }
}
