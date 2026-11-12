package app.pocketfree.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.SettingsRepository
import app.pocketfree.domain.PinHasher
import app.pocketfree.security.Biometric
import app.pocketfree.ui.CollectEvents
import app.pocketfree.ui.Events
import app.pocketfree.ui.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SecurityViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val events = Events()
    val pinConfigured: StateFlow<Boolean> = settings.pinConfigured.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val biometricEnabled: StateFlow<Boolean> = settings.biometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val hideAmounts: StateFlow<Boolean> = settings.hideAmounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setHideAmounts(value: Boolean) { viewModelScope.launch { settings.setHideAmounts(value) } }
    fun setBiometric(value: Boolean) { viewModelScope.launch { settings.setBiometricEnabled(value) } }

    /** [onResult] gets an error message, or null on success. */
    fun setPin(newPin: String, confirm: String, onResult: (String?) -> Unit) {
        val problem = validateNew(newPin, confirm)
        if (problem != null) { onResult(problem); return }
        viewModelScope.launch {
            settings.setPin(newPin)
            events.send("PIN set")
            onResult(null)
        }
    }

    fun changePin(current: String, newPin: String, confirm: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (!settings.verifyPin(current)) { onResult("Current PIN is incorrect."); return@launch }
            val problem = validateNew(newPin, confirm)
            if (problem != null) { onResult(problem); return@launch }
            settings.setPin(newPin)
            events.send("PIN changed")
            onResult(null)
        }
    }

    fun removePin(current: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (!settings.verifyPin(current)) { onResult("Current PIN is incorrect."); return@launch }
            settings.clearPin()
            events.send("PIN removed")
            onResult(null)
        }
    }

    private fun validateNew(newPin: String, confirm: String): String? = when {
        !PinHasher.isValidPin(newPin) -> "Use 4 to 6 digits."
        newPin != confirm -> "The two PINs do not match."
        else -> null
    }
}

private enum class PinDialogMode { SET, CHANGE, REMOVE }

@Composable
fun SecurityScreen(onBack: () -> Unit, viewModel: SecurityViewModel = hiltViewModel()) {
    val pinSet by viewModel.pinConfigured.collectAsStateWithLifecycle()
    val biometric by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val hide by viewModel.hideAmounts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectEvents(viewModel.events.flow, snackbar)
    val context = LocalContext.current
    val biometricAvailable = remember { Biometric.isAvailable(context) }
    var dialog by remember { mutableStateOf<PinDialogMode?>(null) }

    ScreenScaffold(title = "Privacy & security", onBack = onBack, bottomInset = true, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("App lock")
            if (!pinSet) {
                Text("Protect the app with a PIN. It locks when you leave the app for more than 30 seconds.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { dialog = PinDialogMode.SET }) { Text("Set a PIN") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dialog = PinDialogMode.CHANGE }) { Text("Change PIN") }
                    OutlinedButton(onClick = { dialog = PinDialogMode.REMOVE }) { Text("Remove PIN", color = MaterialTheme.colorScheme.error) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Unlock with fingerprint / face", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (biometricAvailable) "Your PIN still works as a fallback." else "Set up a fingerprint or face unlock in your phone's settings first.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = biometric && biometricAvailable, onCheckedChange = { viewModel.setBiometric(it) }, enabled = biometricAvailable)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Hide amounts")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Show •••• instead of money amounts (you can also tap the eye on Home).", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = hide, onCheckedChange = viewModel::setHideAmounts)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("How your data is protected")
            Text(
                "• Everything is stored only on this phone. The app has no internet permission, no accounts, no ads and no analytics.\n" +
                    "• Your PIN is never stored, only a salted one-way hash of it.\n" +
                    "• The database is not encrypted. It is protected by Android's app isolation and your phone's own screen lock. Use a screen lock on your phone.\n" +
                    "• The PIN lock keeps people out of the app, but it does not encrypt the database file.\n" +
                    "• Exported CSV/JSON files are plain text. Keep them somewhere private.\n" +
                    "• Android cloud auto-backup is turned off for this app, so your data is not copied to Google automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val mode = dialog
        if (mode != null) PinDialog(mode, viewModel, onDismiss = { dialog = null })
    }
}

@Composable
private fun PinDialog(mode: PinDialogMode, viewModel: SecurityViewModel, onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    @Composable
    fun PinField(value: String, label: String, onChange: (String) -> Unit) {
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)); error = null },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (mode) { PinDialogMode.SET -> "Set a PIN"; PinDialogMode.CHANGE -> "Change PIN"; PinDialogMode.REMOVE -> "Remove PIN" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode != PinDialogMode.SET) PinField(current, "Current PIN") { current = it }
                if (mode != PinDialogMode.REMOVE) {
                    PinField(newPin, "New PIN (4-6 digits)") { newPin = it }
                    PinField(confirm, "Repeat new PIN") { confirm = it }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val done: (String?) -> Unit = { message -> if (message == null) onDismiss() else error = message }
                when (mode) {
                    PinDialogMode.SET -> viewModel.setPin(newPin, confirm, done)
                    PinDialogMode.CHANGE -> viewModel.changePin(current, newPin, confirm, done)
                    PinDialogMode.REMOVE -> viewModel.removePin(current, done)
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
