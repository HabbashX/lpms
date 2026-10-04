package com.lpms.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.userMessage
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
 * The summary and the line details are fetched from the backend. Data loads on
 * init so the screen shows something immediately, and the last successful
 * response is kept so the screen can show previous data while a refresh is in
 * flight.
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

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadCustom("", "")
    }

    fun loadCustom(from: String, to: String) {
        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            var reportSuccess = false
            var detailsSuccess = false

            try {
                when (val result = executor.run { api.profitReport(from = from.ifBlank { null }, to = to.ifBlank { null }) }) {
                    is ApiResult.Success -> {
                        _report.value = result.value
                        reportSuccess = true
                    }
                    is ApiResult.Failure -> _error.value = result.error.userMessage
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            }

            try {
                when (val result = executor.run { api.profitDetails(from = from.ifBlank { null }, to = to.ifBlank { null }) }) {
                    is ApiResult.Success -> {
                        _details.value = result.value.content
                        detailsSuccess = true
                    }
                    is ApiResult.Failure -> {
                        if (_error.value == null) _error.value = result.error.userMessage
                    }
                }
            } catch (e: Exception) {
                if (_error.value == null) _error.value = e.message ?: "Unknown error"
            }

            _loading.value = false
        }
    }
}