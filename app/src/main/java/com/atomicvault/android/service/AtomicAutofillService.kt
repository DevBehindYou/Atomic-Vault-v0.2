package com.atomicvault.android.service

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.atomicvault.android.MainActivity
import com.atomicvault.android.R
import com.atomicvault.android.model.TrustEventType
import com.atomicvault.android.storage.VaultStorage
import com.atomicvault.android.trust.TrustLedger

class AtomicAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val fields = mutableMapOf<String, AutofillId>()
        traverseStructure(structure, fields)

        val usernameId = fields["username"] ?: fields["email"]
        val passwordId = fields["password"]

        if (usernameId == null && passwordId == null) {
            callback.onSuccess(null)
            return
        }

        VaultStorage.init(applicationContext)

        val currentData = VaultStorage.currentVaultData
        val responseBuilder = FillResponse.Builder()

        if (currentData == null) {
            // Vault is locked: Provide an authentication intent dataset to unlock AtomicVault
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val unlockPresentation = RemoteViews(packageName, android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "🔒 Unlock AtomicVault to autofill")
            }

            responseBuilder.setAuthentication(
                arrayOf(usernameId, passwordId).filterNotNull().toTypedArray(),
                pendingIntent.intentSender,
                unlockPresentation
            )
            callback.onSuccess(responseBuilder.build())
            return
        }

        // Vault is unlocked: populate matching credentials
        val items = currentData.items
        for (item in items.take(5)) {
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
                setTextViewText(android.R.id.text1, item.title)
                setTextViewText(android.R.id.text2, item.username.ifBlank { "Password Only" })
            }

            val datasetBuilder = Dataset.Builder(presentation)

            if (usernameId != null && item.username.isNotBlank()) {
                datasetBuilder.setValue(usernameId, AutofillValue.forText(item.username))
            }
            if (passwordId != null && item.password.isNotBlank()) {
                datasetBuilder.setValue(passwordId, AutofillValue.forText(item.password))
            }

            responseBuilder.addDataset(datasetBuilder.build())
        }

        TrustLedger.record(
            eventType = TrustEventType.CREDENTIAL_FILLED,
            subjectReference = "Android Autofill Framework",
            authType = "session_dek",
            source = "autofill_service",
            result = "presented"
        )

        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun traverseStructure(
        structure: android.app.assist.AssistStructure,
        fields: MutableMap<String, AutofillId>
    ) {
        val windowNodeCount = structure.windowNodeCount
        for (i in 0 until windowNodeCount) {
            val rootNode = structure.getWindowNodeAt(i).rootViewNode
            findAutofillNodes(rootNode, fields)
        }
    }

    private fun findAutofillNodes(
        node: android.app.assist.AssistStructure.ViewNode,
        fields: MutableMap<String, AutofillId>
    ) {
        val hints = node.autofillHints
        val id = node.autofillId

        if (id != null) {
            if (hints != null) {
                for (hint in hints) {
                    when {
                        hint.contains("username", ignoreCase = true) -> fields["username"] = id
                        hint.contains("email", ignoreCase = true) -> fields["email"] = id
                        hint.contains("password", ignoreCase = true) -> fields["password"] = id
                    }
                }
            }

            val idEntry = node.idEntry?.lowercase() ?: ""
            if (idEntry.contains("username") || idEntry.contains("user")) {
                fields["username"] = id
            } else if (idEntry.contains("email")) {
                fields["email"] = id
            } else if (idEntry.contains("password") || idEntry.contains("pass")) {
                fields["password"] = id
            }
        }

        for (i in 0 until node.childCount) {
            findAutofillNodes(node.getChildAt(i), fields)
        }
    }
}
