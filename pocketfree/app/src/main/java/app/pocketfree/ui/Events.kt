package app.pocketfree.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** One-shot messages from a ViewModel to a snackbar. */
class Events {
    private val channel = Channel<String>(Channel.BUFFERED)
    val flow: Flow<String> = channel.receiveAsFlow()
    fun send(message: String) { channel.trySend(message) }
}

@Composable
fun CollectEvents(events: Flow<String>, host: SnackbarHostState) {
    LaunchedEffect(events) {
        events.collect { host.showSnackbar(it) }
    }
}
