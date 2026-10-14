package com.example.ui.generator

import com.example.ui.theme.AtomicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.security.ClipboardHelper
import com.example.ui.theme.AtomicSpacing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import com.example.ui.components.AtomicTitleRow
import com.example.ui.theme.AtomicTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("screen_generate"),
        containerColor = AtomicTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(AtomicSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = AtomicSize.contentMaxWidth).fillMaxWidth()) {
                AtomicTitleRow(title = "Generate", counter = "On this phone")
                Spacer(Modifier.height(AtomicSpacing.lg))
                PasswordGeneratorPanel(
                    onUsePassword = { generatedPassword ->
                        ClipboardHelper.copySensitive(context, "Generated password", generatedPassword)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Password copied. It clears from the clipboard in 45 seconds.",
                                withDismissAction = true
                            )
                        }
                    },
                    useButtonLabel = "Copy"
                )
            }
        }
    }
}
