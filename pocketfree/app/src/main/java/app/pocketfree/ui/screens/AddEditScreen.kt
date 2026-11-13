package app.pocketfree.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.data.repo.SaveOutcome
import app.pocketfree.domain.AmountInput
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.Category
import app.pocketfree.domain.Money
import app.pocketfree.domain.TransactionType
import app.pocketfree.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AddEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: LedgerRepository
) : ViewModel() {
    private val editId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it >= 0 }
    val isEditing: Boolean = editId != null

    var type by mutableStateOf(
        runCatching { TransactionType.valueOf(savedStateHandle.get<String>("type") ?: "EXPENSE") }
            .getOrDefault(TransactionType.EXPENSE)
    )
        private set
    var amountText by mutableStateOf("")
        private set
    var currency by mutableStateOf(AppCurrency.AED)
        private set
    var categoryId by mutableStateOf<Long?>(null)
        private set
    var description by mutableStateOf("")
    var notes by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var errors by mutableStateOf<List<String>>(emptyList())
        private set
    var saving by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    val categories: StateFlow<List<Category>> = repo.snapshot.map { it.categories }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val snap = repo.snapshot.first()
            if (editId != null) {
                val t = snap.transactions.firstOrNull { it.id == editId }
                if (t != null) {
                    type = t.type
                    amountText = Money.toDecimal(t.amountMinor).toPlainString()
                    currency = AppCurrency.fromCode(t.currencyCode)
                    categoryId = t.categoryId
                    description = t.description
                    notes = t.notes
                    date = t.date
                }
            } else {
                currency = snap.defaultCurrency
            }
            loaded = true
        }
    }

    fun changeType(newType: TransactionType) {
        if (newType != type) {
            type = newType
            categoryId = null
        }
    }

    fun onKey(key: Char) { amountText = AmountInput.press(amountText, key, currency.decimals) }
    fun selectCategory(id: Long) { categoryId = id }
    fun selectCurrency(c: AppCurrency) { currency = c }

    fun save(onDone: () -> Unit) {
        if (saving) return
        val minor = Money.parseToMinor(amountText, currency.decimals)
        val chosen = categoryId
        val problems = ArrayList<String>()
        if (minor == null || minor <= 0) problems.add("Enter an amount greater than zero.")
        if (chosen == null) problems.add("Choose a category.")
        if (problems.isNotEmpty() || minor == null || chosen == null) {
            errors = problems
            return
        }
        saving = true
        viewModelScope.launch {
            when (val outcome = repo.saveTransaction(editId, type, minor, currency.code, chosen, description, notes, date)) {
                SaveOutcome.Saved -> onDone()
                is SaveOutcome.Invalid -> { errors = outcome.errors; saving = false }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(onClose: () -> Unit, viewModel: AddEditViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    val visibleCategories = categories.filter {
        it.type == viewModel.type && (!it.isArchived || it.id == viewModel.categoryId)
    }
    val title = when {
        viewModel.isEditing -> "Edit transaction"
        viewModel.type == TransactionType.EXPENSE -> "Add expense"
        viewModel.type == TransactionType.INCOME -> "Add income"
        viewModel.type == TransactionType.INVESTMENT -> "Add investment"
        else -> "Add withdrawal"
    }

    ScreenScaffold(
        title = title,
        onBack = onClose,
        bottomInset = true,
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Button(
                    onClick = { viewModel.save(onClose) },
                    enabled = viewModel.loaded && !viewModel.saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (viewModel.isEditing) "Save changes" else "Save", style = MaterialTheme.typography.titleMedium) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { option ->
                    FilterChip(
                        selected = viewModel.type == option,
                        onClick = { viewModel.changeType(option) },
                        label = { Text(option.chipLabel) }
                    )
                }
            }
            when (viewModel.type) {
                TransactionType.INVESTMENT -> Text(
                    "Money you put into investments or savings. It lowers your cash balance but is not an expense.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TransactionType.WITHDRAWAL -> Text(
                    "Money you take back out of investments. It is not income. If you made a profit, record that part separately as Income > Investment returns.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {}
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                ChipDropdown(
                    label = viewModel.currency.code,
                    selectedText = viewModel.currency.code,
                    options = AppCurrency.entries.map { it.code to it },
                    onSelect = viewModel::selectCurrency
                )
                Text(
                    text = viewModel.amountText.ifEmpty { "0" },
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (viewModel.amountText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            AmountKeypad(onKey = viewModel::onKey, showDecimal = viewModel.currency.decimals > 0)

            SectionTitle("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                visibleCategories.forEach { c ->
                    FilterChip(
                        selected = viewModel.categoryId == c.id,
                        onClick = { viewModel.selectCategory(c.id) },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(CategoryIcons.get(c.iconKey), contentDescription = null, tint = Color(c.colorArgb), modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it.take(200) },
                label = { Text("Description (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(formatDate(viewModel.date), modifier = Modifier.padding(start = 8.dp))
                }
                AssistChip(onClick = { viewModel.date = LocalDate.now() }, label = { Text("Today") })
                AssistChip(onClick = { viewModel.date = LocalDate.now().minusDays(1) }, label = { Text("Yesterday") })
            }

            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it.take(2000) },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            viewModel.errors.forEach {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = viewModel.date,
            onDismiss = { showDatePicker = false },
            onPicked = { viewModel.date = it }
        )
    }
}
