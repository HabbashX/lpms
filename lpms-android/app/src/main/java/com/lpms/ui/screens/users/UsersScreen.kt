package com.lpms.ui.screens.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.lpms.data.model.Role
import com.lpms.data.remote.dto.UserResponse
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsEmptyState
import com.lpms.ui.components.LpmsStatusChip
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.shortDate

/**
 * User management (ADMIN only).
 *
 * The app is used by the pharmacy owner and staff — never by customers. These
 * are the accounts that can sign in.
 */
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val users by viewModel.users.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.users_title), onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: create user */ }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.user_new))
            }
        },
    ) { padding ->
        if (users.isEmpty()) {
            LpmsEmptyState(
                title = stringResource(R.string.users_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    UserCard(user = user)
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: UserResponse) {
    LpmsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (user.lastLoginAt != null) {
                    Text(
                        text = "${stringResource(R.string.user_last_login)}: ${shortDate(com.lpms.data.local.parseIsoToMillis(user.lastLoginAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LpmsStatusChip(
                text = if (user.enabled) {
                    stringResource(R.string.user_enabled_status)
                } else {
                    stringResource(R.string.user_disabled_status)
                },
                container = if (user.enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                content = if (user.enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
