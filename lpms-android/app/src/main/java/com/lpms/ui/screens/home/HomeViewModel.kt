package com.lpms.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.DashboardResponse
import com.lpms.data.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [HomeScreen].
 *
 * The dashboard is a server aggregate, so it is fetched over the network. The
 * last successful response is kept in memory so the screen can show previous
 * figures while a refresh is in flight or when the network is down.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: LpmsApi,
    private val executor: ApiExecutor,
    private val sync: SyncEngine,
) : ViewModel() {

    private val _dashboard = MutableStateFlow<DashboardResponse?>(null)
    val dashboard: StateFlow<DashboardResponse?> = _dashboard.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = executor.run { api.dashboard() }) {
                is ApiResult.Success -> _dashboard.value = result.value
                is ApiResult.Failure -> {
                    // Keep the last good figures; the screen shows them with an
                    // offline banner rather than an error.
                }
            }
        }
    }
}
