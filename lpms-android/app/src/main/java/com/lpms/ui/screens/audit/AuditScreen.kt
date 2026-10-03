package com.lpms.ui.screens.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.remote.dto.AuditLogResponse
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.shortDate

/**
 * The audit log (ADMIN only): who did what, when.
 *
 * Fetched over the network. Filterable by action and user.
 */
@Composable
fun AuditScreen(
    onBack: () -> Unit,
    viewModel: AuditViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.audit_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            var actionFilter by remember { mutableStateOf("") }

            OutlinedTextField(
                value = actionFilter,
                onValueChange = { actionFilter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.audit_filter_action)) },
                singleLine = true,
            )

            val filtered = remember(entries, actionFilter) {
                val term = actionFilter.trim()
                if (term.isEmpty()) entries else entries.filter {
                    it.action.name.contains(term, ignoreCase = true)
                }
            }

            if (filtered.isEmpty()) {
                LpmsEmptyState(
                    title = stringResource(R.string.audit_empty),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        AuditCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditCard(entry: AuditLogResponse) {
    LpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.action.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = entry.username ?: stringResource(R.string.audit_system),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.description != null) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = shortDate(com.lpms.data.local.parseIsoToMillis(entry.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
