package com.lpms.ui.screens.sales

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.local.SaleEntity
import com.lpms.data.remote.dto.SaleStatus
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsStatusChip
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money
import com.lpms.ui.components.shortDate

/**
 * Sale history.
 *
 * Sales recorded offline appear here marked as pending until the sync engine
 * has pushed them, so the owner can see what has not reached the server yet.
 */
@Composable
fun SalesScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SalesViewModel = hiltViewModel(),
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.sales_title), onBack = onBack) },
    ) { padding ->
        if (sales.isEmpty()) {
            LpmsEmptyState(
                title = stringResource(R.string.sales_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sales, key = { it.localId }) { sale ->
                    SaleCard(
                        sale = sale,
                        onClick = { onNavigate("sale/${sale.localId}") },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaleCard(
    sale: SaleEntity,
    onClick: () -> Unit,
) {
    LpmsCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sale.customerName ?: stringResource(R.string.sale_cash_customer),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortDate(sale.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = sale.paymentMethod,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = money(sale.total),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(4.dp))
                if (sale.serverId == null) {
                    LpmsStatusChip(text = stringResource(R.string.sale_pending))
                } else {
                    when (sale.status) {
                        SaleStatus.COMPLETED.name -> LpmsStatusChip(
                            text = stringResource(R.string.sale_status_completed),
                        )
                        SaleStatus.PARTIALLY_REFUNDED.name -> LpmsStatusChip(
                            text = stringResource(R.string.sale_status_partially_refunded),
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        SaleStatus.REFUNDED.name -> LpmsStatusChip(
                            text = stringResource(R.string.sale_status_refunded),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}
