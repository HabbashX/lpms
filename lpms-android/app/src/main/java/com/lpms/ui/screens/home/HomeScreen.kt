package com.lpms.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lpms.R
import com.lpms.data.model.AuthUser
import com.lpms.data.model.Role
import com.lpms.data.remote.dto.DashboardResponse
import com.lpms.ui.components.LpmsIconAction
import com.lpms.ui.components.LpmsSectionHeader
import com.lpms.ui.components.LpmsStatCard
import com.lpms.ui.components.LpmsTopBar
import com.lpms.ui.components.money

/**
 * The dashboard: today's and this month's figures, inventory health, and the
 * quick actions the owner uses most.
 *
 * The dashboard is a server aggregate, so it needs a connection. While offline
 * the screen shows the last cached figures rather than an error, because the
 * owner still wants to see yesterday's numbers.
 */
@Composable
fun HomeScreen(
    user: AuthUser,
    onNavigate: (String) -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { LpmsTopBar(title = stringResource(R.string.home_title)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.home_signed_in_as, user.username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (dashboard != null) {
                item {
                    DashboardStats(dashboard = dashboard!!)
                }
                item {
                    TopSellingSection(dashboard = dashboard!!, onNavigate = onNavigate)
                }
            } else {
                item {
                    com.lpms.ui.components.LpmsLoading()
                }
            }

            item {
                QuickActions(user = user, onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun DashboardStats(dashboard: DashboardResponse) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.dashboard_today),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LpmsStatCard(
                label = stringResource(R.string.stat_revenue),
                value = money(dashboard.today.revenue),
                modifier = Modifier.weight(1f),
            )
            LpmsStatCard(
                label = stringResource(R.string.stat_profit),
                value = money(dashboard.today.profit),
                modifier = Modifier.weight(1f),
            )
            LpmsStatCard(
                label = stringResource(R.string.stat_sales),
                value = dashboard.today.sales.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.dashboard_inventory),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LpmsStatCard(
                label = stringResource(R.string.stat_inventory_value),
                value = money(dashboard.inventory.value),
                modifier = Modifier.weight(1f),
            )
            LpmsStatCard(
                label = stringResource(R.string.stat_low_stock),
                value = dashboard.inventory.lowStock.toString(),
                modifier = Modifier.weight(1f),
            )
            LpmsStatCard(
                label = stringResource(R.string.stat_total_debt),
                value = money(dashboard.customers.totalDebt),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TopSellingSection(
    dashboard: DashboardResponse,
    onNavigate: (String) -> Unit,
) {
    Column {
        LpmsSectionHeader(
            title = stringResource(R.string.dashboard_top_selling),
            trailing = {
                com.lpms.ui.components.LpmsTextAction(
                    text = stringResource(R.string.action_view_all),
                    onClick = { onNavigate("drugs") },
                )
            },
        )
        dashboard.topSellingDrugs.take(5).forEach { drug ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = drug.drugName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.dashboard_units_sold, drug.quantity.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    user: AuthUser,
    onNavigate: (String) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.home_quick_actions),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                LpmsIconAction(
                    icon = Icons.Outlined.PointOfSale,
                    label = stringResource(R.string.nav_sell),
                    onClick = { onNavigate("pos") },
                )
            }
            item {
                LpmsIconAction(
                    icon = Icons.Outlined.Inventory,
                    label = stringResource(R.string.nav_drugs),
                    onClick = { onNavigate("drugs") },
                )
            }
            item {
                LpmsIconAction(
                    icon = Icons.Outlined.PeopleAlt,
                    label = stringResource(R.string.nav_customers),
                    onClick = { onNavigate("customers") },
                )
            }
            item {
                LpmsIconAction(
                    icon = Icons.Outlined.Assessment,
                    label = stringResource(R.string.more_reports),
                    onClick = { onNavigate("reports") },
                )
            }
            item {
                LpmsIconAction(
                    icon = Icons.Outlined.Settings,
                    label = stringResource(R.string.more_settings),
                    onClick = { onNavigate("settings") },
                    tint = if (user.role == Role.ADMIN) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
