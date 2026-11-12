package app.pocketfree.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.pocketfree.ui.screens.AddEditScreen
import app.pocketfree.ui.screens.AnalyticsScreen
import app.pocketfree.ui.screens.BackupScreen
import app.pocketfree.ui.screens.BudgetsScreen
import app.pocketfree.ui.screens.CategoriesScreen
import app.pocketfree.ui.screens.HomeScreen
import app.pocketfree.ui.screens.RatesScreen
import app.pocketfree.ui.screens.RecurringScreen
import app.pocketfree.ui.screens.SecurityScreen
import app.pocketfree.ui.screens.SettingsScreen
import app.pocketfree.ui.screens.TransactionsScreen
import app.pocketfree.domain.TransactionType

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "Home", Icons.Rounded.Home),
    Transactions(Routes.TRANSACTIONS, "History", Icons.Rounded.ReceiptLong),
    Budgets(Routes.BUDGETS, "Budgets", Icons.Rounded.Savings),
    Analytics(Routes.ANALYTICS, "Analytics", Icons.Rounded.BarChart),
    Settings(Routes.SETTINGS, "Settings", Icons.Rounded.Settings)
}

@Composable
fun PocketFreeRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBars = TopDest.entries.any { it.route == current }

    fun goTop(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    TopDest.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest.route,
                            onClick = { goTop(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label, maxLines = 1) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBars) {
                FloatingActionButton(onClick = { nav.navigate(Routes.add(TransactionType.EXPENSE.name)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add transaction")
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAdd = { type -> nav.navigate(Routes.add(type.name)) },
                    onOpenTransactions = { goTop(Routes.TRANSACTIONS) },
                    onOpenTransaction = { id -> nav.navigate(Routes.edit(id)) }
                )
            }
            composable(Routes.TRANSACTIONS) { TransactionsScreen(onEdit = { id -> nav.navigate(Routes.edit(id)) }) }
            composable(Routes.BUDGETS) { BudgetsScreen() }
            composable(Routes.ANALYTICS) { AnalyticsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onOpen = { route -> nav.navigate(route) }) }

            composable(
                route = Routes.TX_PATTERN,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("type") { type = NavType.StringType; defaultValue = TransactionType.EXPENSE.name }
                )
            ) { AddEditScreen(onClose = { nav.popBackStack() }) }

            composable(Routes.CATEGORIES) { CategoriesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.RATES) { RatesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.RECURRING) { RecurringScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.BACKUP) { BackupScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SECURITY) { SecurityScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
