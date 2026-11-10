package app.pocketfree.ui.nav

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.pocketfree.ui.screens.HomeScreen
import app.pocketfree.ui.screens.PlaceholderScreen
import app.pocketfree.ui.screens.SettingsScreen

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Transactions("transactions", "History", Icons.Rounded.ReceiptLong),
    Budgets("budgets", "Budgets", Icons.Rounded.Savings),
    Analytics("analytics", "Analytics", Icons.Rounded.BarChart),
    Settings("settings", "Settings", Icons.Rounded.Settings)
}

const val ROUTE_ADD = "add"

@Composable
fun PocketFreeRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBars = TopDest.entries.any { it.route == current }

    Scaffold(
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    TopDest.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest.route,
                            onClick = {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label, maxLines = 1) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBars) {
                FloatingActionButton(onClick = { nav.navigate(ROUTE_ADD) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add transaction")
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = TopDest.Home.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(TopDest.Home.route) { HomeScreen() }
            composable(TopDest.Transactions.route) {
                PlaceholderScreen("Transactions", "Transaction history arrives in Phase 3.")
            }
            composable(TopDest.Budgets.route) {
                PlaceholderScreen("Budgets", "Budgets arrive in Phase 4.")
            }
            composable(TopDest.Analytics.route) {
                PlaceholderScreen("Analytics", "Analytics arrive in Phase 4.")
            }
            composable(TopDest.Settings.route) { SettingsScreen() }
            composable(ROUTE_ADD) {
                PlaceholderScreen(
                    title = "Add transaction",
                    note = "The transaction entry screen arrives in Phase 3.",
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
