package com.atomicvault.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.atomicvault.android.model.*
import com.atomicvault.android.security.PasswordAnalysis
import com.atomicvault.android.storage.VaultStorage
import com.atomicvault.android.trust.TrustLedger
import com.atomicvault.android.ui.screens.*
import com.atomicvault.android.ui.theme.AtomicVaultTheme
import com.atomicvault.android.ui.theme.BackgroundDark

enum class AppScreen {
    VAULT_LIST,
    CREDENTIAL_EDITOR,
    SECURITY_AUDIT,
    TRUST_TIMELINE,
    SETTINGS
}

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        VaultStorage.init(applicationContext)

        setContent {
            AtomicVaultTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    VaultAppRoot(
                        onTriggerBiometric = { onSuccess, onError ->
                            showBiometricPrompt(onSuccess, onError)
                        }
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Handle Auto-lock
        val settings = VaultStorage.currentVaultData?.settings
        if (settings?.autoLockTimeout == AutoLockTimeout.IMMEDIATELY) {
            VaultStorage.lockVault()
        }
    }

    private fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric authentication failed")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AtomicVault")
            .setSubtitle("Authenticate using your biometric credentials")
            .setNegativeButtonText("Use Password")
            .build()

        prompt.authenticate(promptInfo)
    }
}

@Composable
fun VaultAppRoot(
    onTriggerBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var isInitialized by remember { mutableStateOf(VaultStorage.isVaultInitialized()) }
    var isUnlocked by remember { mutableStateOf(VaultStorage.currentVaultData != null) }
    var hasBiometricArmed by remember { mutableStateOf(VaultStorage.isBiometricArmed()) }

    var unlockErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentScreen by remember { mutableStateOf(AppScreen.VAULT_LIST) }
    var editingItem by remember { mutableStateOf<CredentialItem?>(null) }
    var newItemType by remember { mutableStateOf(VaultItemType.LOGIN) }

    // Vault Data reactive state
    var vaultData by remember { mutableStateOf(VaultStorage.currentVaultData ?: VaultData()) }

    fun refreshState() {
        isInitialized = VaultStorage.isVaultInitialized()
        hasBiometricArmed = VaultStorage.isBiometricArmed()
        val data = VaultStorage.currentVaultData
        isUnlocked = data != null
        if (data != null) {
            vaultData = data
        }
    }

    if (!isInitialized) {
        OnboardingScreen(
            onCreateVault = { password, enableBiometric, seedSamples ->
                val data = VaultStorage.initVault(password, enableBiometric, seedSamples)
                vaultData = data
                refreshState()
                currentScreen = AppScreen.VAULT_LIST
            }
        )
    } else if (!isUnlocked) {
        UnlockScreen(
            hasBiometric = hasBiometricArmed,
            errorMessage = unlockErrorMessage,
            onUnlockPassword = { password ->
                try {
                    val data = VaultStorage.unlockWithPassword(password)
                    vaultData = data
                    unlockErrorMessage = null
                    refreshState()
                    currentScreen = AppScreen.VAULT_LIST
                } catch (e: Exception) {
                    unlockErrorMessage = "Invalid master password"
                }
            },
            onUnlockBiometric = {
                onTriggerBiometric(
                    {
                        try {
                            val data = VaultStorage.unlockWithBiometric()
                            vaultData = data
                            unlockErrorMessage = null
                            refreshState()
                            currentScreen = AppScreen.VAULT_LIST
                        } catch (e: Exception) {
                            unlockErrorMessage = "Biometric decryption failed: ${e.message}"
                        }
                    },
                    { err ->
                        unlockErrorMessage = err
                    }
                )
            }
        )
    } else {
        // Main App Navigation
        when (currentScreen) {
            AppScreen.VAULT_LIST -> {
                VaultListScreen(
                    vaultData = vaultData,
                    onSelectItem = { item ->
                        editingItem = item
                        currentScreen = AppScreen.CREDENTIAL_EDITOR
                    },
                    onAddItem = { type ->
                        editingItem = null
                        newItemType = type
                        currentScreen = AppScreen.CREDENTIAL_EDITOR
                    },
                    onOpenAudit = { currentScreen = AppScreen.SECURITY_AUDIT },
                    onOpenTimeline = { currentScreen = AppScreen.TRUST_TIMELINE },
                    onOpenGenerator = { currentScreen = AppScreen.SETTINGS },
                    onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                    onLockVault = {
                        VaultStorage.lockVault()
                        refreshState()
                    },
                    onCreateFolder = { name ->
                        VaultStorage.addFolder(name)
                        refreshState()
                    }
                )
            }

            AppScreen.CREDENTIAL_EDITOR -> {
                CredentialEditorScreen(
                    initialItem = editingItem,
                    initialType = newItemType,
                    folders = vaultData.folders,
                    availableTags = vaultData.tags,
                    onSave = { input ->
                        if (editingItem != null) {
                            VaultStorage.updateCredential(editingItem!!.id, input)
                        } else {
                            VaultStorage.addCredential(input)
                        }
                        refreshState()
                        currentScreen = AppScreen.VAULT_LIST
                    },
                    onDelete = if (editingItem != null) {
                        {
                            VaultStorage.deleteCredential(editingItem!!.id)
                            refreshState()
                            currentScreen = AppScreen.VAULT_LIST
                        }
                    } else null,
                    onBack = { currentScreen = AppScreen.VAULT_LIST }
                )
            }

            AppScreen.SECURITY_AUDIT -> {
                val auditReport = remember(vaultData.items) {
                    PasswordAnalysis.auditVault(vaultData.items)
                }
                SecurityAuditScreen(
                    auditReport = auditReport,
                    allItems = vaultData.items,
                    onSelectItem = { item ->
                        editingItem = item
                        currentScreen = AppScreen.CREDENTIAL_EDITOR
                    },
                    onBack = { currentScreen = AppScreen.VAULT_LIST }
                )
            }

            AppScreen.TRUST_TIMELINE -> {
                TrustTimelineScreen(
                    onBack = { currentScreen = AppScreen.VAULT_LIST }
                )
            }

            AppScreen.SETTINGS -> {
                SettingsScreen(
                    settings = vaultData.settings,
                    onUpdateSettings = { patch ->
                        VaultStorage.updateSettings(patch)
                        refreshState()
                    },
                    onResetVault = {
                        VaultStorage.resetVault()
                        refreshState()
                    },
                    onBack = { currentScreen = AppScreen.VAULT_LIST }
                )
            }
        }
    }
}
