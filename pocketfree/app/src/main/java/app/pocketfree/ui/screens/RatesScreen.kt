package app.pocketfree.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.RateTable
import app.pocketfree.ui.CollectEvents
import app.pocketfree.ui.Events
import app.pocketfree.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RateEntry(val currency: AppCurrency, val direct: String?, val inverse: String?)
data class RatesUi(val base: AppCurrency, val entries: List<RateEntry>)

@HiltViewModel
class RatesViewModel @Inject constructor(private val repo: LedgerRepository) : ViewModel() {
    val events = Events()

    val ui: StateFlow<RatesUi?> = repo.snapshot.map { snap ->
        val base = snap.defaultCurrency
        RatesUi(
            base,
            AppCurrency.entries.filter { it != base }.map {
                RateEntry(it, snap.rates.rateOrNull(it.code, base.code)?.toPlainString(), snap.rates.rateOrNull(base.code, it.code)?.toPlainString())
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(from: AppCurrency, to: AppCurrency, text: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            val error = repo.setRate(from.code, to.code, text)
            if (error != null) events.send(error) else { events.send("Rate saved"); onSaved() }
        }
    }

    fun clear(from: AppCurrency, to: AppCurrency, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.clearRate(from.code, to.code)
            events.send("Rate removed")
            onDone()
        }
    }
}

@Composable
fun RatesScreen(onBack: () -> Unit, viewModel: RatesViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEvents(viewModel.events.flow, snackbar)
    var editing by remember { mutableStateOf<RateEntry?>(null) }

    ScreenScaffold(title = "Exchange rates", onBack = onBack, bottomInset = true, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val state = ui
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(
                    "PocketFree never downloads rates and never guesses. Enter the rate you want to use for each currency you spend in; transactions without a rate are left out of totals and flagged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.entries, key = { it.currency.code }) { entry ->
                AppCard(Modifier.clickable { editing = entry }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${entry.currency.code} · ${entry.currency.displayName}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    entry.direct != null -> "1 ${entry.currency.code} = ${entry.direct} ${state.base.code}"
                                    entry.inverse != null -> "1 ${state.base.code} = ${entry.inverse} ${entry.currency.code}"
                                    else -> "No rate set"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.direct == null && entry.inverse == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Edit", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        val entry = editing
        if (entry != null) {
            var text by remember(entry) { mutableStateOf(entry.direct ?: "") }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text("1 ${entry.currency.code} = ? ${state.base.code}") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(14) },
                        label = { Text("Rate in ${state.base.code}") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                },
                confirmButton = { TextButton(onClick = { viewModel.save(entry.currency, state.base, text) { editing = null } }) { Text("Save") } },
                dismissButton = {
                    Row {
                        if (entry.direct != null || entry.inverse != null) {
                            TextButton(onClick = { viewModel.clear(entry.currency, state.base) { editing = null } }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                        TextButton(onClick = { editing = null }) { Text("Cancel") }
                    }
                }
            )
        }
    }
}
