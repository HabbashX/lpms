package com.lpms.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lpms.data.local.SaleDao
import com.lpms.data.local.SaleEntity
import com.lpms.data.remote.ApiExecutor
import com.lpms.data.remote.ApiResult
import com.lpms.data.remote.LpmsApi
import com.lpms.data.remote.dto.DashboardResponse
import com.lpms.data.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs [HomeScreen].
 *
 * The day's revenue, profit and sale count come from Room, not the server, because Room
 * holds a sale the instant it is recorded — including one still queued in the outbox.
 * Only the figures that cannot be derived locally (inventory valuation, customer debt,
 * top sellers) are left to the server aggregate.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: LpmsApi,
    private val executor: ApiExecutor,
    private val sync: SyncEngine,
    private val saleDao: SaleDao,
) : ViewModel() {

    private val _dashboard = MutableStateFlow<DashboardResponse?>(null)
    val dashboard: StateFlow<DashboardResponse?> = _dashboard.asStateFlow()

    init {
        observeTodaySales()
        refresh()
    }

    /** Re-applies the local day's takings whenever a sale is added or amended. */
    private fun observeTodaySales() {
        viewModelScope.launch {
            todaySales().collect { applyTodaySales(it) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val localToday = todaySales().first()
            when (val result = executor.run { api.dashboard() }) {
                is ApiResult.Success ->
                    _dashboard.value = withLocalToday(result.value, localToday)
                is ApiResult.Failure ->
                    // Keep the last good figures so the screen can show them
                    // alongside the offline banner instead of an error.
                    _dashboard.value?.let { _dashboard.value = withLocalToday(it, localToday) }
            }
        }
    }

    private fun todaySales(): Flow<List<SaleEntity>> =
        saleDao.observeSince(startOfDayMillis())

    private fun startOfDayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun applyTodaySales(sales: List<SaleEntity>) {
        _dashboard.value?.let { _dashboard.value = withLocalToday(it, sales) }
    }

    /** Net takings, i.e. refunds are already deducted from each sale's total. */
    private fun withLocalToday(
        base: DashboardResponse,
        sales: List<SaleEntity>,
    ): DashboardResponse = base.copy(
        today = base.today.copy(
            revenue = sales.sumOf { it.total - it.refundedTotal },
            profit = sales.sumOf { it.profit },
            sales = sales.size.toLong(),
        ),
    )
}