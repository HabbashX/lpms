package com.lpms.ui.screens.drugs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.lpms.data.local.DrugEntity
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsStatusChip
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money

/**
 * The drug catalog.
 *
 * Reads from Room, so it works offline and reflects local edits immediately.
 * Search is applied on top of the already-filtered catalog flow.
 */
@Composable
fun DrugListScreen(
    onNavigate: (String) -> Unit,
    viewModel: DrugListViewModel = hiltViewModel(),
) {
    val drugs by viewModel.drugs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.drugs_title)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate("drug/new") }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.drug_new))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            var search by remember { mutableStateOf("") }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.drugs_search_hint)) },
                singleLine = true,
            )

            val filtered = remember(drugs, search) {
                val term = search.trim()
                if (term.isEmpty()) drugs else drugs.filter {
                    it.name.contains(term, ignoreCase = true) ||
                        it.genericName?.contains(term, ignoreCase = true) == true ||
                        it.barcode?.contains(term, ignoreCase = true) == true
                }
            }

            if (filtered.isEmpty()) {
                LpmsEmptyState(
                    title = stringResource(R.string.drugs_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.localId }) { drug ->
                        DrugCard(
                            drug = drug,
                            onClick = { onNavigate("drug?drugId=${drug.serverId ?: -1}") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrugCard(
    drug: DrugEntity,
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
                    text = drug.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (drug.genericName != null) {
                    Text(
                        text = drug.genericName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${drug.currentQuantity} ${drug.unit ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = money(drug.lastSellPrice),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (drug.currentQuantity <= drug.minimumStockLevel) {
                    Spacer(Modifier.height(4.dp))
                    LpmsStatusChip(text = stringResource(R.string.chip_low_stock))
                }
                if (!drug.active) {
                    Spacer(Modifier.height(4.dp))
                    LpmsStatusChip(text = stringResource(R.string.chip_inactive))
                }
            }
        }
    }
}
