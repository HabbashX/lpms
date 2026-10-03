package com.lpms.ui.screens.inventory

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.remote.dto.StockBatchResponse
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money

/**
 * Stock: what has been received, what is low, what is expiring, and what it is
 * all worth.
 *
 * Every tab is a server-side view (batches, low stock, expiring, valuation), so
 * this screen needs a connection. Purchases are recorded through the repository
 * and queued, so receiving stock works offline.
 */
@Composable
fun InventoryScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.inventory_tab_batches),
        stringResource(R.string.inventory_tab_low_stock),
        stringResource(R.string.inventory_tab_expiring),
        stringResource(R.string.inventory_tab_valuation),
    )

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.inventory_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> BatchesTab(viewModel = viewModel)
                1 -> LowStockTab(viewModel = viewModel)
                2 -> ExpiringTab(viewModel = viewModel)
                3 -> ValuationTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun BatchesTab(viewModel: InventoryViewModel) {
    val batches by viewModel.batches.collectAsStateWithLifecycle()

    if (batches.isEmpty()) {
        LpmsEmptyState(
            title = stringResource(R.string.inventory_batches_empty),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(batches, key = { it.id }) { batch ->
            BatchCard(batch = batch)
        }
    }
}

@Composable
private fun BatchCard(batch: StockBatchResponse) {
    LpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = batch.drugName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${batch.remainingQuantity}/${batch.quantityReceived}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (batch.batchNumber != null) {
            Text(
                text = batch.batchNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (batch.expirationDate != null) {
            Text(
                text = "${stringResource(R.string.batch_expiry)}: ${batch.expirationDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LowStockTab(viewModel: InventoryViewModel) {
    val drugs by viewModel.lowStock.collectAsStateWithLifecycle()

    if (drugs.isEmpty()) {
        LpmsEmptyState(
            title = stringResource(R.string.inventory_low_stock_empty),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(drugs, key = { it.drugId }) { drug ->
            LpmsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = drug.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${drug.currentQuantity}/${drug.minimumStockLevel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiringTab(viewModel: InventoryViewModel) {
    val batches by viewModel.expiring.collectAsStateWithLifecycle()

    if (batches.isEmpty()) {
        LpmsEmptyState(
            title = stringResource(R.string.inventory_expiring_empty),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(batches, key = { it.batchId }) { batch ->
            LpmsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = batch.drugName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (batch.expired) {
                            stringResource(R.string.batch_expired)
                        } else {
                            stringResource(R.string.batch_expiring_in, batch.daysUntilExpiration.toString())
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (batch.expired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ValuationTab(viewModel: InventoryViewModel) {
    val items by viewModel.valuation.collectAsStateWithLifecycle()

    if (items.isEmpty()) {
        LpmsEmptyState(
            title = stringResource(R.string.inventory_valuation_empty),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.drugId }) { item ->
            LpmsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = item.drugName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = money(item.totalInventoryCost),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = "${item.totalQuantity} ${stringResource(R.string.valuation_total_qty)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
