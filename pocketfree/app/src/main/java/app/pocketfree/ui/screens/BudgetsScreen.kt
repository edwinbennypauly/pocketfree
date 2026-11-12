package app.pocketfree.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.BudgetCalc
import app.pocketfree.domain.BudgetLevel
import app.pocketfree.domain.BudgetStatus
import app.pocketfree.domain.Calculations
import app.pocketfree.domain.Category
import app.pocketfree.domain.DateRange
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.Money
import app.pocketfree.domain.OVERALL_BUDGET_ID
import app.pocketfree.domain.TransactionType
import app.pocketfree.ui.CollectEvents
import app.pocketfree.ui.Events
import app.pocketfree.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetRow(val category: Category, val status: BudgetStatus?, val spentMinor: Long)

data class BudgetsUi(
    val month: YearMonth,
    val currency: AppCurrency,
    val overall: BudgetStatus?,
    val overallSpentMinor: Long,
    val rows: List<BudgetRow>
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(private val repo: LedgerRepository) : ViewModel() {
    val events = Events()
    private val month = MutableStateFlow(YearMonth.now())

    val ui: StateFlow<BudgetsUi?> = combine(repo.snapshot, month) { snap, m -> build(snap, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }

    /** [text] blank or 0 clears the budget. Returns true when saved. */
    fun setBudget(categoryId: Long, text: String, onSaved: () -> Unit) {
        val minor = if (text.isBlank()) 0L else Money.parseToMinor(text)
        if (minor == null) {
            events.send("Enter a valid amount, for example 3000 or 450.50")
            return
        }
        viewModelScope.launch {
            val currency = ui.value?.currency ?: AppCurrency.AED
            val error = repo.setBudget(month.value, categoryId, minor, currency.code)
            if (error != null) events.send(error) else {
                events.send(if (minor == 0L) "Budget cleared" else "Budget saved")
                onSaved()
            }
        }
    }

    private fun build(snap: LedgerSnapshot, m: YearMonth): BudgetsUi {
        val effective = BudgetCalc.effective(snap.budgets, m)
        val spent = BudgetCalc.spentByCategory(snap.transactions, m, snap.rates)
        val totalSpent = Calculations.summarize(snap.transactions, DateRange.ofMonth(m), snap.rates).expenseMinor

        fun statusFor(categoryId: Long, spentMinor: Long): BudgetStatus? {
            val b = effective[categoryId] ?: return null
            val limit = snap.rates.convert(b.limitMinor, b.currencyCode) ?: return null
            return BudgetStatus(limit, spentMinor)
        }

        val rows = snap.categories
            .filter { it.type == TransactionType.EXPENSE && (!it.isArchived || effective.containsKey(it.id)) }
            .map { c -> BudgetRow(c, statusFor(c.id, spent[c.id] ?: 0L), spent[c.id] ?: 0L) }
        return BudgetsUi(m, snap.defaultCurrency, statusFor(OVERALL_BUDGET_ID, totalSpent), totalSpent, rows)
    }
}

@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEvents(viewModel.events.flow, snackbar)
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) } // categoryId to title

    ScreenScaffold(title = "Budgets", snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val state = ui
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { MonthSwitcher(state.month, viewModel::previousMonth, viewModel::nextMonth) }
            item {
                OverallBudgetCard(state) { editing = OVERALL_BUDGET_ID to "Monthly budget" }
            }
            item {
                Text(
                    "A budget applies to this month and every later month until you change it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item { SectionTitle("Category budgets") }
            items(state.rows, key = { it.category.id }) { row ->
                CategoryBudgetCard(row, state.currency) { editing = row.category.id to row.category.name }
            }
        }

        val target = editing
        if (target != null) {
            val currentStatus = if (target.first == OVERALL_BUDGET_ID) state.overall
            else state.rows.firstOrNull { it.category.id == target.first }?.status
            BudgetDialog(
                title = target.second,
                currency = state.currency,
                initial = currentStatus?.let { Money.toDecimal(it.limitMinor).toPlainString() } ?: "",
                hasBudget = currentStatus != null,
                onSave = { text -> viewModel.setBudget(target.first, text) { editing = null } },
                onClear = { viewModel.setBudget(target.first, "") { editing = null } },
                onDismiss = { editing = null }
            )
        }
    }
}

@Composable
private fun OverallBudgetCard(state: BudgetsUi, onEdit: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = state.overall
            Text("Monthly budget", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status == null) {
                Text("No monthly budget set", style = MaterialTheme.typography.titleMedium)
                Text("Spent so far: ${moneyText(state.overallSpentMinor, state.currency.code)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onEdit) { Text("Set monthly budget") }
            } else {
                Text(
                    "${moneyText(status.spentMinor, state.currency.code)} of ${moneyText(status.limitMinor, state.currency.code)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                BudgetBar(status.percentUsed, status.level)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (status.remainingMinor >= 0) "Remaining ${moneyText(status.remainingMinor, state.currency.code)}"
                        else "Over by ${moneyText(-status.remainingMinor, state.currency.code)}",
                        color = levelColor(status.level, MaterialTheme.colorScheme.onSurface)
                    )
                    Text("${status.percentUsed}% used", color = levelColor(status.level, MaterialTheme.colorScheme.onSurface))
                }
                when (status.level) {
                    BudgetLevel.OVER -> Text("You have gone over your budget.", color = levelColor(status.level, MaterialTheme.colorScheme.onSurface), style = MaterialTheme.typography.bodySmall)
                    BudgetLevel.WARNING -> Text("You are close to your limit.", color = levelColor(status.level, MaterialTheme.colorScheme.onSurface), style = MaterialTheme.typography.bodySmall)
                    BudgetLevel.OK -> {}
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetCard(row: BudgetRow, currency: AppCurrency, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(row.category, size = 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.category.name, style = MaterialTheme.typography.bodyLarge)
                    val status = row.status
                    Text(
                        if (status == null) "Spent ${moneyText(row.spentMinor, currency.code)} • No budget"
                        else "${moneyText(status.spentMinor, currency.code)} of ${moneyText(status.limitMinor, currency.code)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val status2 = row.status
                if (status2 != null) {
                    Text("${status2.percentUsed}%", fontWeight = FontWeight.SemiBold, color = levelColor(status2.level, MaterialTheme.colorScheme.onSurface))
                } else {
                    Text("Set", color = MaterialTheme.colorScheme.primary)
                }
            }
            val status3 = row.status
            if (status3 != null) {
                BudgetBar(status3.percentUsed, status3.level)
                if (status3.level != BudgetLevel.OK) {
                    Text(
                        if (status3.level == BudgetLevel.OVER) "Over by ${moneyText(-status3.remainingMinor, currency.code)}" else "Close to the limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = levelColor(status3.level, MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetDialog(
    title: String,
    currency: AppCurrency,
    initial: String,
    hasBudget: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget: $title") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(14) },
                label = { Text("Monthly limit (${currency.code})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (hasBudget) TextButton(onClick = onClear) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
