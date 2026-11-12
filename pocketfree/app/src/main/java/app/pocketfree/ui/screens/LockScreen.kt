package app.pocketfree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketfree.ui.components.AmountKeypad

@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    onSubmit: (String, (Boolean) -> Unit) -> Unit,
    onBiometric: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (biometricEnabled) onBiometric() }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Enter your PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(16.dp)) {
            repeat(pin.length) {
                Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (error) "Incorrect PIN, try again" else " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        AmountKeypad(
            onKey = { key ->
                if (!checking) {
                    error = false
                    if (key == '<') pin = pin.dropLast(1)
                    else if (key in '0'..'9' && pin.length < 6) pin += key
                }
            },
            showDecimal = false,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                checking = true
                onSubmit(pin) { ok ->
                    checking = false
                    if (!ok) { error = true; pin = "" }
                }
            },
            enabled = pin.length >= 4 && !checking,
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(50.dp)
        ) { Text("Unlock") }
        if (biometricEnabled) {
            TextButton(onClick = onBiometric) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Use fingerprint", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
