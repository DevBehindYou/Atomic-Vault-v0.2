package com.example.ui

import com.example.ui.theme.AtomicSize
import com.example.ui.theme.AtomicSpacing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.example.database.CredentialPlain
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.LocalActivity
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.security.AppBiometricManager
import com.example.ui.components.AtomicBottomNav
import com.example.ui.components.AtomicTab
import com.example.ui.backup.BackupScreen
import com.example.ui.editor.CredentialEditorScreen
import com.example.ui.generator.PasswordGeneratorScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.security.SecurityDashboardScreen
import com.example.ui.detail.ItemDetailScreen
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
    /** Read view for a login (0.4.0): copy and reveal here, EDIT opens the editor. */
    object ItemDetail : Screen("item/{itemId}") {
        fun createRoute(itemId: String): String = "item/$itemId"
    }
    object Generator : Screen("generator")
    object Security : Screen("security")
    object Settings : Screen("settings")
    object Backup : Screen("backup")
    object PrivacyProof : Screen("privacy_proof")
    object SecurityTimeline : Screen("security_timeline")
}

private sealed interface ItemLoad {
    data object Loading : ItemLoad
    data class Ready(val item: CredentialPlain?) : ItemLoad
}

/**
 * Loads [itemId] on the IO dispatcher (a SQLCipher read plus several AES-GCM
 * decryptions must not run inside composition). A null [itemId] is a new
 * item and is Ready immediately.
 */
@Composable
private fun rememberItemLoad(viewModel: VaultViewModel, itemId: String?): ItemLoad {
    val load by produceState<ItemLoad>(
        initialValue = if (itemId == null) ItemLoad.Ready(null) else ItemLoad.Loading,
        key1 = itemId
    ) {
        value = ItemLoad.Ready(itemId?.let { viewModel.getItem(it) })
    }
    return load
}

@Composable
fun AtomicVaultNavGraph(
    navController: NavHostController,
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalActivity.current as? FragmentActivity

    // Shared helper: run the begin-cipher -> biometric-prompt -> complete-arm
    // sequence used by both Onboarding (initial opt-in) and Settings
    // (enabling it later). Requires a FragmentActivity context, which
    // MainActivity provides.
    // One prompt at a time: a second tap (or the unlock screen's auto-prompt
    // racing a manual tap) must not stack a second BiometricPrompt.
    val biometricPromptActive = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun armBiometricWithPrompt(onDone: (Boolean) -> Unit) {
        val act = activity
        if (act == null || !biometricPromptActive.compareAndSet(false, true)) {
            onDone(false)
            return
        }
        val cipher = try {
            viewModel.beginBiometricArm()
        } catch (e: Exception) {
            null
        }
        if (cipher == null) {
            biometricPromptActive.set(false)
            onDone(false)
            return
        }
        AppBiometricManager.promptBiometricAuthForCrypto(
            activity = act,
            cipher = cipher,
            title = if (viewModel.quickUnlockUsesScreenLock()) "Enable screen-lock unlock" else "Enable biometric unlock",
            subtitle = "Confirm your fingerprint or face to protect quick unlock",
            screenLock = viewModel.quickUnlockUsesScreenLock(),
            onSuccess = { authedCipher ->
                biometricPromptActive.set(false)
                val armed = try {
                    viewModel.completeBiometricArm(authedCipher)
                    true
                } catch (e: Exception) {
                    false
                }
                onDone(armed)
            },
            onError = {
                biometricPromptActive.set(false)
                onDone(false)
            },
            onCancel = {
                biometricPromptActive.set(false)
                onDone(false)
            }
        )
    }

    // Bottom-bar destinations replace one another instead of stacking, and
    // every one pops back to Home so Back always leaves via the vault.
    fun navigateTab(tab: AtomicTab) {
        val route = when (tab) {
            AtomicTab.Vault -> Screen.Home.route
            AtomicTab.Generate -> Screen.Generator.route
            AtomicTab.Audit -> Screen.Security.route
            AtomicTab.Settings -> Screen.Settings.route
        }
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Each item type has its own editor.
    fun editRoute(itemId: String): String =
        when (uiState.previews.firstOrNull { it.id == itemId }?.itemType) {
            com.example.database.VaultItemType.PAYMENT_CARD -> Screen.PaymentCardEditor.createRoute(itemId)
            com.example.database.VaultItemType.IDENTITY -> Screen.IdentityEditor.createRoute(itemId)
            else -> Screen.Editor.createRoute(itemId)
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
                    val act = activity
                    if (act != null && biometricPromptActive.compareAndSet(false, true)) {
                        val cipher = viewModel.prepareBiometricUnlockCipher()
                        if (cipher == null) {
                            // Not armed any more (e.g. a new fingerprint was
                            // enrolled and the Keystore invalidated the key).
                            biometricPromptActive.set(false)
                            viewModel.onBiometricUnavailable()
                        } else {
                            AppBiometricManager.promptBiometricAuthForCrypto(
                                activity = act,
                                cipher = cipher,
                                title = "Unlock AtomicVault",
                                subtitle = "Authenticate using fingerprint or face to access your vault",
                                screenLock = viewModel.quickUnlockUsesScreenLock(),
                                onSuccess = { authedCipher ->
                                    biometricPromptActive.set(false)
                                    viewModel.unlockWithBiometric(authedCipher) { success ->
                                        if (success) {
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(Screen.Unlock.route) { inclusive = true }
                                            }
                                        } else {
                                            viewModel.reportBiometricError(
                                                "Biometric unlock failed. Use your master password."
                                            )
                                        }
                                    }
                                },
                                onError = { message ->
                                    biometricPromptActive.set(false)
                                    viewModel.reportBiometricError(message)
                                },
                                // Cancel / "Use Master Password": leave the
                                // password field in front of the user, no error.
                                onCancel = { biometricPromptActive.set(false) }
                            )
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            // Expanded widths (840 dp and up) show the list and the selected
            // item side by side; narrower screens open the item as its own
            // screen. 600-839 dp keeps one pane: beside the rail and a 360 dp
            // list, the read view would be too narrow to use.
            val split = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
            var selectedId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
            val selectedPreview = uiState.previews.firstOrNull { it.id == selectedId }
            AdaptiveNav(AtomicTab.Vault, { navigateTab(it) }) { bar ->
              ListDetail(
                split = split,
                detail = {
                    if (selectedPreview != null) {
                        ItemDetailScreen(
                            itemId = selectedPreview.id,
                            onLoadItem = { id -> viewModel.getItem(id) },
                            onEdit = { navController.navigate(editRoute(selectedPreview.id)) },
                            onBack = { selectedId = null },
                            refreshKey = selectedPreview.updatedAt,
                            showBack = false,
                            onLoadHistory = { id -> viewModel.passwordHistory(id) },
                            onClearHistory = { id, done -> viewModel.clearPasswordHistory(id, done) }
                        )
                    } else {
                        com.example.ui.components.AtomicEmptyState(
                            message = if (uiState.previews.isEmpty()) {
                                "Items you add appear here."
                            } else {
                                "Pick an item on the left to see it here."
                            },
                            modifier = Modifier.fillMaxSize().padding(com.example.ui.theme.AtomicSpacing.xl)
                        )
                    }
                }
              ) {
                VaultHomeScreen(
                    uiState = uiState,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onSelectFolder = { viewModel.setFolderFilter(it) },
                    onSelectTag = { viewModel.setTagFilter(it) },
                    selectedItemId = if (split) selectedPreview?.id else null,
                    onItemClick = { itemId ->
                        // Every item opens in its read view; EDIT there picks the type's editor.
                        if (split) {
                            selectedId = itemId
                        } else {
                            navController.navigate(Screen.ItemDetail.createRoute(itemId))
                        }
                    },
                    onAddNewClick = {
                        navController.navigate(Screen.Editor.createRoute(null))
                    },
                    onAddPaymentCard = { navController.navigate(Screen.PaymentCardEditor.createRoute(null)) },
                    onAddIdentity = { navController.navigate(Screen.IdentityEditor.createRoute(null)) },
                    onLockClick = {
                        viewModel.lockVault()
                        navController.navigate(Screen.Unlock.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onReload = { viewModel.reloadVaultData() },
                    bottomBar = bar
                )
              }
    
                if (uiState.showKeyboardRemovedNotice) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    com.example.ui.components.AtomicSheet(
                        label = "Notice",
                        title = "The Atomic keyboard is gone",
                        message = "AtomicVault now fills passwords inside the keyboard you already use, " +
                            "such as Gboard, through Android Autofill. Your keyboard is back to your phone's default. " +
                            "Turn on AtomicVault as your autofill service to see your logins in the keyboard's suggestion strip.",
                        confirmLabel = "Turn on Autofill",
                        dismissLabel = "Later",
                        onConfirm = {
                            viewModel.dismissKeyboardRemovedNotice()
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
                            }
                        },
                        onDismiss = { viewModel.dismissKeyboardRemovedNotice() }
                    )
                }
            }
        }

        composable(
            route = Screen.ItemDetail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId").orEmpty()
            ItemDetailScreen(
                itemId = itemId,
                onLoadItem = { id -> viewModel.getItem(id) },
                onEdit = { navController.navigate(editRoute(itemId)) },
                onBack = { navController.popBackStack() },
                refreshKey = uiState.previews.firstOrNull { it.id == itemId }?.updatedAt,
                onLoadHistory = { id -> viewModel.passwordHistory(id) },
                onClearHistory = { id, done -> viewModel.clearPasswordHistory(id, done) }
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
                        // The item's detail screen is underneath: skip it.
                        if (!navController.popBackStack(Screen.Home.route, inclusive = false)) {
                            navController.popBackStack()
                        }
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
            val load = rememberItemLoad(viewModel, itemId)
            // The editor seeds its form fields once from `existing`, so it
            // must not compose until the load finishes -- otherwise an edit
            // opens blank and saving would overwrite the stored card.
            (load as? ItemLoad.Ready)?.let { ready ->
                com.example.ui.paymentcard.PaymentCardEditorScreen(
                    existing = ready.item,
                    folders = uiState.folders,
                    allTags = uiState.tags,
                    onSave = { input ->
                        if (itemId != null) {
                            viewModel.updateItem(itemId, input) { navController.popBackStack() }
                        } else {
                            viewModel.createItem(input) { navController.popBackStack() }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onDelete = { id ->
                        viewModel.deleteItem(id) {
                            // The item's detail screen is underneath: skip it.
                            if (!navController.popBackStack(Screen.Home.route, inclusive = false)) navController.popBackStack()
                        }
                    }
                )
            }
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
            val load = rememberItemLoad(viewModel, itemId)
            (load as? ItemLoad.Ready)?.let { ready ->
                com.example.ui.identity.IdentityEditorScreen(
                    existing = ready.item,
                    folders = uiState.folders,
                    allTags = uiState.tags,
                    onSave = { input ->
                        if (itemId != null) {
                            viewModel.updateItem(itemId, input) { navController.popBackStack() }
                        } else {
                            viewModel.createItem(input) { navController.popBackStack() }
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onDelete = { id ->
                        viewModel.deleteItem(id) {
                            // The item's detail screen is underneath: skip it.
                            if (!navController.popBackStack(Screen.Home.route, inclusive = false)) navController.popBackStack()
                        }
                    }
                )
            }
        }

        composable(Screen.Generator.route) {
            AdaptiveNav(AtomicTab.Generate, { navigateTab(it) }) { bar ->
                PasswordGeneratorScreen(
                    bottomBar = bar
                )
            }
        }

        composable(Screen.Security.route) {
            AdaptiveNav(AtomicTab.Audit, { navigateTab(it) }) { bar ->
                SecurityDashboardScreen(
                    integrityWarnings = uiState.integrityWarnings,
                    onLoadAllCredentials = { viewModel.getAllCredentialsForSecurity() },
                    onItemClick = { itemId ->
                        navController.navigate(Screen.ItemDetail.createRoute(itemId))
                    },
                    bottomBar = bar
                )
            }
        }

        composable(Screen.Settings.route) {
            AdaptiveNav(AtomicTab.Settings, { navigateTab(it) }) { bar ->
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
                    onNavigateBackup = { navController.navigate(Screen.Backup.route) },
                    onNavigatePrivacyProof = { navController.navigate(Screen.PrivacyProof.route) },
                    bottomBar = bar
                )
            }
        }

        composable(Screen.Backup.route) {
            BackupScreen(
                onExportBackup = { passphrase, onResult ->
                    viewModel.exportBackup(passphrase, onResult)
                },
                onImportBackup = { bytes, passphrase, onResult ->
                    viewModel.importBackup(bytes, passphrase, onResult)
                },
                onImportCsv = { bytes, onResult -> viewModel.importCsv(bytes, onResult) },
                onBack = { navController.popBackStack() },
                onUndoRestore = { onResult -> viewModel.undoLastRestore(onResult) }
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
                    // Read on the main thread: the live window flags.
                    val screenSecure = (act.window.attributes.flags and
                        android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.trust.PrivacyChecks.runAll(
                            context = act,
                            sqlcipherVerified = viewModel.isSqlcipherVerified(),
                            biometricArmed = uiState.biometricArmed,
                            integrityWarnings = uiState.integrityWarnings,
                            screenCaptureProtectionActive = screenSecure
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

            val verify by androidx.compose.runtime.produceState<Pair<String?, String?>>(initialValue = null to null) {
                val act = activity
                if (act != null) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.trust.AppSignature.signingCertSha256(act) to
                            com.example.trust.TrustLedger.headHash(act)
                    }
                }
            }

            com.example.ui.trust.PrivacyProofScreen(
                checks = checks,
                chainBroken = chainBroken,
                signingCertSha256 = verify.first,
                ledgerHead = verify.second,
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

/**
 * Top-level navigation that adapts to width (plan 8.9): phones get the
 * bottom bar in the screen's own bottomBar slot; 600 dp and wider (large
 * phones in landscape, foldables, tablets) get the side rail and the screen
 * gets no bottom bar.
 */
@Composable
internal fun ListDetail(
    split: Boolean,
    detail: @Composable () -> Unit,
    list: @Composable () -> Unit
) {
    if (!split) {
        list()
        return
    }
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.width(AtomicSize.listPane).fillMaxHeight().testTag("pane_list")
        ) { list() }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.width(com.example.ui.theme.AtomicBorder.rule).fillMaxHeight()
                .background(com.example.ui.theme.AtomicTheme.colors.borderControl)
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("pane_detail")
        ) { detail() }
    }
}

@Composable
private fun AdaptiveNav(
    tab: AtomicTab,
    onSelect: (AtomicTab) -> Unit,
    content: @Composable (bar: @Composable () -> Unit) -> Unit
) {
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    if (wide) {
        androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
            com.example.ui.components.AtomicNavRail(selected = tab, onSelect = onSelect)
            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) { content {} }
        }
    } else {
        content { AtomicBottomNav(tab, onSelect) }
    }
}
