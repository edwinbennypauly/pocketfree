package app.pocketfree.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketfree.data.repo.LedgerRepository
import app.pocketfree.data.repo.SettingsRepository
import app.pocketfree.domain.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LockState { LOADING, LOCKED, UNLOCKED }

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val repo: LedgerRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val hideAmounts: StateFlow<Boolean> = settings.hideAmounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val biometricEnabled: StateFlow<Boolean> = settings.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val unlocked = MutableStateFlow(false)

    /** LOADING until the settings file has been read, so the app never flashes content before a PIN prompt. */
    val lockState: StateFlow<LockState> = combine(settings.pinConfigured, unlocked) { configured, isUnlocked ->
        if (!configured || isUnlocked) LockState.UNLOCKED else LockState.LOCKED
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LockState.LOADING)

    private var backgroundedAt: Long? = null

    init {
        // Setting a PIN while using the app must not lock you out of your current session.
        viewModelScope.launch {
            var previous: Boolean? = null
            settings.pinConfigured.collect { now ->
                if (previous == false && now) unlocked.value = true
                previous = now
            }
        }
    }

    fun onStopped() { backgroundedAt = SystemClock.elapsedRealtime() }

    fun onStarted() {
        val left = backgroundedAt
        if (left != null && SystemClock.elapsedRealtime() - left > LOCK_AFTER_MS) unlocked.value = false
        backgroundedAt = null
        viewModelScope.launch { repo.runRecurring(LocalDate.now()) }
    }

    fun tryUnlock(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = settings.verifyPin(pin)
            if (ok) unlocked.value = true
            onResult(ok)
        }
    }

    fun unlockWithBiometric() { unlocked.value = true }

    private companion object {
        /** Re-lock if the app was in the background longer than this. Short trips (file picker, rotation) don't lock. */
        const val LOCK_AFTER_MS = 30_000L
    }
}
