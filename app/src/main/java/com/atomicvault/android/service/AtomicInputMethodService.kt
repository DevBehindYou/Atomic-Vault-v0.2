package com.atomicvault.android.service

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atomicvault.android.model.TrustEventType
import com.atomicvault.android.storage.VaultStorage
import com.atomicvault.android.trust.TrustLedger
import com.atomicvault.android.ui.theme.AtomicVaultTheme
import com.atomicvault.android.ui.theme.BackgroundDark
import com.atomicvault.android.ui.theme.EmeraldPrimary
import com.atomicvault.android.ui.theme.SurfaceDark

class AtomicInputMethodService : InputMethodService() {

    override fun onCreateInputView(): View {
        VaultStorage.init(applicationContext)

        return ComposeView(this).apply {
            setContent {
                AtomicVaultTheme(darkTheme = true) {
                    VaultKeyboardBar(
                        onInsertText = { text, subject ->
                            currentInputConnection?.commitText(text, 1)
                            TrustLedger.record(
                                eventType = TrustEventType.CREDENTIAL_FILLED,
                                subjectReference = subject,
                                authType = "ime_keyboard",
                                source = "ime_keyboard",
                                result = "success"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VaultKeyboardBar(
    onInsertText: (String, String) -> Unit
) {
    val currentData = VaultStorage.currentVaultData

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        color = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ATOMIC VAULT KEYBOARD",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = EmeraldPrimary
                )

                Text(
                    text = if (currentData != null) "Unlocked (${currentData.items.size} assets)" else "Locked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (currentData == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unlock AtomicVault in app to insert credentials",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentData.items.take(8).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SurfaceDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (item.username.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = { onInsertText(item.username, "${item.title} (user)") },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("User", fontSize = 11.sp)
                                        }
                                    }

                                    if (item.password.isNotBlank()) {
                                        Button(
                                            onClick = { onInsertText(item.password, "${item.title} (pass)") },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pass", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
