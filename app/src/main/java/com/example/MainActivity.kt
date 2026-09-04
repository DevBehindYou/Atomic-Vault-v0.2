package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.example.security.VaultLifecycleObserver
import com.example.ui.AtomicVaultNavGraph
import com.example.ui.VaultStatus
import com.example.ui.VaultViewModel
import com.example.ui.theme.AtomicColors
import com.example.ui.theme.AtomicVaultTheme
import com.example.ui.theme.ThemePreferenceStore

class MainActivity : FragmentActivity() {

    private val viewModel: VaultViewModel by viewModels()
    private lateinit var lifecycleObserver: VaultLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Load the persisted theme choice before the first frame renders,
        // so the app doesn't flash dark-then-light on every launch for a
        // user who picked light mode.
        AtomicColors.applyTheme(ThemePreferenceStore.load(this))

        // Attach LifecycleObserver that automatically re-locks the application if backgrounded > 60s
        lifecycleObserver = VaultLifecycleObserver(
            getAutoLockSeconds = { viewModel.uiState.value.settings?.autoLockSeconds ?: 60 },
            isUnlocked = { viewModel.uiState.value.status == VaultStatus.UNLOCKED },
            onLock = { viewModel.lockVault() }
        )
        lifecycle.addObserver(lifecycleObserver)

        setContent {
            AtomicVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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
