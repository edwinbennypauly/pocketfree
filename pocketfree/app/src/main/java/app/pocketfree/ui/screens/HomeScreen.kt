package app.pocketfree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.data.repo.SettingsRepository
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Calculations
import app.pocketfree.domain.Category
import app.pocketfree.domain.CategoryTotal
import app.pocketfree.domain.DateRange
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.Summary
import app.pocketfree.domain.Transaction
import app.pocketfree.domain.TransactionType
import app.pocketfree.ui.components.*
import app.pocketfree.ui.theme.ExpenseRed
import app.pocketfree.ui.theme.IncomeGreen
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

data class HomeUi(
    val month: YearMonth,
    val currency: AppCurrency,
    val balanceMinor: Long,
    val summary: Summary,
    val breakdown: List<CategoryTotal>,
    val categories: Map<Long, Category>,
    val daily: List<Long>,
    val recent: List<Transaction>,
    val hasTransactions: Boolean,
    val skipped: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repo: LedgerRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val ui: StateFlow<HomeUi?> = combine(repo.snapshot, month) { snap, m -> build(snap, m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hideAmounts: StateFlow<Boolean> = settings.hideAmounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }
    fun toggleHide() { viewModelScope.launch { settings.setHideAmounts(!hideAmounts.value) } }

    private fun build(snap: LedgerSnapshot, m: YearMonth): HomeUi {
        val range = DateRange.ofMonth(m)
        val overall = Calculations.balanceUpTo(snap.transactions, m.atEndOfMonth(), snap.rates)
        val monthly = Calculations.summarize(snap.transactions, range, snap.rates)
        return HomeUi(
            month = m,
            currency = snap.defaultCurrency,
            balanceMinor = overall.netMinor,
            summary = monthly,
            breakdown = Calculations.categoryTotals(snap.transactions, range, TransactionType.EXPENSE, snap.rates),
            categories = snap.categoryMap,
            daily = Calculations.dailyExpenses(snap.transactions, range, snap.rates).map { it.second },
            recent = snap.transactions.take(5),
            hasTransactions = snap.transactions.isNotEmpty(),
            skipped = overall.skipped
        )
    }
}

@Composable
fun HomeScreen(
    onAdd: (TransactionType) -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hide by viewModel.hideAmounts.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "PocketFree",
        actions = {
            IconButton(onClick = viewModel::toggleHide) {
                Icon(
                    if (hide) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (hide) "Show amounts" else "Hide amounts"
                )
            }
        }
    ) { padding ->
        val state = ui
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { MonthSwitcher(state.month, viewModel::previousMonth, viewModel::nextMonth) }
                item { BalanceCard(state) }
                item { SummaryRow(state) }
                if (state.skipped > 0) {
                    item {
                        Text(
                            "${state.skipped} transaction(s) in other currencies are not counted because no exchange rate is set (Settings > Exchange rates).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                item { BreakdownCard(state) }
                item { TrendCard(state) }
                item { QuickActions(onAdd, onOpenTransactions) }
                item { SectionTitle("Recent transactions") }
                item {
                    AppCard {
                        if (state.recent.isEmpty()) {
                            EmptyState("No transactions yet", "Tap + to record your first expense or income.")
                        } else {
                            Column {
                                state.recent.forEach { t ->
                                    TransactionRow(t, state.categories[t.categoryId], onClick = { onOpenTransaction(t.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(ui: HomeUi) {
    val scheme = MaterialTheme.colorScheme
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, lerp(scheme.primaryContainer, scheme.primary, 0.5f))))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Total balance", style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer)
            Text(
                moneyText(ui.balanceMinor, ui.currency.code),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer
            )
            val isCurrent = ui.month == YearMonth.now()
            Text(
                if (isCurrent) "All income minus all expenses so far" else "Balance at the end of ${formatMonth(ui.month)}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SummaryRow(ui: HomeUi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat("Income", ui.summary.incomeMinor, ui.currency, IncomeGreen, Modifier.weight(1f))
        MiniStat("Expenses", ui.summary.expenseMinor, ui.currency, ExpenseRed, Modifier.weight(1f))
        MiniStat("Savings", ui.summary.netMinor, ui.currency, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
    }
}

@Composable
private fun MiniStat(label: String, minor: Long, currency: AppCurrency, color: Color, modifier: Modifier) {
    AppCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                moneyText(minor, currency.code),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BreakdownCard(ui: HomeUi) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Where your money went")
            if (ui.breakdown.isEmpty()) {
                Text("No expenses in ${formatMonth(ui.month)}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val top = ui.breakdown.take(5)
                val otherTotal = ui.breakdown.drop(5).sumOf { it.totalMinor }
                val otherColor = MaterialTheme.colorScheme.outline
                val slices = top.map { DonutSlice(it.totalMinor, colorFor(ui.categories[it.categoryId])) } +
                    if (otherTotal > 0) listOf(DonutSlice(otherTotal, otherColor)) else emptyList()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DonutChart(slices = slices, modifier = Modifier.size(132.dp), strokeWidth = 20.dp) {
                        Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        top.forEach { total ->
                            val category = ui.categories[total.categoryId]
                            LegendRow(colorFor(category), category?.name ?: "Deleted category", moneyText(total.totalMinor, ui.currency.code))
                        }
                        if (otherTotal > 0) LegendRow(otherColor, "Other categories", moneyText(otherTotal, ui.currency.code))
                    }
                }
            }
        }
    }
}

private fun colorFor(category: Category?): Color =
    if (category != null) Color(category.colorArgb) else Color(0xFF94A3B8)

@Composable
fun LegendRow(color: Color, name: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Text(name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrendCard(ui: HomeUi) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Daily spending")
            if (ui.summary.expenseMinor == 0L) {
                Text("Nothing spent yet this month.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChart(values = ui.daily, modifier = Modifier.fillMaxWidth().height(110.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${ui.daily.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onAdd: (TransactionType) -> Unit, onOpenTransactions: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onAdd(TransactionType.EXPENSE) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Rounded.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Expense", maxLines = 1)
        }
        FilledTonalButton(onClick = { onAdd(TransactionType.INCOME) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Income", maxLines = 1)
        }
        OutlinedButton(onClick = onOpenTransactions, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("All", maxLines = 1)
        }
    }
}
