package app.pocketfree

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketfree.ui.AppViewModel
import app.pocketfree.ui.nav.PocketFreeRoot
import app.pocketfree.ui.theme.PocketFreeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            PocketFreeTheme(themeMode) {
                PocketFreeRoot()
            }
        }
    }
}
