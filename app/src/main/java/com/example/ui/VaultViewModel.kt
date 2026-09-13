package com.example.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupCodec
import com.example.crypto.Argon2Kdf
import com.example.crypto.DekCodec
import com.example.database.CredentialInput
import com.example.database.CredentialPlain
import com.example.database.CredentialPreview
import com.example.database.FolderPlain
import com.example.database.SqlcipherGuard
import com.example.database.TagPlain
import com.example.database.VaultDatabase
import com.example.database.VaultExport
import com.example.database.VaultRepository
import com.example.database.VaultRepositoryImpl
import com.example.database.VaultSettingsPatch
import com.example.database.VaultSettingsPlain
import com.example.keystore.BiometricGatedKeyStore
import com.example.keystore.VaultMetaStore
import com.example.security.DeviceIntegrity
import com.example.trust.TrustEventType
import com.example.trust.TrustLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sqlcipher.database.SQLiteDatabase
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
    val integrityWarnings: List<String> = emptyList()
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val metaStore = VaultMetaStore(application)

    // Single Keystore-backed store shared by the app's own biometric
    // unlock AND the autofill service's biometric reveal -- see
    // BiometricGatedKeyStore's doc comment for why these were consolidated
    // and why the key itself, not app code, now enforces the auth check.
    private val keyStore = BiometricGatedKeyStore(application)

    private var activeDb: SQLiteDatabase? = null
    private var activeDek: ByteArray? = null
    private var repository: VaultRepository? = null

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
                status = if (!hasVault) VaultStatus.ONBOARDING else VaultStatus.LOCKED,
                biometricArmed = bioArmed,
                autofillArmed = autofillArmed,
                autofillSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
                integrityWarnings = warnings,
                error = null
            )
        }
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
                val salt = Argon2Kdf.generateSaltBase64()
                val kek = Argon2Kdf.deriveKek(password, salt)
                val dek = DekCodec.generateDek()
                val wrappedDek = DekCodec.wrapDek(kek, dek)
                val kdfParamsJson = VaultMetaStore.createDefaultKdfParamsJson(salt)

                metaStore.saveVaultEnvelope(salt, kdfParamsJson, wrappedDek)

                // NOTE: biometric arming is NOT done here anymore. Wrapping the
                // DEK with the biometric-gated Keystore key requires a live,
                // successful BiometricPrompt (see BiometricGatedKeyStore) --
                // it can't happen synchronously inside vault creation. If the
                // caller requested biometricEnabled, they're expected to follow
                // up with beginBiometricArm()/completeBiometricArm() right
                // after this completes (OnboardingScreen does this).
                keyStore.clear()

                val db = VaultDatabase.open(getApplication(), dek)
                val repo = VaultRepositoryImpl(db, dek)
                repo.updateSettings(VaultSettingsPatch(autoLockSeconds = 60, biometricEnabled = false))

                activeDb = db
                activeDek = dek
                repository = repo

                val previews = repo.listPreviews()
                val folders = repo.listFolders()
                val tags = repo.listTags()
                val settings = repo.getSettings()

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
                        _uiState.update { it.copy(busy = false, error = "Vault envelope not found") }
                        onComplete(false)
                    }
                    return@launch
                }

                val kek = Argon2Kdf.deriveKek(password, envelope.saltBase64)
                val dek = try {
                    DekCodec.unwrapDek(kek, envelope.wrappedDek)
                } catch (e: Exception) {
                    Arrays.fill(kek, 0.toByte())
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
                // KEK's only job was unwrapping the DEK above -- clear it now
                // rather than leaving it reachable for the rest of this scope.
                Arrays.fill(kek, 0.toByte())

                val db = VaultDatabase.open(getApplication(), dek)
                val repo = VaultRepositoryImpl(db, dek)

                activeDb = db
                activeDek = dek
                repository = repo

                val previews = repo.listPreviews()
                val folders = repo.listFolders()
                val tags = repo.listTags()
                val settings = repo.getSettings()

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
                    _uiState.update { it.copy(busy = false, error = "Incorrect master password") }
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

                val db = VaultDatabase.open(getApplication(), dek)
                val repo = VaultRepositoryImpl(db, dek)

                activeDb = db
                activeDek = dek
                repository = repo

                val previews = repo.listPreviews()
                val folders = repo.listFolders()
                val tags = repo.listTags()
                val settings = repo.getSettings()

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
        try {
            activeDb?.close()
        } catch (e: Exception) {
            // Ignore close exceptions
        }
        activeDek?.let { Arrays.fill(it, 0.toByte()) }
        activeDb = null
        activeDek = null
        repository = null

        _uiState.update {
            it.copy(
                status = VaultStatus.LOCKED,
                previews = emptyList(),
                folders = emptyList(),
                query = "",
                folderFilter = null,
                error = null
            )
        }
        TrustLedger.record(getApplication(), TrustEventType.VAULT_LOCKED)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        reloadPreviews()
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
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val previews = repo.listPreviews(state.folderFilter, state.query, state.tagFilter)
            val folders = repo.listFolders()
            val tags = repo.listTags()
            val settings = repo.getSettings()
            _uiState.update {
                it.copy(
                    previews = previews,
                    folders = folders,
                    tags = tags,
                    settings = settings
                )
            }
        }
    }

    private fun reloadPreviews() {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val previews = repo.listPreviews(state.folderFilter, state.query, state.tagFilter)
            _uiState.update { it.copy(previews = previews) }
        }
    }

    fun createTag(name: String, onDone: (TagPlain) -> Unit = {}) {
        val repo = repository ?: return
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val tag = repo.createTag(name)
            val tags = repo.listTags()
            _uiState.update { it.copy(tags = tags) }
            withContext(Dispatchers.Main) { onDone(tag) }
        }
    }

    fun deleteTag(id: String) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteTag(id)
            val tags = repo.listTags()
            val clearFilter = _uiState.value.tagFilter == id
            _uiState.update {
                it.copy(tags = tags, tagFilter = if (clearFilter) null else it.tagFilter)
            }
            reloadPreviews()
        }
    }

    fun getItem(id: String): CredentialPlain? {
        return repository?.getItem(id)
    }

    fun createItem(input: CredentialInput, onDone: () -> Unit) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.createItem(input)
            reloadVaultData()
            TrustLedger.record(getApplication(), TrustEventType.CREDENTIAL_CREATED, source = "app")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun updateItem(id: String, input: CredentialInput, onDone: () -> Unit) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateItem(id, input)
            reloadVaultData()
            TrustLedger.record(getApplication(), TrustEventType.CREDENTIAL_MODIFIED, subjectReference = id, source = "app")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun deleteItem(id: String, onDone: () -> Unit) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteItem(id)
            reloadVaultData()
            TrustLedger.record(getApplication(), TrustEventType.CREDENTIAL_DELETED, subjectReference = id, source = "app")
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun createFolder(name: String) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.createFolder(name)
            val folders = repo.listFolders()
            _uiState.update { it.copy(folders = folders) }
        }
    }

    fun deleteFolder(id: String) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
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
        if (activeDek == null) return null
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
        val dek = activeDek ?: return
        keyStore.finishArming(authenticatedCipher, dek)
        val repo = repository
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repo?.updateSettings(VaultSettingsPatch(biometricEnabled = true))
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

    /** Disarms biometric unlock and autofill reveal together (one shared key -- see keyStore's doc comment). */
    fun disableBiometric() {
        keyStore.clear()
        val repo = repository
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repo?.updateSettings(VaultSettingsPatch(biometricEnabled = false))
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
        val db = activeDb ?: return false
        return try {
            SqlcipherGuard.assertSqlcipherActive(db)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun updateAutoLockSeconds(autoLockSeconds: Int) {
        val repo = repository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repo.updateSettings(VaultSettingsPatch(autoLockSeconds = autoLockSeconds))
            _uiState.update { it.copy(settings = updated) }
        }
    }

    fun exportBackup(passphrase: String, onResult: (Result<ByteArray>) -> Unit) {
        val repo = repository
        if (repo == null) {
            onResult(Result.failure(IllegalStateException("Vault is locked")))
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val exportData = repo.exportData()
                val backupBytes = BackupCodec.exportBackup(exportData, passphrase.toCharArray())
                withContext(Dispatchers.Main) { onResult(Result.success(backupBytes)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
            }
        }
    }

    fun importBackup(backupBytes: ByteArray, passphrase: String, onResult: (Result<Int>) -> Unit) {
        val repo = repository
        if (repo == null) {
            onResult(Result.failure(IllegalStateException("Vault is locked")))
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val data = BackupCodec.importBackup(backupBytes, passphrase.toCharArray())
                repo.importReplace(data)
                reloadVaultData()
                withContext(Dispatchers.Main) { onResult(Result.success(data.items.size)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(Result.failure(e)) }
            }
        }
    }

    fun getAllCredentialsForSecurity(): List<CredentialPlain> {
        val repo = repository ?: return emptyList()
        val export = repo.exportData()
        return export.items
    }
}
