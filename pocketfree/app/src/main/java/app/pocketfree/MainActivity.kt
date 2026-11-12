package app.pocketfree

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketfree.security.Biometric
import app.pocketfree.ui.AppViewModel
import app.pocketfree.ui.LockState
import app.pocketfree.ui.components.LocalHideAmounts
import app.pocketfree.ui.nav.PocketFreeRoot
import app.pocketfree.ui.screens.LockScreen
import app.pocketfree.ui.theme.PocketFreeTheme
import dagger.hilt.android.AndroidEntryPoint

/** FragmentActivity (a ComponentActivity subclass) is required by the system biometric prompt. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val hideAmounts by appViewModel.hideAmounts.collectAsStateWithLifecycle()
            val biometricEnabled by appViewModel.biometricEnabled.collectAsStateWithLifecycle()
            val lockState by appViewModel.lockState.collectAsStateWithLifecycle()

            PocketFreeTheme(themeMode) {
                CompositionLocalProvider(LocalHideAmounts provides hideAmounts) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        when (lockState) {
                            LockState.LOADING -> Box(Modifier.fillMaxSize())
                            LockState.LOCKED -> LockScreen(
                                biometricEnabled = biometricEnabled && Biometric.isAvailable(this@MainActivity),
                                onSubmit = { pin, onResult -> appViewModel.tryUnlock(pin, onResult) },
                                onBiometric = { Biometric.prompt(this@MainActivity) { appViewModel.unlockWithBiometric() } }
                            )
                            LockState.UNLOCKED -> PocketFreeRoot()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onStarted()
    }

    override fun onStop() {
        super.onStop()
        appViewModel.onStopped()
    }
}
