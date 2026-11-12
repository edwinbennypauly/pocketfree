package app.pocketfree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Calculations
import app.pocketfree.domain.Category
import app.pocketfree.domain.CategoryComparison
import app.pocketfree.domain.CategoryTotal
import app.pocketfree.domain.Comparison
import app.pocketfree.domain.DateRange
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.MonthTotal
import app.pocketfree.domain.PeriodStats
import app.pocketfree.domain.TransactionType
import app.pocketfree.ui.components.*
import app.pocketfree.ui.theme.ExpenseRed
import app.pocketfree.ui.theme.IncomeGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class RangePreset(val label: String) {
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_3("Last 3 months"),
    LAST_6("Last 6 months"),
    THIS_YEAR("This year"),
    CUSTOM("Custom")
}

data class AnalyticsUi(
    val preset: RangePreset,
    val range: DateRange,
    val currency: AppCurrency,
    val stats: PeriodStats,
    val expenseTotals: List<CategoryTotal>,
    val monthly: List<MonthTotal>,
    val trend: List<Long>,
    val trendIsDaily: Boolean,
    val categories: Map<Long, Category>,
    val compareFrom: YearMonth,
    val compareTo: YearMonth,
    val comparison: Comparison,
    val categoryComparison: List<CategoryComparison>,
    val monthOptions: List<YearMonth>,
    val highestExpenseLabel: String
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(repo: LedgerRepository) : ViewModel() {
    private val preset = MutableStateFlow(RangePreset.THIS_MONTH)
    private val custom = MutableStateFlow(LocalDate.now().withDayOfMonth(1) to LocalDate.now())
    private val compareFrom = MutableStateFlow(YearMonth.now().minusMonths(1))
    private val compareTo = MutableStateFlow(YearMonth.now())

    val ui: StateFlow<AnalyticsUi?> = combine(repo.snapshot, preset, custom, compareFrom, compareTo) { snap, p, c, from, to ->
        build(snap, p, c, from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val customRange: StateFlow<Pair<LocalDate, LocalDate>> = custom

    fun setPreset(p: RangePreset) { preset.value = p }
    fun setCustomFrom(d: LocalDate) { custom.value = d to custom.value.second; preset.value = RangePreset.CUSTOM }
    fun setCustomTo(d: LocalDate) { custom.value = custom.value.first to d; preset.value = RangePreset.CUSTOM }
    fun setCompareFrom(m: YearMonth) { compareFrom.value = m }
    fun setCompareTo(m: YearMonth) { compareTo.value = m }

    private fun build(
        snap: LedgerSnapshot, p: RangePreset, c: Pair<LocalDate, LocalDate>, from: YearMonth, to: YearMonth
    ): AnalyticsUi {
        val today = LocalDate.now()
        val current = YearMonth.from(today)
        val range = when (p) {
            RangePreset.THIS_MONTH -> DateRange.ofMonth(current)
            RangePreset.LAST_MONTH -> DateRange.ofMonth(current.minusMonths(1))
            RangePreset.LAST_3 -> DateRange.lastMonths(3, today)
            RangePreset.LAST_6 -> DateRange.lastMonths(6, today)
            RangePreset.THIS_YEAR -> DateRange.thisYear(today)
            RangePreset.CUSTOM -> DateRange.between(c.first, c.second)
        }
        val stats = Calculations.stats(snap.transactions, range, snap.rates, today)
        val monthly = Calculations.monthlyTotals(snap.transactions, range.months().takeLast(12), snap.rates)
        val daily = range.days <= 62
        val trend = if (daily) Calculations.dailyExpenses(snap.transactions, range, snap.rates).map { it.second }
        else Calculations.monthlyTotals(snap.transactions, range.months(), snap.rates).map { it.expenseMinor }

        val earliest = snap.transactions.minOfOrNull { it.date }?.let { YearMonth.from(it) } ?: current
        val first = if (earliest.isBefore(current.minusMonths(1))) earliest else current.minusMonths(1)
        val options = generateSequence(current) { it.minusMonths(1) }.takeWhile { !it.isBefore(first) }.take(60).toList()

        val highest = stats.highestExpense
        val highestLabel = when {
            highest == null -> "—"
            highest.description.isNotBlank() -> highest.description
            else -> snap.categoryMap[highest.categoryId]?.name ?: "Expense"
        }
        return AnalyticsUi(
            preset = p,
            range = range,
            currency = snap.defaultCurrency,
            stats = stats,
            expenseTotals = Calculations.categoryTotals(snap.transactions, range, TransactionType.EXPENSE, snap.rates),
            monthly = monthly,
            trend = trend,
            trendIsDaily = daily,
            categories = snap.categoryMap,
            compareFrom = from,
            compareTo = to,
            comparison = Calculations.compareExpenses(snap.transactions, from, to, snap.rates),
            categoryComparison = Calculations.compareCategories(snap.transactions, from, to, snap.rates),
            monthOptions = options,
            highestExpenseLabel = highestLabel
        )
    }
}

@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val custom by viewModel.customRange.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(0) } // 1 = custom from, 2 = custom to

    ScreenScaffold(title = "Analytics") { padding ->
        val state = ui
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangePreset.entries.forEach { p ->
                        FilterChip(selected = state.preset == p, onClick = { viewModel.setPreset(p) }, label = { Text(p.label) })
                    }
                }
            }
            if (state.preset == RangePreset.CUSTOM) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picking = 1 }) { Text("From ${formatDate(custom.first)}") }
                        OutlinedButton(onClick = { picking = 2 }) { Text("To ${formatDate(custom.second)}") }
                    }
                }
            }
            item {
                Text(
                    "${formatDate(state.range.start)} – ${formatDate(state.range.end)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.stats.summary.skipped > 0) {
                item {
                    Text(
                        "${state.stats.summary.skipped} transaction(s) in other currencies are not counted because no exchange rate is set.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            item { StatsGrid(state) }
            item { DistributionCard(state) }
            item { IncomeVsExpenseCard(state) }
            item { TrendCard2(state) }
            item { CategoryBarsCard(state) }
            item { ComparisonCard(state, viewModel) }
        }

        if (picking != 0) {
            val isFrom = picking == 1
            AppDatePickerDialog(
                initial = if (isFrom) custom.first else custom.second,
                onDismiss = { picking = 0 },
                onPicked = { d -> if (isFrom) viewModel.setCustomFrom(d) else viewModel.setCustomTo(d) }
            )
        }
    }
}

@Composable
private fun StatsGrid(ui: AnalyticsUi) {
    val code = ui.currency.code
    val s = ui.stats
    val topName = s.topCategory?.let { ui.categories[it.categoryId]?.name ?: "Deleted category" } ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Total expenses", moneyText(s.summary.expenseMinor, code), ExpenseRed, Modifier.weight(1f))
            StatCard("Total income", moneyText(s.summary.incomeMinor, code), IncomeGreen, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Net savings", moneyText(s.summary.netMinor, code), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatCard("Avg. daily spending", moneyText(s.averageDailyMinor, code), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Highest expense", if (s.highestExpense == null) "—" else moneyText(s.highestExpenseMinor, code), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), caption = ui.highestExpenseLabel)
            StatCard("Top category", topName, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f), caption = s.topCategory?.let { moneyText(it.totalMinor, code) } ?: "")
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier, caption: String = "") {
    AppCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor, maxLines = 1)
            if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun colorOf(category: Category?): Color = if (category != null) Color(category.colorArgb) else Color(0xFF94A3B8)

@Composable
private fun DistributionCard(ui: AnalyticsUi) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Expenses by category")
            if (ui.expenseTotals.isEmpty()) {
                Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val total = ui.expenseTotals.sumOf { it.totalMinor }
                DonutChart(
                    slices = ui.expenseTotals.map { DonutSlice(it.totalMinor, colorOf(ui.categories[it.categoryId])) },
                    modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally),
                    strokeWidth = 24.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(moneyText(total, ui.currency.code), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
                ui.expenseTotals.take(8).forEach { t ->
                    val category = ui.categories[t.categoryId]
                    val pct = if (total > 0) (t.totalMinor * 100 / total) else 0L
                    LegendRow(colorOf(category), "${category?.name ?: "Deleted category"} ($pct%)", moneyText(t.totalMinor, ui.currency.code))
                }
            }
        }
    }
}

@Composable
private fun IncomeVsExpenseCard(ui: AnalyticsUi) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Income vs expenses")
            if (ui.monthly.all { it.incomeMinor == 0L && it.expenseMinor == 0L }) {
                Text("No data in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                GroupedBarChart(
                    groups = ui.monthly.map { BarGroup(formatMonthShort(it.month), it.incomeMinor, it.expenseMinor) },
                    firstColor = IncomeGreen,
                    secondColor = ExpenseRed,
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(IncomeGreen, "Income")
                    LegendDot(ExpenseRed, "Expenses")
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TrendCard2(ui: AnalyticsUi) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(if (ui.trendIsDaily) "Daily spending trend" else "Monthly spending trend")
            if (ui.trend.all { it == 0L }) {
                Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChart(values = ui.trend, modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        }
    }
}

@Composable
private fun CategoryBarsCard(ui: AnalyticsUi) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Category comparison")
            val rows = ui.expenseTotals.take(8)
            if (rows.isEmpty()) {
                Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val max = rows.maxOf { it.totalMinor }.coerceAtLeast(1L)
                rows.forEach { t ->
                    val category = ui.categories[t.categoryId]
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(category?.name ?: "Deleted category", style = MaterialTheme.typography.bodySmall)
                            Text(moneyText(t.totalMinor, ui.currency.code), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            Box(
                                Modifier.fillMaxWidth((t.totalMinor.toFloat() / max.toFloat()).coerceIn(0.02f, 1f))
                                    .fillMaxHeight().background(colorOf(category))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(ui: AnalyticsUi, viewModel: AnalyticsViewModel) {
    val code = ui.currency.code
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Compare two months")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChipDropdown(
                    label = "From",
                    selectedText = formatMonth(ui.compareFrom),
                    options = ui.monthOptions.map { formatMonth(it) to it },
                    onSelect = viewModel::setCompareFrom
                )
                Text("vs", style = MaterialTheme.typography.bodySmall)
                ChipDropdown(
                    label = "To",
                    selectedText = formatMonth(ui.compareTo),
                    options = ui.monthOptions.map { formatMonth(it) to it },
                    onSelect = viewModel::setCompareTo
                )
            }
            val c = ui.comparison
            Text("${formatMonth(ui.compareFrom)} expenses: ${moneyText(c.fromMinor, code)}", style = MaterialTheme.typography.bodyMedium)
            Text("${formatMonth(ui.compareTo)} expenses: ${moneyText(c.toMinor, code)}", style = MaterialTheme.typography.bodyMedium)
            val diff = c.differenceMinor
            val percent = c.percentChange()
            val diffColor = when {
                diff > 0 -> ExpenseRed
                diff < 0 -> IncomeGreen
                else -> MaterialTheme.colorScheme.onSurface
            }
            val sign = if (diff > 0) "+" else if (diff < 0) "-" else ""
            val percentText = percent?.let { " (${if (it.signum() > 0) "+" else ""}${it.toPlainString()}%)" } ?: " (no earlier spending to compare)"
            Text(
                "Difference: $sign${moneyText(kotlin.math.abs(diff), code)}$percentText",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = diffColor
            )
            val movers = ui.categoryComparison.filter { it.differenceMinor != 0L }.take(5)
            if (movers.isNotEmpty()) {
                HorizontalDivider()
                Text("Biggest changes by category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                movers.forEach { m ->
                    val category = ui.categories[m.categoryId]
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(category?.name ?: "Deleted category", style = MaterialTheme.typography.bodySmall)
                        Text(
                            (if (m.differenceMinor > 0) "+" else "-") + moneyText(kotlin.math.abs(m.differenceMinor), code),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (m.differenceMinor > 0) ExpenseRed else IncomeGreen
                        )
                    }
                }
            }
        }
    }
}
