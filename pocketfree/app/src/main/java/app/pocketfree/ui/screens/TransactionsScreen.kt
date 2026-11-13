package app.pocketfree.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Category
import app.pocketfree.domain.Filtering
import app.pocketfree.domain.LedgerSnapshot
import app.pocketfree.domain.RateTable
import app.pocketfree.domain.SortOrder
import app.pocketfree.domain.Transaction
import app.pocketfree.domain.TransactionFilter
import app.pocketfree.domain.TransactionType
import app.pocketfree.ui.CollectEvents
import app.pocketfree.ui.Events
import app.pocketfree.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionsUi(
    val items: List<Transaction>,
    val categoryMap: Map<Long, Category>,
    val categories: List<Category>,
    val years: List<Int>,
    val currency: AppCurrency,
    val totalCount: Int
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(private val repo: LedgerRepository) : ViewModel() {
    val events = Events()
    val filter = MutableStateFlow(TransactionFilter())

    val ui: StateFlow<TransactionsUi?> = combine(repo.snapshot, filter) { snap, f -> build(snap, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (TransactionFilter) -> TransactionFilter) = filter.update(transform)
    fun clearFilters() { filter.update { TransactionFilter(sort = it.sort) } }

    fun delete(id: Long) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
            events.send("Transaction deleted")
        }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            val ok = repo.duplicateTransaction(id, LocalDate.now())
            events.send(if (ok) "Duplicated with today's date" else "Could not duplicate")
        }
    }

    private fun build(snap: LedgerSnapshot, f: TransactionFilter): TransactionsUi = TransactionsUi(
        items = Filtering.apply(snap.transactions, f, snap.categoryMap, snap.rates),
        categoryMap = snap.categoryMap,
        categories = snap.categories,
        years = snap.transactions.map { it.date.year }.distinct().sortedDescending(),
        currency = snap.defaultCurrency,
        totalCount = snap.transactions.size
    )
}

@Composable
fun TransactionsScreen(onEdit: (Long) -> Unit, viewModel: TransactionsViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEvents(viewModel.events.flow, snackbar)

    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Long?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(0) } // 1 = from date, 2 = to date

    ScreenScaffold(
        title = "Transactions",
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = { showSearch = !showSearch; if (!showSearch) viewModel.update { it.copy(query = "") } }) {
                Icon(if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = "Search")
            }
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Rounded.FilterList, contentDescription = "Filters", tint = if (filter.hasFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    ) { padding ->
        val state = ui
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSearch) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = { q -> viewModel.update { it.copy(query = q) } },
                    placeholder = { Text("Search description, notes, category or amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (showFilters && state != null) {
                FilterBar(state, filter, viewModel, onPickDate = { picking = it })
            }
            if (state == null) return@Column

            if (state.totalCount == 0) {
                EmptyState("No transactions yet", "Tap + to add your first one.")
            } else if (state.items.isEmpty()) {
                EmptyState("Nothing matches", "Try changing or clearing the filters.")
            } else {
                Text(
                    "${state.items.size} transaction(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                val byDate = filter.sort == SortOrder.NEWEST || filter.sort == SortOrder.OLDEST
                val groups = remember(state.items, byDate) { if (byDate) Filtering.groupByDate(state.items) else null }
                val today = LocalDate.now()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (groups != null) {
                        groups.forEach { (date, list) ->
                            item(key = "h$date") {
                                Text(
                                    dayHeading(date, today),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp)
                                )
                            }
                            items(list, key = { it.id }) { t ->
                                TransactionRow(t, state.categoryMap[t.categoryId], onClick = { openId = t.id }, showDate = false)
                            }
                        }
                    } else {
                        items(state.items, key = { it.id }) { t ->
                            TransactionRow(t, state.categoryMap[t.categoryId], onClick = { openId = t.id })
                        }
                    }
                }
            }
        }

        val selected = ui?.items?.firstOrNull { it.id == openId }
        if (selected != null) {
            DetailDialog(
                transaction = selected,
                category = ui?.categoryMap?.get(selected.categoryId),
                onClose = { openId = null },
                onEdit = { openId = null; onEdit(selected.id) },
                onDuplicate = { openId = null; viewModel.duplicate(selected.id) },
                onDelete = { deleteId = selected.id; openId = null }
            )
        }
        val toDelete = deleteId
        if (toDelete != null) {
            ConfirmDialog(
                title = "Delete transaction?",
                message = "This permanently removes the transaction. It cannot be undone.",
                confirmLabel = "Delete",
                onConfirm = { viewModel.delete(toDelete); deleteId = null },
                onDismiss = { deleteId = null }
            )
        }
        if (picking != 0) {
            val isFrom = picking == 1
            AppDatePickerDialog(
                initial = (if (isFrom) filter.from else filter.to) ?: LocalDate.now(),
                onDismiss = { picking = 0 },
                onPicked = { d -> viewModel.update { if (isFrom) it.copy(from = d) else it.copy(to = d) } }
            )
        }
    }
}

@Composable
private fun FilterBar(
    state: TransactionsUi,
    filter: TransactionFilter,
    viewModel: TransactionsViewModel,
    onPickDate: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChipDropdown<TransactionType?>(
            label = "Type",
            selectedText = filter.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() },
            options = listOf<Pair<String, TransactionType?>>("All types" to null) + TransactionType.entries.map { it.displayName to it },
            onSelect = { v -> viewModel.update { it.copy(type = v) } }
        )
        ChipDropdown<Long?>(
            label = "Category",
            selectedText = filter.categoryId?.let { state.categoryMap[it]?.name },
            options = listOf<Pair<String, Long?>>("All categories" to null) + state.categories.map { it.name + (if (it.type != TransactionType.EXPENSE) " (${it.type.displayName.lowercase()})" else "") to it.id },
            onSelect = { v -> viewModel.update { it.copy(categoryId = v) } }
        )
        ChipDropdown<Int?>(
            label = "Year",
            selectedText = filter.year?.toString(),
            options = listOf<Pair<String, Int?>>("All years" to null) + state.years.map { it.toString() to it },
            onSelect = { v -> viewModel.update { it.copy(year = v) } }
        )
        ChipDropdown<Int?>(
            label = "Month",
            selectedText = filter.month?.let { Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
            options = listOf<Pair<String, Int?>>("All months" to null) + (1..12).map { Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) to it },
            onSelect = { v -> viewModel.update { it.copy(month = v) } }
        )
        FilterChip(selected = filter.from != null, onClick = { onPickDate(1) }, label = { Text(filter.from?.let { "From ${formatDate(it)}" } ?: "From date") })
        FilterChip(selected = filter.to != null, onClick = { onPickDate(2) }, label = { Text(filter.to?.let { "To ${formatDate(it)}" } ?: "To date") })
        ChipDropdown<SortOrder>(
            label = "Sort",
            selectedText = when (filter.sort) {
                SortOrder.NEWEST -> null
                SortOrder.OLDEST -> "Oldest first"
                SortOrder.HIGHEST -> "Highest amount"
                SortOrder.LOWEST -> "Lowest amount"
            },
            options = listOf("Newest first" to SortOrder.NEWEST, "Oldest first" to SortOrder.OLDEST, "Highest amount" to SortOrder.HIGHEST, "Lowest amount" to SortOrder.LOWEST),
            onSelect = { v -> viewModel.update { it.copy(sort = v) } }
        )
        if (filter.hasFilters) {
            TextButton(onClick = viewModel::clearFilters) { Text("Clear") }
        }
    }
}

@Composable
private fun DetailDialog(
    transaction: Transaction,
    category: Category?,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(transaction.description.ifBlank { category?.name ?: "Transaction" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (transaction.type) {
                        TransactionType.EXPENSE -> signedMoneyText(transaction.amountMinor, transaction.currencyCode, negative = true)
                        TransactionType.INCOME -> signedMoneyText(transaction.amountMinor, transaction.currencyCode, negative = false)
                        else -> moneyText(transaction.amountMinor, transaction.currencyCode)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("${transaction.type.displayName} • ${category?.name ?: "Deleted category"}")
                if (transaction.type == TransactionType.INVESTMENT || transaction.type == TransactionType.WITHDRAWAL) {
                    Text("Not counted as income or expense.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatDate(transaction.date))
                if (transaction.recurringRuleId != null) Text("Created by a recurring rule")
                if (transaction.notes.isNotBlank()) Text("Notes: ${transaction.notes}")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDuplicate) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Duplicate", modifier = Modifier.padding(start = 4.dp))
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Edit", modifier = Modifier.padding(start = 4.dp))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
