package com.example

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.example.security.IdleLockStore
import com.example.security.VaultLifecycleObserver
import com.example.ui.AtomicVaultNavGraph
import com.example.ui.VaultStatus
import com.example.ui.VaultViewModel
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.theme.ThemePreferenceStore

@OptIn(ExperimentalComposeUiApi::class)
class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private lateinit var lifecycleObserver: VaultLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Every screen of this app shows or takes a secret -- including
        // onboarding, where the master password is created -- so screenshots,
        // screen recording and the Recents thumbnail are blocked from the
        // first frame, not only once a vault exists.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Keep Autofill services (ours included) off our own fields: nothing
        // should offer to save the master password or the item being edited.
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        enableEdgeToEdge()

        // Load the appearance choice before the first frame renders, so the
        // app doesn't flash one theme and then the other at launch.
        AtomicColors.applyTheme(ThemePreferenceStore.load(this))

        // Locks after the chosen time away from the app, and after the chosen
        // time on screen with no taps.
        lifecycleObserver = VaultLifecycleObserver(
            getAutoLockSeconds = { viewModel.uiState.value.settings?.autoLockSeconds ?: 60 },
            getIdleLockSeconds = { IdleLockStore.load(this) },
            isUnlocked = { viewModel.uiState.value.status == VaultStatus.UNLOCKED },
            onLock = { viewModel.lockVault() }
        )
        lifecycle.addObserver(lifecycleObserver)

        setContent {
            AtomicVaultTheme {
                // Test tags double as resource ids, so the emulator check can
                // find screens by a stable id instead of visible text.
                Surface(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    val navController = rememberNavController()
                    AtomicVaultNavGraph(
                        navController = navController,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::lifecycleObserver.isInitialized) {
            lifecycleObserver.onUserActivity()
        }
    }
}
