package app.pocketfree.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.ImportPreview
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.ui.CollectEvents
import app.pocketfree.ui.Events
import app.pocketfree.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: LedgerRepository
) : ViewModel() {
    val events = Events()

    var busy by mutableStateOf(false)
        private set
    var preview by mutableStateOf<ImportPreview?>(null)
        private set

    fun exportCsv(uri: Uri) = export(uri, "CSV") { "\uFEFF" + repo.buildCsv() }
    fun exportJson(uri: Uri) = export(uri, "backup") { repo.buildBackupJson() }

    private fun export(uri: Uri, label: String, build: suspend () -> String) {
        viewModelScope.launch {
            busy = true
            try {
                val text = build()
                withContext(Dispatchers.IO) {
                    val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the file for writing.")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                events.send("$label saved")
            } catch (e: Exception) {
                events.send("Export failed: ${e.message ?: "unknown error"}")
            } finally {
                busy = false
            }
        }
    }

    fun pickedForImport(uri: Uri, isJson: Boolean) {
        viewModelScope.launch {
            busy = true
            try {
                val text = readText(uri)
                preview = if (isJson) repo.previewJson(text) else repo.previewCsv(text)
            } catch (e: Exception) {
                events.send("Could not read the file: ${e.message ?: "unknown error"}")
            } finally {
                busy = false
            }
        }
    }

    fun cancelImport() { preview = null }

    fun confirmImport() {
        val current = preview ?: return
        viewModelScope.launch {
            busy = true
            try {
                val r = repo.applyImport(current)
                preview = null
                events.send(
                    buildString {
                        append("Imported ${r.added} transaction(s)")
                        if (r.duplicatesSkipped > 0) append(", skipped ${r.duplicatesSkipped} duplicate(s)")
                        if (r.categoriesCreated > 0) append(", created ${r.categoriesCreated} categor${if (r.categoriesCreated == 1) "y" else "ies"}")
                        if (r.budgets > 0) append(", restored ${r.budgets} budget(s)")
                        if (r.rates > 0) append(", restored ${r.rates} rate(s)")
                    }
                )
            } catch (e: Exception) {
                events.send("Import failed, nothing was changed: ${e.message ?: "unknown error"}")
            } finally {
                busy = false
            }
        }
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open the file.")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_IMPORT_BYTES) throw IOException("The file is larger than 20 MB.")
                out.write(buffer, 0, n)
            }
            String(out.toByteArray(), Charsets.UTF_8)
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 20L * 1024 * 1024
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val snackbar = remember { SnackbarHostState() }
    CollectEvents(viewModel.events.flow, snackbar)
    var importIsJson by remember { mutableStateOf(false) }

    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportCsv(uri)
    }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportJson(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickedForImport(uri, importIsJson)
    }

    ScreenScaffold(title = "Backup & restore", onBack = onBack, bottomInset = true, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (viewModel.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            SectionTitle("Export")
            Text(
                "Files are saved wherever you choose (Downloads, Google Drive, etc.). Nothing is uploaded by the app. Exported files are not encrypted, so keep them somewhere private.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { jsonExport.launch("pocketfree-backup-${LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.busy) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Full backup (JSON)", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { csvExport.launch("pocketfree-transactions-${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.busy) {
                Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Transactions (CSV)", modifier = Modifier.padding(start = 8.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Restore / import")
            Text(
                "You will see a preview first. Existing data is never overwritten: new transactions are added, exact duplicates are skipped, and nothing changes until you confirm.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = { importIsJson = true; importPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.busy) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Restore from backup (JSON)", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { importIsJson = false; importPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.busy) {
                Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Import transactions (CSV)", modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                "CSV columns: date (yyyy-MM-dd), type (expense/income), amount, category. Optional: currency, description, notes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val p = viewModel.preview
        if (p != null) ImportPreviewDialog(p, onConfirm = viewModel::confirmImport, onCancel = viewModel::cancelImport)
    }
}

@Composable
private fun ImportPreviewDialog(preview: ImportPreview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val fatal = preview.parsed.fatalError
    val plan = preview.plan
    if (fatal != null || plan == null) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Can't import this file") },
            text = { Text(fatal ?: "The file could not be read.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("OK") } }
        )
        return
    }
    val parsed = preview.parsed
    val somethingToDo = plan.toAdd.isNotEmpty() || plan.newCategories.isNotEmpty() || parsed.budgets.isNotEmpty() || parsed.rates.isNotEmpty()
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Preview import") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("New transactions to add: ${plan.toAdd.size}")
                Text("Already in the app (skipped): ${plan.duplicates.size}")
                if (plan.newCategories.isNotEmpty()) {
                    Text("New categories to create: ${plan.newCategories.joinToString { it.name }}")
                }
                if (parsed.budgets.isNotEmpty()) Text("Budgets in file: ${parsed.budgets.size} (same month and category will be replaced)")
                if (parsed.rates.isNotEmpty()) Text("Exchange rates in file: ${parsed.rates.size} (same pair will be replaced)")
                if (parsed.errors.isNotEmpty()) {
                    Text("Problems found, these rows will be skipped: ${parsed.errors.size}", color = MaterialTheme.colorScheme.error)
                    parsed.errors.take(5).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (parsed.errors.size > 5) Text("…and ${parsed.errors.size - 5} more", style = MaterialTheme.typography.bodySmall)
                }
                plan.toAdd.take(3).forEach {
                    Text("e.g. ${it.date} ${it.type.name.lowercase()} ${app.pocketfree.domain.Money.toDecimal(it.amountMinor).toPlainString()} ${it.currencyCode} · ${it.categoryName}", style = MaterialTheme.typography.bodySmall)
                }
                if (!somethingToDo) Text("There is nothing new to import.")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = somethingToDo) { Text("Import") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
