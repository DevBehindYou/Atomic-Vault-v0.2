package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.security.AppBiometricManager
import com.example.ui.backup.BackupScreen
import com.example.ui.editor.CredentialEditorScreen
import com.example.ui.generator.PasswordGeneratorScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.security.SecurityDashboardScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.unlock.UnlockScreen
import com.example.ui.vaulthome.VaultHomeScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Unlock : Screen("unlock")
    object Home : Screen("home")
    object Editor : Screen("editor?itemId={itemId}") {
        fun createRoute(itemId: String? = null): String {
            return if (itemId != null) "editor?itemId=$itemId" else "editor"
        }
    }
    object PaymentCardEditor : Screen("payment_card_editor?itemId={itemId}") {
        fun createRoute(itemId: String? = null): String {
            return if (itemId != null) "payment_card_editor?itemId=$itemId" else "payment_card_editor"
        }
    }
    object IdentityEditor : Screen("identity_editor?itemId={itemId}") {
        fun createRoute(itemId: String? = null): String {
            return if (itemId != null) "identity_editor?itemId=$itemId" else "identity_editor"
        }
    }
    object Generator : Screen("generator")
    object Security : Screen("security")
    object Settings : Screen("settings")
    object Backup : Screen("backup")
    object PrivacyProof : Screen("privacy_proof")
    object SecurityTimeline : Screen("security_timeline")
}

@Composable
fun AtomicVaultNavGraph(
    navController: NavHostController,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as? FragmentActivity

    // Shared helper: run the begin-cipher -> biometric-prompt -> complete-arm
    // sequence used by both Onboarding (initial opt-in) and Settings
    // (enabling it later). Requires a FragmentActivity context, which
    // MainActivity provides.
    fun armBiometricWithPrompt(onDone: (Boolean) -> Unit) {
        val cipher = viewModel.beginBiometricArm()
        val act = activity
        if (cipher == null || act == null) {
            onDone(false)
            return
        }
        AppBiometricManager.promptBiometricAuthForCrypto(
            activity = act,
            cipher = cipher,
            title = "Enable biometric unlock",
            subtitle = "Confirm your fingerprint or face to protect quick unlock",
            onSuccess = { authedCipher ->
                viewModel.completeBiometricArm(authedCipher)
                onDone(true)
            },
            onError = { onDone(false) },
            onCancel = { onDone(false) }
        )
    }

    val startDestination = when (uiState.status) {
        VaultStatus.ONBOARDING -> Screen.Onboarding.route
        VaultStatus.LOCKED -> Screen.Unlock.route
        VaultStatus.UNLOCKED -> Screen.Home.route
        VaultStatus.LOADING -> Screen.Unlock.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                uiState = uiState,
                onCreateVault = { password, biometricEnabled ->
                    viewModel.createVault(password, biometricEnabled) { success ->
                        if (success) {
                            val proceed = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            }
                            if (biometricEnabled) {
                                // Arming requires a live biometric prompt (see
                                // BiometricGatedKeyStore) -- do it right after
                                // vault creation, but don't block navigation
                                // on it succeeding; the user can always enable
                                // it later from Settings.
                                armBiometricWithPrompt { proceed() }
                            } else {
                                proceed()
                            }
                        }
                    }
                }
            )
        }

        composable(Screen.Unlock.route) {
            UnlockScreen(
                uiState = uiState,
                onUnlockWithPassword = { password ->
                    viewModel.unlockWithPassword(password) { success ->
                        if (success) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Unlock.route) { inclusive = true }
                            }
                        }
                    }
                },
                onUnlockWithBiometric = {
                    val cipher = viewModel.prepareBiometricUnlockCipher()
                    val act = activity
                    if (cipher != null && act != null) {
                        AppBiometricManager.promptBiometricAuthForCrypto(
                            activity = act,
                            cipher = cipher,
                            title = "Unlock AtomicVault",
                            subtitle = "Authenticate using fingerprint or face to access your vault",
                            onSuccess = { authedCipher ->
                                viewModel.unlockWithBiometric(authedCipher) { success ->
                                    if (success) {
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Unlock.route) { inclusive = true }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            VaultHomeScreen(
                uiState = uiState,
                onSearchChange = { viewModel.setSearchQuery(it) },
                onSelectFolder = { viewModel.setFolderFilter(it) },
                onSelectTag = { viewModel.setTagFilter(it) },
                onItemClick = { itemId ->
                    // Route by item type so a Payment Card / Identity opens
                    // its own editor rather than the generic Login one --
                    // resolved here in NavGraph (which already has
                    // uiState.previews) rather than changing
                    // VaultHomeScreen's own click-handling code.
                    val type = uiState.previews.firstOrNull { it.id == itemId }?.itemType
                    when (type) {
                        com.example.database.VaultItemType.PAYMENT_CARD ->
                            navController.navigate(Screen.PaymentCardEditor.createRoute(itemId))
                        com.example.database.VaultItemType.IDENTITY ->
                            navController.navigate(Screen.IdentityEditor.createRoute(itemId))
                        else ->
                            navController.navigate(Screen.Editor.createRoute(itemId))
                    }
                },
                onAddNewClick = {
                    navController.navigate(Screen.Editor.createRoute(null))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onLockClick = {
                    viewModel.lockVault()
                    navController.navigate(Screen.Unlock.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onReload = { viewModel.reloadVaultData() }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            CredentialEditorScreen(
                itemId = itemId,
                folders = uiState.folders,
                allTags = uiState.tags,
                onLoadItem = { id -> viewModel.getItem(id) },
                onSave = { input ->
                    if (itemId != null) {
                        viewModel.updateItem(itemId, input) {
                            navController.popBackStack()
                        }
                    } else {
                        viewModel.createItem(input) {
                            navController.popBackStack()
                        }
                    }
                },
                onDelete = { id ->
                    viewModel.deleteItem(id) {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PaymentCardEditor.route,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            val existing = remember(itemId) { itemId?.let { viewModel.getItem(it) } }
            com.example.ui.paymentcard.PaymentCardEditorScreen(
                existing = existing,
                onSave = { input ->
                    if (itemId != null) {
                        viewModel.updateItem(itemId, input) { navController.popBackStack() }
                    } else {
                        viewModel.createItem(input) { navController.popBackStack() }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.IdentityEditor.route,
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId")
            val existing = remember(itemId) { itemId?.let { viewModel.getItem(it) } }
            com.example.ui.identity.IdentityEditorScreen(
                existing = existing,
                onSave = { input ->
                    if (itemId != null) {
                        viewModel.updateItem(itemId, input) { navController.popBackStack() }
                    } else {
                        viewModel.createItem(input) { navController.popBackStack() }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Generator.route) {
            PasswordGeneratorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Security.route) {
            SecurityDashboardScreen(
                integrityWarnings = uiState.integrityWarnings,
                onLoadAllCredentials = { viewModel.getAllCredentialsForSecurity() },
                onItemClick = { itemId ->
                    navController.navigate(Screen.Editor.createRoute(itemId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                uiState = uiState,
                onUpdateAutoLock = { seconds -> viewModel.updateAutoLockSeconds(seconds) },
                onUpdateBiometric = { enabled ->
                    if (enabled) {
                        armBiometricWithPrompt { }
                    } else {
                        viewModel.disableBiometric()
                    }
                },
                onSetAutofillArmed = { armed ->
                    // Same Keystore-bound key as app-unlock now (see
                    // BiometricGatedKeyStore) -- arming/disarming one arms
                    // or disarms both.
                    if (armed) {
                        armBiometricWithPrompt { }
                    } else {
                        viewModel.disableBiometric()
                    }
                },
                onCreateFolder = { name -> viewModel.createFolder(name) },
                onDeleteFolder = { id -> viewModel.deleteFolder(id) },
                onCreateTag = { name -> viewModel.createTag(name) },
                onDeleteTag = { id -> viewModel.deleteTag(id) },
                onNavigateSecurity = { navController.navigate(Screen.Security.route) },
                onNavigateGenerator = { navController.navigate(Screen.Generator.route) },
                onNavigateBackup = { navController.navigate(Screen.Backup.route) },
                onNavigatePrivacyProof = { navController.navigate(Screen.PrivacyProof.route) },
                onNavigateAddPaymentCard = { navController.navigate(Screen.PaymentCardEditor.createRoute(null)) },
                onNavigateAddIdentity = { navController.navigate(Screen.IdentityEditor.createRoute(null)) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Backup.route) {
            BackupScreen(
                onExportBackup = { passphrase, onResult ->
                    viewModel.exportBackup(passphrase, onResult)
                },
                onImportBackup = { bytes, passphrase, onResult ->
                    viewModel.importBackup(bytes, passphrase, onResult)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PrivacyProof.route) {
            // produceState moves this off the composition/main thread --
            // PrivacyChecks.runAll() and verifyChainIntegrity() both do
            // real SQLite I/O (and the latter re-derives an HMAC per
            // ledger entry), which grows with usage over time. Running
            // that synchronously inside remember{} during composition
            // was a real jank/ANR-adjacent risk on an aging ledger, not
            // just a style nitpick.
            val checks by androidx.compose.runtime.produceState(
                initialValue = emptyList<com.example.trust.PrivacyCheck>(),
                uiState.biometricArmed,
                uiState.integrityWarnings
            ) {
                val act = activity
                value = if (act != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.trust.PrivacyChecks.runAll(
                            context = act,
                            sqlcipherVerified = viewModel.isSqlcipherVerified(),
                            biometricArmed = uiState.biometricArmed,
                            integrityWarnings = uiState.integrityWarnings
                        )
                    }
                } else {
                    emptyList()
                }
            }
            val chainBroken by androidx.compose.runtime.produceState(initialValue = false) {
                val act = activity
                value = if (act != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.trust.TrustLedger.verifyChainIntegrity(act) != null
                    }
                } else {
                    false
                }
            }

            com.example.ui.trust.PrivacyProofScreen(
                checks = checks,
                chainBroken = chainBroken,
                onBack = { navController.popBackStack() },
                onNavigateTimeline = { navController.navigate(Screen.SecurityTimeline.route) }
            )
        }

        composable(Screen.SecurityTimeline.route) {
            val entries by androidx.compose.runtime.produceState(
                initialValue = emptyList<com.example.trust.TrustLedgerEntry>()
            ) {
                val act = activity
                value = if (act != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.trust.TrustLedger.listEntries(act)
                    }
                } else {
                    emptyList()
                }
            }
            val chainBrokenAtId by androidx.compose.runtime.produceState<String?>(initialValue = null) {
                val act = activity
                value = if (act != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.trust.TrustLedger.verifyChainIntegrity(act)
                    }
                } else {
                    null
                }
            }

            com.example.ui.trust.SecurityTimelineScreen(
                entries = entries,
                previews = uiState.previews,
                chainBrokenAtId = chainBrokenAtId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
