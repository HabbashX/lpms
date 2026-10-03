package com.lpms.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.ProfitDetailResponse
import com.lpms.data.remote.dto.ProfitReportResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [ReportsScreen].
 *
 * The summary and the line details are fetched over the network. The last
 * successful response is kept so the screen can show previous data while a
 * refresh is in flight.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val api: LpmsApi,
    private val executor: ApiExecutor,
) : ViewModel() {

    private val _report = MutableStateFlow<ProfitReportResponse?>(null)
    val report: StateFlow<ProfitReportResponse?> = _report.asStateFlow()

    private val _details = MutableStateFlow<List<ProfitDetailResponse>>(emptyList())
    val details: StateFlow<List<ProfitDetailResponse>> = _details.asStateFlow()

    fun loadCustom(from: String, to: String) {
        viewModelScope.launch {
            when (val result = executor.run { api.profitReport(from = from.ifBlank { null }, to = to.ifBlank { null }) }) {
                is ApiResult.Success -> _report.value = result.value
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
        viewModelScope.launch {
            when (val result = executor.run { api.profitDetails(from = from.ifBlank { null }, to = to.ifBlank { null }) }) {
                is ApiResult.Success -> _details.value = result.value.content
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
    }
}
