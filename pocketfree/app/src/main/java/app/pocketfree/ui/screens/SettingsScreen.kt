package app.pocketfree.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.SettingsRepository
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    val currency: StateFlow<AppCurrency> = settings.defaultCurrency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppCurrency.AED)

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setCurrency(currency: AppCurrency) { viewModelScope.launch { settings.setDefaultCurrency(currency) } }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        ThemeMode.entries.forEach { mode ->
            RadioRow(
                label = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                selected = theme == mode,
                onSelect = { viewModel.setTheme(mode) }
            )
        }

        Text("Default currency", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        AppCurrency.entries.forEach { option ->
            RadioRow(
                label = "${option.code} · ${option.displayName}",
                selected = currency == option,
                onSelect = { viewModel.setCurrency(option) }
            )
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
    }
}
