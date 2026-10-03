package com.lpms.ui.screens.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.AuditLogResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [AuditScreen]. The audit log is fetched over the network.
 */
@HiltViewModel
class AuditViewModel @Inject constructor(
    private val api: LpmsApi,
    private val executor: ApiExecutor,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<AuditLogResponse>>(emptyList())
    val entries: StateFlow<List<AuditLogResponse>> = _entries.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = executor.run { api.searchAudit() }) {
                is ApiResult.Success -> _entries.value = result.value.content
                is ApiResult.Failure -> { /* keep last good data */ }
            }
        }
    }
}
