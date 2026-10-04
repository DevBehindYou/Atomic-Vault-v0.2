package com.example.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupCodec
import com.example.crypto.Argon2Kdf
import com.example.crypto.DekCodec
import com.example.crypto.MasterPassword
import com.example.database.CredentialInput
import com.example.database.CredentialPlain
import com.example.database.CredentialPreview
import com.example.database.FolderPlain
import com.example.database.SqlcipherGuard
import com.example.database.TagPlain
import com.example.database.VaultDatabase
import com.example.database.VaultExport
import com.example.database.VaultRepository
import com.example.database.VaultLockedException
import com.example.database.VaultSession
import com.example.database.VaultSettingsPatch
import com.example.database.VaultSettingsPlain
import com.example.keystore.BiometricGatedKeyStore
import com.example.keystore.KdfParams
import com.example.keystore.VaultMetaStore
import com.example.security.DeviceIntegrity
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays
import javax.crypto.Cipher

enum class VaultStatus {
    LOADING, ONBOARDING, LOCKED, UNLOCKED
}

data class VaultUiState(
    val status: VaultStatus = VaultStatus.LOADING,
    val biometricArmed: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val previews: List<CredentialPreview> = emptyList(),
    val folders: List<FolderPlain> = emptyList(),
    val tags: List<TagPlain> = emptyList(),
    val tagFilter: String? = null,
    val settings: VaultSettingsPlain? = null,
    val query: String = "",
    val folderFilter: String? = null,
    val autofillSupported: Boolean = true,
    val autofillArmed: Boolean = false,
    val integrityWarnings: List<String> = emptyList(),
    /** One-time explanation for users upgrading from a version that shipped the Atomic keyboard. */
    val showKeyboardRemovedNotice: Boolean = false
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val metaStore = VaultMetaStore(application)

    // Single Keystore-backed store shared by the app's own biometric
    // unlock AND the autofill service's biometric reveal -- see
    // BiometricGatedKeyStore's doc comment for why these were consolidated
    // and why the key itself, not app code, now enforces the auth check.
    private val keyStore = BiometricGatedKeyStore(application)

    // The open vault (key, connection, repository) lives in VaultSession so
    // locking can wait for in-flight work instead of zeroing the key under it.

    /** Failures in background vault work end up on screen, never as a crash. */
    private val errors = CoroutineExceptionHandler { _, e ->
        _uiState.update { it.copy(busy = false, error = describe(e)) }
    }

    /** The vault before the last restore, decrypted, in memory only; dropped on lock. */
    @Volatile private var undoSnapshot: VaultExport? = null

    /** The latest list/search load; a newer one cancels it so results never arrive out of order. */
    private var previewJob: Job? = null

    // Non-secret app flags that must be readable before unlock.
    private val appPrefs = application.getSharedPreferences(APP_PREFS, android.content.Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        checkInitialState()
    }

    fun checkInitialState() {
        val hasVault = metaStore.hasVault()
        // Same underlying Keystore entry now gates both -- see keyStore's doc comment.
        val bioArmed = keyStore.isArmed()
        val autofillArmed = keyStore.isArmed()
        val warnings = DeviceIntegrity.checkIntegrity()

        _uiState.update {
            it.copy(
                // An unreadable key store is never "no vault": showing
                // onboarding there would invite creating a vault over the
                // real one.
                status = if (!hasVault && !metaStore.isUnavailable) VaultStatus.ONBOARDING else VaultStatus.LOCKED,
                // A vault that already exists was made by a version that
                // shipped the Atomic keyboard; a fresh install never sees this.
                showKeyboardRemovedNotice = hasVault && !appPrefs.getBoolean(PREF_KEYBOARD_NOTICE_SEEN, false),
                biometricArmed = bioArmed,
                autofillArmed = autofillArmed,
                autofillSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                integrityWarnings = warnings,
                error = if (metaStore.isUnavailable) KEY_STORE_UNAVAILABLE else null
            )
        }
    }

    fun dismissKeyboardRemovedNotice() {
        appPrefs.edit().putBoolean(PREF_KEYBOARD_NOTICE_SEEN, true).apply()
        _uiState.update { it.copy(showKeyboardRemovedNotice = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun createVault(password: String, biometricEnabled: Boolean, onComplete: (Boolean) -> Unit) {
        if (password.length < 8) {
            _uiState.update { it.copy(error = "Password must be at least 8 characters") }
            onComplete(false)
            return
        }

        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                moveAsideOrphanedDatabase()
                val salt = Argon2Kdf.generateSaltBase64()
                val kek = Argon2Kdf.deriveKek(MasterPassword.normalize(password), salt)
                val dek = DekCodec.generateDek()
                val wrappedDek = DekCodec.wrapDek(kek, dek)
                Arrays.fill(kek, 0.toByte())
                val kdfParamsJson = VaultMetaStore.createDefaultKdfParamsJson(salt)

                metaStore.saveVaultEnvelope(salt, kdfParamsJson, wrappedDek)
                appPrefs.edit().putBoolean(PREF_KEYBOARD_NOTICE_SEEN, true).apply()

                // NOTE: biometric arming is NOT done here anymore. Wrapping the
                // DEK with the biometric-gated Keystore key requires a live,
                // successful BiometricPrompt (see BiometricGatedKeyStore) --
                // it can't happen synchronously inside vault creation. If the
                // caller requested biometricEnabled, they're expected to follow
                // up with beginBiometricArm()/completeBiometricArm() right
                // after this completes (OnboardingScreen does this).
                keyStore.clear()

                VaultSession.unlock(getApplication(), dek)
                VaultSession.use { it.repository.updateSettings(VaultSettingsPatch(autoLockSeconds = 60, biometricEnabled = false)) }
                val (previews, folders, tags, settings) = loadSnapshot()

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            status = VaultStatus.UNLOCKED,
                            busy = false,
                            biometricArmed = false,
                            previews = previews,
                            folders = folders,
                            tags = tags,
                            settings = settings,
                            error = null
                        )
                    }
                    onComplete(true)
                }
                TrustLedger.record(getApplication(), TrustEventType.VAULT_CREATED, authenticationType = "master_password")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(busy = false, error = "Failed to create vault: ${e.message}") }
                    onComplete(false)
                }
            }
        }
    }

    fun unlockWithPassword(password: String, onComplete: (Boolean) -> Unit) {
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val envelope = metaStore.getVaultEnvelope()
                if (envelope == null) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(busy = false, error = if (metaStore.isUnavailable) KEY_STORE_UNAVAILABLE else "Vault envelope not found") }
                        onComplete(false)
                    }
                    return@launch
                }

                // The parameters this vault was created with, not today's defaults.
                val kdf = KdfParams.parse(envelope.kdfParamsJson)
                val unlocked = MasterPassword.unlock(
                    password = password,
                    derive = { candidate -> deriveWith(kdf, candidate, envelope.saltBase64) },
                    unwrap = { kek -> DekCodec.unwrapDek(kek, envelope.wrappedDek) }
                )
                if (unlocked == null) {
                    TrustLedger.record(
                        getApplication(), TrustEventType.VAULT_UNLOCK_FAILED,
                        authenticationType = "master_password", result = "failure"
                    )
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(busy = false, error = "Incorrect master password") }
                        onComplete(false)
                    }
                    return@launch
                }
                val dek = unlocked.dek
                if (unlocked.needsRewrap) {
                    // Created before passwords were normalized, from a
                    // decomposed form: wrap the same DEK under the canonical
                    // form once, so any keyboard opens it from now on.
                    val kek = deriveWith(kdf, MasterPassword.normalize(password), envelope.saltBase64)
                    try {
                        metaStore.saveVaultEnvelope(envelope.saltBase64, envelope.kdfParamsJson, DekCodec.wrapDek(kek, dek))
                    } finally {
                        Arrays.fill(kek, 0.toByte())
                    }
                }

                VaultSession.unlock(getApplication(), dek)
                val (previews, folders, tags, settings) = loadSnapshot()

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            status = VaultStatus.UNLOCKED,
                            busy = false,
                            previews = previews,
                            folders = folders,
                            tags = tags,
                            settings = settings,
                            error = null
                        )
                    }
                    onComplete(true)
                }
                TrustLedger.record(getApplication(), TrustEventType.VAULT_UNLOCKED, authenticationType = "master_password")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(busy = false, error = "Could not open the vault: ${e.message ?: e.javaClass.simpleName}") }
                    onComplete(false)
                }
            }
        }
    }

    /**
     * Step 1 of biometric unlock: get a Cipher to pass into
     * BiometricPrompt.CryptoObject. Returns null if biometric unlock isn't
     * armed. The screen calling this owns showing the actual prompt (via
     * AppBiometricManager.promptBiometricAuthForCrypto) and must call
     * [unlockWithBiometric] with the Cipher the prompt hands back on success.
     */
    fun prepareBiometricUnlockCipher(): Cipher? = keyStore.beginReveal()

    /** Step 2 of biometric unlock. [authenticatedCipher] must come from a successful BiometricPrompt result. */
    fun unlockWithBiometric(authenticatedCipher: Cipher, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val dek = keyStore.finishReveal(authenticatedCipher)
                if (dek == null) {
                    TrustLedger.record(
                        getApplication(), TrustEventType.VAULT_UNLOCK_FAILED,
                        authenticationType = "biometric", result = "failure"
                    )
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                VaultSession.unlock(getApplication(), dek)
                val (previews, folders, tags, settings) = loadSnapshot()

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            status = VaultStatus.UNLOCKED,
                            busy = false,
                            previews = previews,
                            folders = folders,
                            tags = tags,
                            settings = settings,
                            error = null
                        )
                    }
                    onComplete(true)
                }
                TrustLedger.record(getApplication(), TrustEventType.VAULT_UNLOCKED, authenticationType = "biometric")
            } catch (e: Exception) {
                TrustLedger.record(
                    getApplication(), TrustEventType.VAULT_UNLOCK_FAILED,
                    authenticationType = "biometric", result = "failure"
                )
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun lockVault() {
        // A locked vault should not leave a copied password sitting on the clipboard.
        com.example.security.ClipboardHelper.clearIfOwned(getApplication())
        previewJob?.cancel()
        undoSnapshot = null
        // Stops new work now; the database closes and the key is zeroed as
        // soon as any save or export still running has finished.
        VaultSession.lock()

        _uiState.update {
            it.copy(
                status = VaultStatus.LOCKED,
                previews = emptyList(),
                folders = emptyList(),
                // tags/tagFilter/settings were previously left populated on
                // lock while previews and folders were cleared. Nothing here
                // is a decrypted secret, but leaving a half-cleared vault in
                // state meant the locked UI could still render real vault
                // contents (tag names) and stale settings.
                tags = emptyList(),
                tagFilter = null,
                settings = null,
                query = "",
                folderFilter = null,
                error = null
            )
        }
        TrustLedger.record(getApplication(), TrustEventType.VAULT_LOCKED)
    }

    private data class Snapshot(
        val previews: List<CredentialPreview>,
        val folders: List<FolderPlain>,
        val tags: List<TagPlain>,
        val settings: VaultSettingsPlain
    )

    private fun loadSnapshot(): Snapshot = VaultSession.use { session ->
        val repo = session.repository
        val state = _uiState.value
        Snapshot(
            previews = repo.listPreviews(state.folderFilter, state.query, state.tagFilter),
            folders = repo.listFolders(),
            tags = repo.listTags(),
            settings = repo.getSettings()
        )
    }

    /** Runs vault work off the main thread; a locked vault or a failure is reported, never thrown. */
    private fun launchVaultWork(block: suspend (VaultRepository) -> Unit) {
        if (!VaultSession.isUnlocked) {
            _uiState.update { it.copy(error = "The vault is locked") }
            return
        }
        viewModelScope.launch(Dispatchers.IO + errors) {
            VaultSession.use { session -> block(session.repository) }
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is VaultLockedException -> "The vault is locked"
        else -> e.message ?: e.javaClass.simpleName
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        // Typing fires this per keystroke: wait for a short pause first.
        reloadPreviews(debounceMs = 150)
    }

    fun setFolderFilter(folderId: String?) {
        _uiState.update { it.copy(folderFilter = folderId) }
        reloadPreviews()
    }

    fun setTagFilter(tagId: String?) {
        _uiState.update { it.copy(tagFilter = tagId) }
        reloadPreviews()
    }

    fun reloadVaultData() {
        if (!VaultSession.isUnlocked) return
        viewModelScope.launch(Dispatchers.IO + errors) {
            val snapshot = loadSnapshot()
            _uiState.update {
                it.copy(
                    previews = snapshot.previews,
                    folders = snapshot.folders,
                    tags = snapshot.tags,
                    settings = snapshot.settings
                )
            }
        }
    }

    private fun reloadPreviews(debounceMs: Long = 0) {
        if (!VaultSession.isUnlocked) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.IO + errors) {
            if (debounceMs > 0) delay(debounceMs)
            val state = _uiState.value
            val previews = VaultSession.use { it.repository.listPreviews(state.folderFilter, state.query, state.tagFilter) }
            // A newer query may have started while this one ran.
            ensureActive()
            _uiState.update { it.copy(previews = previews) }
        }
    }

    fun createTag(name: String, onDone: (TagPlain) -> Unit = {}) {
        if (name.isBlank()) return
        launchVaultWork { repo ->
            val tag = repo.createTag(name)
            val tags = repo.listTags()
            _uiState.update { it.copy(tags = tags) }
            withContext(Dispatchers.Main) { onDone(tag) }
        }
    }

    fun deleteTag(id: String) {
        launchVaultWork { repo ->
            repo.deleteTag(id)
            val tags = repo.listTags()
            val clearFilter = _uiState.value.tagFilter == id
            _uiState.update {
                it.copy(tags = tags, tagFilter = if (clearFilter) null else it.tagFilter)
            }
            reloadPreviews()
        }
    }

    /**
     * Loads one full credential, including decrypting every field plus its
     * custom fields and tags -- real SQLCipher I/O and several AES-GCM
     * operations, so it suspends onto IO. Null if the vault is locked.
     */
    suspend fun getItem(id: String): CredentialPlain? = withContext(Dispatchers.IO) {
        VaultSession.useIfUnlocked { it.repository.getItem(id) }
    }

    fun createItem(input: CredentialInput, onDone: () -> Unit) {
        launchVaultWork { repo ->
            repo.createItem(input)
            reloadVaultData()
            TrustLedger.record(getApplication(), TrustEventType.CREDENTIAL_CREATED, source = "app")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun updateItem(id: String, input: CredentialInput, onDone: () -> Unit) {
        launchVaultWork { repo ->
            repo.updateItem(id, input)
            reloadVaultData()
            TrustLedger.record(getApplication(), TrustEventType.CREDENTIAL_MODIFIED, subjectReference = id, source = "app")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun deleteItem(id: String, onDone: () -> Unit) {
        launchVaultWork { repo ->
            repo.deleteItem(id)
            reloadVaultData()
            TrustLedger.record(getApplication(), TrustEventType.CREDENTIAL_DELETED, subjectReference = id, source = "app")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun createFolder(name: String) {
        launchVaultWork { repo ->
            repo.createFolder(name)
            val folders = repo.listFolders()
            _uiState.update { it.copy(folders = folders) }
        }
    }

    fun deleteFolder(id: String) {
        launchVaultWork { repo ->
            repo.deleteFolder(id)
            reloadVaultData()
        }
    }

    /**
     * Step 1 of enabling biometric unlock/autofill from Settings or
     * Onboarding. Returns null if the vault isn't currently unlocked (no
     * DEK to wrap). Pass the returned Cipher into
     * AppBiometricManager.promptBiometricAuthForCrypto, then call
     * [completeBiometricArm] with the authenticated Cipher on success.
     */
    fun beginBiometricArm(): Cipher? {
        if (!VaultSession.isUnlocked) return null
        return keyStore.beginArming()
    }

    /**
     * Step 2 of enabling biometric unlock/autofill. [authenticatedCipher]
     * must come from a successful BiometricPrompt result. This arms BOTH
     * the app's biometric unlock and the autofill service's biometric
     * reveal together -- they now share one Keystore-bound key (see
     * BiometricGatedKeyStore), so there's no meaningful difference between
     * "biometric unlock is on" and "autofill can reveal via biometric."
     */
    fun completeBiometricArm(authenticatedCipher: Cipher) {
        VaultSession.useIfUnlocked { keyStore.finishArming(authenticatedCipher, it.dek) } ?: return
        viewModelScope.launch(Dispatchers.IO + errors) {
            val updated = VaultSession.useIfUnlocked { it.repository.updateSettings(VaultSettingsPatch(biometricEnabled = true)) }
            _uiState.update {
                it.copy(
                    settings = updated ?: it.settings,
                    biometricArmed = true,
                    autofillArmed = true
                )
            }
            TrustLedger.record(getApplication(), TrustEventType.BIOMETRIC_ENABLED)
        }
    }

    /** Shows a biometric failure on the unlock screen without touching the keystore. */
    fun reportBiometricError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    /**
     * Called when the unlock screen asked for a biometric Cipher and got
     * none. Either the key is gone (a new fingerprint was enrolled, so the
     * Keystore invalidated it) -- in which case the UI must stop offering
     * biometrics -- or the key store failed transiently and stays armed.
     */
    fun onBiometricUnavailable() {
        val stillArmed = keyStore.isArmed()
        _uiState.update {
            it.copy(
                biometricArmed = stillArmed,
                autofillArmed = stillArmed,
                error = if (stillArmed) {
                    "Biometric unlock is unavailable right now. Use your master password."
                } else {
                    "Biometric unlock was reset (your fingerprints changed). " +
                        "Unlock with your master password, then turn it back on in Settings."
                }
            )
        }
    }

    /** Disarms biometric unlock and autofill reveal together (one shared key -- see keyStore's doc comment). */
    fun disableBiometric() {
        keyStore.clear()
        viewModelScope.launch(Dispatchers.IO + errors) {
            val updated = VaultSession.useIfUnlocked { it.repository.updateSettings(VaultSettingsPatch(biometricEnabled = false)) }
            _uiState.update {
                it.copy(
                    settings = updated ?: it.settings,
                    biometricArmed = false,
                    autofillArmed = false
                )
            }
            TrustLedger.record(getApplication(), TrustEventType.BIOMETRIC_DISABLED)
        }
    }

    /**
     * Live check for the Privacy Proof screen -- verifies SQLCipher
     * against the ACTUAL currently-open vault database, not a hardcoded
     * claim. Returns false (not "unknown") when the vault is locked,
     * since there's nothing open to verify; the screen should present
     * that as "unlock to verify," not as a failed check.
     */
    fun isSqlcipherVerified(): Boolean {
        return VaultSession.useIfUnlocked { session ->
            try {
                SqlcipherGuard.assertSqlcipherActive(session.db)
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    fun updateAutoLockSeconds(autoLockSeconds: Int) {
        launchVaultWork { repo ->
            val updated = repo.updateSettings(VaultSettingsPatch(autoLockSeconds = autoLockSeconds))
            _uiState.update { it.copy(settings = updated) }
        }
    }

    fun exportBackup(passphrase: String, onResult: (Result<ByteArray>) -> Unit) {
        if (!VaultSession.isUnlocked) {
            onResult(Result.failure(VaultLockedException()))
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val exportData = VaultSession.use { it.repository.exportData() }
                val backupBytes = BackupCodec.exportBackup(exportData, passphrase.toCharArray())
                withContext(Dispatchers.Main) { onResult(Result.success(backupBytes)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
            }
        }
    }

    fun importBackup(backupBytes: ByteArray, passphrase: String, onResult: (Result<Int>) -> Unit) {
        if (!VaultSession.isUnlocked) {
            onResult(Result.failure(VaultLockedException()))
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val data = BackupCodec.importBackup(backupBytes, passphrase.toCharArray())
                VaultSession.use { session ->
                    // Keep the current vault (in memory only, never on disk)
                    // so the restore can be undone until the vault locks.
                    val before = session.repository.exportData()
                    session.repository.importReplace(data)
                    undoSnapshot = before
                }
                reloadVaultData()
                withContext(Dispatchers.Main) { onResult(Result.success(data.items.size)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
            }
        }
    }

    /** Puts back the vault as it was before the last restore. */
    fun undoLastRestore(onResult: (Result<Int>) -> Unit) {
        val before = undoSnapshot
        if (before == null || !VaultSession.isUnlocked) {
            onResult(Result.failure(IllegalStateException("Nothing to undo")))
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                VaultSession.use { it.repository.importReplace(before) }
                undoSnapshot = null
                reloadVaultData()
                withContext(Dispatchers.Main) { onResult(Result.success(before.items.size)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
            }
        }
    }

    /**
     * Decrypts EVERY credential in the vault for the security dashboard's
     * reuse/weakness analysis. This is by far the heaviest read in the
     * app -- one query per item plus an AES-GCM open per field -- so it
     * suspends onto the IO dispatcher rather than blocking the frame it
     * was called from.
     */
    suspend fun getAllCredentialsForSecurity(): List<CredentialPlain> = withContext(Dispatchers.IO) {
        VaultSession.useIfUnlocked { it.repository.exportData().items } ?: emptyList()
    }

    private fun deriveWith(kdf: KdfParams, password: String, saltBase64: String): ByteArray {
        val chars = password.toCharArray()
        try {
            return Argon2Kdf.deriveKek(chars, saltBase64, kdf.memoryKiB, kdf.iterations, kdf.parallelism)
        } finally {
            Arrays.fill(chars, '\u0000')
        }
    }

    /**
     * A database file with no envelope pointing at it cannot be opened by
     * anyone (its key is gone), but it is the user's data. Never overwrite or
     * delete it: rename it aside so a new vault can be created next to it.
     */
    private fun moveAsideOrphanedDatabase() {
        val file = VaultDatabase.getDatabaseFile(getApplication())
        if (!file.exists() || metaStore.hasVault()) return
        val stamp = System.currentTimeMillis()
        for (suffix in listOf("", "-journal", "-wal", "-shm")) {
            val f = java.io.File(file.path + suffix)
            if (f.exists()) f.renameTo(java.io.File("${file.path}.orphaned-$stamp$suffix"))
        }
    }

    override fun onCleared() {
        // The screen that owned this vault session is gone for good (not a
        // rotation): lock rather than leave the key in memory with no UI.
        VaultSession.lock()
        super.onCleared()
    }

    private companion object {
        const val KEY_STORE_UNAVAILABLE = "This phone can't open AtomicVault's key store right now. " +
            "Restart the phone and try again. Your vault has not been changed."
        const val APP_PREFS = "atomicvault_app_prefs"
        const val PREF_KEYBOARD_NOTICE_SEEN = "notice_keyboard_removed_seen"
    }
}
