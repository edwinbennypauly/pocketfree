package app.pocketfree.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.SettingsRepository
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.ThemeMode
import app.pocketfree.ui.components.*
import app.pocketfree.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val currency: StateFlow<AppCurrency> = settings.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppCurrency.AED)

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setCurrency(currency: AppCurrency) { viewModelScope.launch { settings.setDefaultCurrency(currency) } }
}

@Composable
fun SettingsScreen(onOpen: (String) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Settings") { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsHeader("Appearance")
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = theme == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            SettingsHeader("Default currency")
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipDropdown(
                    label = currency.code,
                    selectedText = "${currency.code} · ${currency.displayName}",
                    options = AppCurrency.entries.map { "${it.code} · ${it.displayName}" to it },
                    onSelect = viewModel::setCurrency
                )
            }
            Text(
                "Totals, budgets and charts are shown in this currency. Transactions in other currencies are converted only with the exchange rates you enter yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            SettingsHeader("Manage")
            SettingsRow(Icons.Rounded.Category, "Categories", "Add, rename, recolor or archive") { onOpen(Routes.CATEGORIES) }
            SettingsRow(Icons.Rounded.CurrencyExchange, "Exchange rates", "Manual rates for other currencies") { onOpen(Routes.RATES) }
            SettingsRow(Icons.Rounded.Repeat, "Recurring transactions", "Salary, rent, subscriptions") { onOpen(Routes.RECURRING) }

            SettingsHeader("Data & privacy")
            SettingsRow(Icons.Rounded.Backup, "Backup & restore", "Export CSV or JSON, import with preview") { onOpen(Routes.BACKUP) }
            SettingsRow(Icons.Rounded.Lock, "Privacy & security", "PIN, fingerprint, hide amounts") { onOpen(Routes.SECURITY) }

            SettingsHeader("About")
            Text(
                "PocketFree 0.2 · Free, no ads, no account. This app has no internet permission: your data stays on this phone unless you export it yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
