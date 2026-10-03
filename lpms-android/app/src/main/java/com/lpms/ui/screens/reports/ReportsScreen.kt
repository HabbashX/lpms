package com.lpms.ui.screens.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.remote.dto.ProfitDetailResponse
import com.lpms.data.remote.dto.ProfitReportResponse
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsDetailRow
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsPrimaryButton
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money

/**
 * Profit reports: daily, weekly, monthly, or a custom range.
 *
 * Reports are a server-side aggregate, so this screen needs a connection. The
 * summary cards and the line-details table are both driven by the selected
 * range.
 */
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.reports_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RangeSelector(viewModel = viewModel)

            if (report == null) {
                com.lpms.ui.components.LpmsLoading()
                return@Column
            }

            val current = report!!

            LpmsCard {
                LpmsDetailRow(
                    label = stringResource(R.string.report_revenue),
                    value = money(current.revenue),
                )
                LpmsDetailRow(
                    label = stringResource(R.string.report_cost),
                    value = money(current.cost),
                )
                LpmsDetailRow(
                    label = stringResource(R.string.report_profit),
                    value = money(current.profit),
                )
                LpmsDetailRow(
                    label = stringResource(R.string.report_sales_count),
                    value = current.salesCount.toString(),
                )
            }

            Text(
                text = stringResource(R.string.report_details),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (details.isEmpty()) {
                LpmsEmptyState(title = stringResource(R.string.report_details_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(details, key = { it.saleId }) { line ->
                        DetailRow(line = line)
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(viewModel: ReportsViewModel) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = from,
            onValueChange = { from = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.report_from)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = to,
            onValueChange = { to = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.report_to)) },
            singleLine = true,
        )
    }

    LpmsPrimaryButton(
        text = stringResource(R.string.action_apply),
        onClick = { viewModel.loadCustom(from, to) },
    )
}

@Composable
private fun DetailRow(line: ProfitDetailResponse) {
    LpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = line.drug, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${line.quantity} × ${money(line.revenue / line.quantity)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text(text = money(line.revenue), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = money(line.profit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
