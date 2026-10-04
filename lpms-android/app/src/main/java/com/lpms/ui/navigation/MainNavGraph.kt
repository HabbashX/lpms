package com.lpms.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lpms.R
import com.lpms.data.model.AuthUser
import com.lpms.ui.screens.audit.AuditScreen
import com.lpms.ui.screens.customers.CustomerDetailScreen
import com.lpms.ui.screens.customers.CustomerEditScreen
import com.lpms.ui.screens.customers.CustomerListScreen
import com.lpms.ui.screens.drugs.DrugEditScreen
import com.lpms.ui.screens.drugs.DrugListScreen
import com.lpms.ui.screens.home.HomeScreen
import com.lpms.ui.screens.inventory.InventoryScreen
import com.lpms.ui.screens.more.MoreScreen
import com.lpms.ui.screens.pos.PosScreen
import com.lpms.ui.screens.reports.ReportsScreen
import com.lpms.ui.screens.sales.SaleDetailScreen
import com.lpms.ui.screens.sales.SalesScreen
import com.lpms.ui.screens.settings.SettingsScreen
import com.lpms.ui.screens.users.UsersScreen

/**
 * The signed-in shell: one [NavHost] plus a five-item bottom bar.
 *
 * Only the five top-level destinations show the bar; pushed screens (editors,
 * detail views) hide it, so a form never has its primary button covered by
 * navigation chrome.
 */
@Composable
fun MainNavGraph(
    user: AuthUser,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit = {},
) {
    val navController = rememberNavController()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BOTTOM_BAR_ROUTES

    val navigateBottomBar: (String) -> Unit = { navController.navigateTo(it, isBottomBar = true) }
    val navigateInternal: (String) -> Unit = { navController.navigateTo(it) }
    val back: () -> Unit = { navController.popBackStack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                LpmsBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateBottomBar,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Home,
            modifier = Modifier.padding(padding),
        ) {

            composable(Routes.Home) {
                HomeScreen(
                    user = user,
                    onNavigate = navigateInternal,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.More) {
                MoreScreen(
                    onNavigate = navigateInternal,
                    role = user.role,
                    onSignOut = onSignOut,
                )
            }

            composable(Routes.Pos) { PosScreen(onNavigate = navigateInternal, onBack = back) }
            composable(Routes.Drugs) { DrugListScreen(onNavigate = navigateInternal) }
            composable(Routes.Customers) { CustomerListScreen(onNavigate = navigateInternal) }

            composable(Routes.Sales) { SalesScreen(onNavigate = navigateInternal, onBack = back) }
            composable(
                route = Routes.SaleDetail,
                arguments = listOf(navArgument(Routes.ArgSaleId) {
                    type = androidx.navigation.NavType.StringType
                }),
            ) { entry ->
                SaleDetailScreen(
                    saleId = entry.arguments?.getString(Routes.ArgSaleId) ?: "",
                    onNavigate = navigateInternal,
                    onBack = back,
                )
            }

            composable(
                route = Routes.DrugEdit,
                arguments = listOf(navArgument(Routes.ArgDrugId) {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = Routes.NewDrugId
                }),
            ) { entry ->
                DrugEditScreen(
                    drugId = entry.arguments?.getString(Routes.ArgDrugId) ?: Routes.NewDrugId,
                    onBack = back,
                )
            }

            composable(
                route = Routes.CustomerDetail,
                arguments = listOf(navArgument(Routes.ArgCustomerId) {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = Routes.NewCustomerId
                }),
            ) { entry ->
                val customerId = entry.arguments?.getString(Routes.ArgCustomerId)
                    ?: Routes.NewCustomerId
                if (customerId == Routes.NewCustomerId) {
                    CustomerEditScreen(customerId = customerId, onBack = back)
                } else {
                    CustomerDetailScreen(
                        customerId = customerId,
                        onNavigate = navigateInternal,
                        onBack = back,
                    )
                }
            }

            composable(Routes.Inventory) { InventoryScreen(onNavigate = navigateInternal, onBack = back) }
            composable(Routes.Reports) { ReportsScreen(onBack = back) }
            composable(Routes.Users) { UsersScreen(onBack = back) }
            composable(Routes.Audit) { AuditScreen(onBack = back) }
            composable(Routes.Settings) { SettingsScreen(onBack = back) }
        }
    }
}

private fun NavHostController.navigateTo(route: String, isBottomBar: Boolean = false) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        if (isBottomBar) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
        }
    }
}

private data class BottomItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val BOTTOM_ITEMS = listOf(
    BottomItem(Routes.Home, R.string.nav_home, Icons.Outlined.Home),
    BottomItem(Routes.Pos, R.string.nav_sell, Icons.Outlined.PointOfSale),
    BottomItem(Routes.Drugs, R.string.nav_drugs, Icons.Outlined.Medication),
    BottomItem(Routes.Customers, R.string.nav_customers, Icons.Outlined.PeopleAlt),
    BottomItem(Routes.More, R.string.nav_more, Icons.Outlined.MoreHoriz),
)

private val BOTTOM_BAR_ROUTES = BOTTOM_ITEMS.map { it.route }.toSet()

@Composable
private fun LpmsBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        BOTTOM_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) },
            )
        }
    }
}
