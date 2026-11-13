package app.pocketfree.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.CategoryOutcome
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.domain.Category
import app.pocketfree.domain.TransactionType
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

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repo: LedgerRepository) : ViewModel() {
    val events = Events()
    val categories: StateFlow<List<Category>> = repo.snapshot.map { it.categories }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [onResult] gets an error message, or null when saved. */
    fun save(id: Long?, name: String, type: TransactionType, icon: String, color: Int, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            when (val outcome = repo.saveCategory(id, name, type, icon, color)) {
                is CategoryOutcome.Invalid -> onResult(outcome.message)
                else -> { events.send("Category saved"); onResult(null) }
            }
        }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            when (val outcome = repo.removeCategory(id)) {
                CategoryOutcome.Deleted -> events.send("Category deleted")
                CategoryOutcome.Archived -> events.send("This category has transactions, so it was archived instead. Your history is unchanged.")
                is CategoryOutcome.Invalid -> events.send(outcome.message)
                CategoryOutcome.Saved -> {}
            }
        }
    }

    fun restore(id: Long) {
        viewModelScope.launch {
            when (val outcome = repo.restoreCategory(id)) {
                is CategoryOutcome.Invalid -> events.send(outcome.message)
                else -> events.send("Category restored")
            }
        }
    }
}

private data class CategoryEditor(val id: Long?, val name: String, val type: TransactionType, val icon: String, val color: Int)

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEvents(viewModel.events.flow, snackbar)
    var editor by remember { mutableStateOf<CategoryEditor?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    ScreenScaffold(
        title = "Categories",
        onBack = onBack,
        bottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = {
                editor = CategoryEditor(null, "", TransactionType.EXPENSE, "category", CategoryColors.palette[7])
            }) { Icon(Icons.Rounded.Add, contentDescription = "Add category") }
        }
    ) { padding ->
        val active = categories.filter { !it.isArchived }
        val archived = categories.filter { it.isArchived }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            TransactionType.entries.forEach { sectionType ->
                item(key = "header-${sectionType.name}") { SettingsHeader("${sectionType.displayName} categories") }
                categoryRows(active.filter { it.type == sectionType }) { c ->
                    editor = CategoryEditor(c.id, c.name, c.type, c.iconKey, c.colorArgb)
                }
            }
            if (archived.isNotEmpty()) {
                item { SettingsHeader("Archived (kept for your history)") }
                archived.forEach { c ->
                    item(key = "a${c.id}") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryAvatar(c, size = 36.dp)
                            Text(
                                "${c.name} (${c.type.name.lowercase()})",
                                Modifier.weight(1f).padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { viewModel.restore(c.id) }) { Text("Restore") }
                        }
                    }
                }
            }
        }

        val current = editor
        if (current != null) {
            CategoryEditorDialog(
                initial = current,
                onSave = { e, onError -> viewModel.save(e.id, e.name, e.type, e.icon, e.color) { error -> if (error == null) editor = null else onError(error) } },
                onDelete = { deleting = categories.firstOrNull { it.id == current.id }; editor = null },
                onDismiss = { editor = null }
            )
        }
        val toDelete = deleting
        if (toDelete != null) {
            ConfirmDialog(
                title = "Delete \"${toDelete.name}\"?",
                message = "If this category has transactions it will be archived instead of deleted, so your history keeps its category.",
                confirmLabel = "Delete",
                onConfirm = { viewModel.remove(toDelete.id); deleting = null },
                onDismiss = { deleting = null }
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.categoryRows(list: List<Category>, onClick: (Category) -> Unit) {
    list.forEach { c ->
        item(key = "c${c.id}") {
            Row(
                Modifier.fillMaxWidth().clickable { onClick(c) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryAvatar(c, size = 36.dp)
                Text(c.name, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
                Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    initial: CategoryEditor,
    onSave: (CategoryEditor, (String) -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var type by remember { mutableStateOf(initial.type) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    var error by remember { mutableStateOf<String?>(null) }
    val isNew = initial.id == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); error = null },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } }
                )
                if (isNew) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransactionType.entries.forEach { option ->
                            FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option.chipLabel) })
                        }
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.all.forEach { (key, vector) ->
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(if (icon == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center
                        ) { Icon(vector, contentDescription = key, tint = Color(color)) }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryColors.palette.forEach { argb ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(argb))
                                .then(if (color == argb) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = argb }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(CategoryEditor(initial.id, name, type, icon, color)) { message -> error = message } }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
