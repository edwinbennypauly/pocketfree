package app.pocketfree.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pocketfree.domain.AccentTheme
import app.pocketfree.domain.AppCurrency
import app.pocketfree.domain.PinHasher
import app.pocketfree.domain.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val CURRENCY = stringPreferencesKey("default_currency")
        val ACCENT = stringPreferencesKey("accent_theme")
        val INVEST_SEEDED = booleanPreferencesKey("investment_defaults_seeded")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
    }

    private val data get() = context.settingsDataStore.data

    val themeMode: Flow<ThemeMode> = data.map { prefs ->
        prefs[Keys.THEME]?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    val accent: Flow<AccentTheme> = data.map { prefs ->
        prefs[Keys.ACCENT]?.let { stored -> runCatching { AccentTheme.valueOf(stored) }.getOrNull() } ?: AccentTheme.TEAL
    }

    val defaultCurrency: Flow<AppCurrency> = data.map { AppCurrency.fromCode(it[Keys.CURRENCY]) }
    val hideAmounts: Flow<Boolean> = data.map { it[Keys.HIDE_AMOUNTS] ?: false }
    val biometricEnabled: Flow<Boolean> = data.map { it[Keys.BIOMETRIC] ?: false }
    val pinConfigured: Flow<Boolean> = data.map { it[Keys.PIN_HASH] != null && it[Keys.PIN_SALT] != null }

    /** One-time flag so the new Investment/Withdrawal categories are added to existing installs exactly once. */
    suspend fun investmentDefaultsSeeded(): Boolean = data.first()[Keys.INVEST_SEEDED] ?: false
    suspend fun markInvestmentDefaultsSeeded() { context.settingsDataStore.edit { it[Keys.INVEST_SEEDED] = true } }

    suspend fun setThemeMode(mode: ThemeMode) { context.settingsDataStore.edit { it[Keys.THEME] = mode.name } }
    suspend fun setAccent(accent: AccentTheme) { context.settingsDataStore.edit { it[Keys.ACCENT] = accent.name } }
    suspend fun setDefaultCurrency(currency: AppCurrency) { context.settingsDataStore.edit { it[Keys.CURRENCY] = currency.code } }
    suspend fun setHideAmounts(hide: Boolean) { context.settingsDataStore.edit { it[Keys.HIDE_AMOUNTS] = hide } }
    suspend fun setBiometricEnabled(enabled: Boolean) { context.settingsDataStore.edit { it[Keys.BIOMETRIC] = enabled } }

    /** Stores a salted PBKDF2 hash. The PIN itself is never saved. Caller must validate with PinHasher.isValidPin. */
    suspend fun setPin(pin: String) {
        val (salt, hash) = withContext(Dispatchers.Default) {
            val s = PinHasher.newSalt()
            PinHasher.toHex(s) to PinHasher.hash(pin, s)
        }
        context.settingsDataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
        }
    }

    suspend fun clearPin() {
        context.settingsDataStore.edit {
            it.remove(Keys.PIN_SALT)
            it.remove(Keys.PIN_HASH)
            it[Keys.BIOMETRIC] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = data.first()
        val hash = prefs[Keys.PIN_HASH] ?: return false
        val salt = prefs[Keys.PIN_SALT] ?: return false
        return withContext(Dispatchers.Default) { PinHasher.verify(pin, salt, hash) }
    }
}
