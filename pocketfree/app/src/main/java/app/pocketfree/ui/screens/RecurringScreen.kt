package app.pocketfree.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.data.repo.SaveOutcome
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Category
import app.pocketfree.domain.Frequency
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.Money
import app.pocketfree.domain.Recurring
import app.pocketfree.domain.RecurringRule
import app.pocketfree.domain.TransactionType
import app.pocketfree.ui.CollectEvents
import app.pocketfree.ui.Events
import app.pocketfree.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringUi(val rules: List<RecurringRule>, val categories: List<Category>, val currency: AppCurrency)

@HiltViewModel
class RecurringViewModel @Inject constructor(private val repo: LedgerRepository) : ViewModel() {
    val events = Events()

    val ui: StateFlow<RecurringUi?> = repo.snapshot.map { s: LedgerSnapshot -> RecurringUi(s.recurring, s.categories, s.defaultCurrency) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** [onResult] gets an error message, or null when saved. */
    fun add(
        type: TransactionType, amountText: String, currency: AppCurrency, categoryId: Long?, description: String,
        frequency: Frequency, start: LocalDate, end: LocalDate?, onResult: (String?) -> Unit
    ) {
        val minor = Money.parseToMinor(amountText, currency.decimals)
        if (minor == null || minor <= 0) { onResult("Enter an amount greater than zero."); return }
        if (categoryId == null) { onResult("Choose a category."); return }
        viewModelScope.launch {
            when (val outcome = repo.addRecurring(type, minor, currency.code, categoryId, description, "", frequency, start, end)) {
                is SaveOutcome.Invalid -> onResult(outcome.errors.joinToString(" "))
                SaveOutcome.Saved -> {
                    val created = repo.runRecurring(LocalDate.now())
                    events.send(if (created > 0) "Saved. $created past occurrence(s) were added." else "Recurring transaction saved")
                    onResult(null)
                }
            }
        }
    }

    fun setActive(id: Long, active: Boolean) { viewModelScope.launch { repo.setRecurringActive(id, active) } }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.deleteRecurring(id)
            events.send("Stopped. Transactions already created were kept.")
        }
    }
}

@Composable
fun RecurringScreen(onBack: () -> Unit, viewModel: RecurringViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEvents(viewModel.events.flow, snackbar)
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecurringRule?>(null) }

    ScreenScaffold(
        title = "Recurring",
        onBack = onBack,
        bottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = { IconButton(onClick = { showEditor = true }) { Icon(Icons.Rounded.Add, contentDescription = "Add recurring") } }
    ) { padding ->
        val state = ui
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@ScreenScaffold
        }
        if (state.rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState("No recurring transactions", "Add salary, rent or subscriptions once and they are recorded automatically on their due dates.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rules, key = { it.id }) { rule ->
                    val category = state.categories.firstOrNull { it.id == rule.categoryId }
                    AppCard {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryAvatar(category, size = 36.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(rule.description.ifBlank { category?.name ?: "Recurring" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    "${moneyText(rule.amountMinor, rule.currencyCode)} • ${rule.frequency.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val next = Recurring.nextDate(rule)
                                Text(
                                    when {
                                        !rule.isActive -> "Paused"
                                        next == null -> "Finished"
                                        else -> "Next: ${formatDate(next)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = rule.isActive, onCheckedChange = { viewModel.setActive(rule.id, it) })
                            IconButton(onClick = { deleting = rule }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        if (showEditor) {
            RecurringEditor(state, viewModel, onDismiss = { showEditor = false })
        }
        val toDelete = deleting
        if (toDelete != null) {
            ConfirmDialog(
                title = "Stop this recurring transaction?",
                message = "No new entries will be created. Transactions it already created stay in your history.",
                confirmLabel = "Stop",
                onConfirm = { viewModel.delete(toDelete.id); deleting = null },
                onDismiss = { deleting = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringEditor(state: RecurringUi, viewModel: RecurringViewModel, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(state.currency) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var description by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(0) } // 1 = start, 2 = end
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New recurring transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.entries.forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option; categoryId = null }, label = { Text(option.chipLabel) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.take(14); error = null },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    ChipDropdown(label = currency.code, selectedText = currency.code, options = AppCurrency.entries.map { it.code to it }, onSelect = { currency = it })
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.filter { it.type == type && !it.isArchived }.forEach { c ->
                        FilterChip(selected = categoryId == c.id, onClick = { categoryId = c.id; error = null }, label = { Text(c.name) })
                    }
                }
                OutlinedTextField(value = description, onValueChange = { description = it.take(200) }, label = { Text("Description (optional)") }, singleLine = true)
                ChipDropdown(
                    label = "Repeats",
                    selectedText = frequency.name.lowercase().replaceFirstChar { it.uppercase() },
                    options = Frequency.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it },
                    onSelect = { frequency = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = 1 }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Starts ${formatDate(start)}", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = 2 }) { Text(end?.let { "Ends ${formatDate(it)}" } ?: "No end date") }
                    if (end != null) TextButton(onClick = { end = null }) { Text("Clear") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.add(type, amount, currency, categoryId, description, frequency, start, end) { message ->
                    if (message == null) onDismiss() else error = message
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (picking != 0) {
        val isStart = picking == 1
        AppDatePickerDialog(
            initial = if (isStart) start else (end ?: start),
            onDismiss = { picking = 0 },
            onPicked = { d -> if (isStart) start = d else end = d }
        )
    }
}
