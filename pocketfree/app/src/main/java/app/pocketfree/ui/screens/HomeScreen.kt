package app.pocketfree.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.db.CategoryDao
import app.pocketfree.data.db.TransactionDao
import app.pocketfree.data.repo.SettingsRepository
import app.pocketfree.domain.AppCurrency
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    categoryDao: CategoryDao,
    transactionDao: TransactionDao,
    settings: SettingsRepository
) : ViewModel() {

    data class FoundationState(
        val categories: Int = 0,
        val transactions: Int = 0,
        val currency: AppCurrency = AppCurrency.AED
    )

    val state: StateFlow<FoundationState> = combine(
        categoryDao.observeCount(),
        transactionDao.observeCount(),
        settings.defaultCurrency
    ) { categories, transactions, currency ->
        FoundationState(categories, transactions, currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoundationState())
}

/** Phase 2 stand-in: proves the database and settings are wired. The real dashboard replaces this in Phase 3. */
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Home", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Foundation check", style = MaterialTheme.typography.titleMedium)
                Text("Categories in database: ${state.categories}")
                Text("Transactions in database: ${state.transactions}")
                Text("Default currency: ${state.currency.code}")
            }
        }
        Text(
            "The dashboard arrives in Phase 3.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
