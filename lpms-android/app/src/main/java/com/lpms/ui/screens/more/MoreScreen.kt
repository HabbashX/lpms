package com.lpms.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lpms.R
import com.lpms.data.model.Role
import com.lpms.ui.components.LpmsCard
import com.lpms.ui.components.LpmsSectionHeader
import com.lpms.ui.components.LpmsTopBar

/**
 * The "More" hub: everything that is not a bottom-bar destination.
 *
 * Items the signed-in role cannot use are hidden rather than shown disabled,
 * so the owner only ever sees actions they can actually take.
 */
@Composable
fun MoreScreen(
    onNavigate: (String) -> Unit,
    role: Role = Role.EMPLOYEE,
    onSignOut: () -> Unit = {},
) {
    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.more_title)) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(title = stringResource(R.string.more_section_operations)) {
                MoreItem(
                    icon = Icons.Outlined.ReceiptLong,
                    label = stringResource(R.string.more_sales_history),
                    onClick = { onNavigate("sales") },
                )
                MoreItem(
                    icon = Icons.Outlined.Inventory,
                    label = stringResource(R.string.more_inventory),
                    onClick = { onNavigate("inventory") },
                )
            }

            Section(title = stringResource(R.string.more_section_reports)) {
                MoreItem(
                    icon = Icons.Outlined.Assessment,
                    label = stringResource(R.string.more_reports),
                    onClick = { onNavigate("reports") },
                )
            }

            if (role == Role.ADMIN) {
                Section(title = stringResource(R.string.more_section_administration)) {
                    MoreItem(
                        icon = Icons.Outlined.PeopleAlt,
                        label = stringResource(R.string.more_users),
                        onClick = { onNavigate("users") },
                    )
                    MoreItem(
                        icon = Icons.Outlined.Assessment,
                        label = stringResource(R.string.more_audit),
                        onClick = { onNavigate("audit") },
                    )
                    MoreItem(
                        icon = Icons.Outlined.Settings,
                        label = stringResource(R.string.more_settings),
                        onClick = { onNavigate("settings") },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            MoreItem(
                icon = Icons.Outlined.Logout,
                label = stringResource(R.string.action_sign_out),
                onClick = onSignOut,
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        LpmsSectionHeader(title = title)
        LpmsCard {
            content()
        }
    }
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
