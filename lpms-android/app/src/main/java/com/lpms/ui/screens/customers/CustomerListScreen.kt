package com.lpms.ui.screens.customers

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
import com.lpms.data.local.CustomerEntity
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsStatusChip
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money
import com.lpms.ui.navigation.Routes

/**
 * The customer list — everyone the pharmacy has sold to or recorded a debt for.
 *
 * A "customer" here is a buyer the owner registered, not an app user: it exists
 * so a sale can be attributed to a name and so a debt can be tracked.
 */
@Composable
fun CustomerListScreen(
    onNavigate: (String) -> Unit,
    viewModel: CustomerListViewModel = hiltViewModel(),
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.customers_title)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate(Routes.newCustomer()) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.customer_new))
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
                placeholder = { Text(stringResource(R.string.customers_search_hint)) },
                singleLine = true,
            )

            val filtered = remember(customers, search) {
                val term = search.trim()
                if (term.isEmpty()) customers else customers.filter {
                    it.name.contains(term, ignoreCase = true) ||
                        it.phone?.contains(term, ignoreCase = true) == true
                }
            }

            if (filtered.isEmpty()) {
                LpmsEmptyState(
                    title = stringResource(R.string.customers_empty),
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
                    items(filtered, key = { it.localId }) { customer ->
                        CustomerCard(
                            customer = customer,
                            onClick = { onNavigate(Routes.customerDetail(customer.localId)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCard(
    customer: CustomerEntity,
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
                    text = customer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (customer.phone != null) {
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val debt = customer.totalDebt ?: 0.0
                if (debt > 0) {
                    Text(
                        text = money(debt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    LpmsStatusChip(
                        text = stringResource(R.string.customer_in_debt),
                        container = MaterialTheme.colorScheme.errorContainer,
                        content = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    LpmsStatusChip(text = stringResource(R.string.customer_settled))
                }
            }
        }
    }
}
